# Hail — Race-Free Ride Dispatch API

**Live Demo (Swagger UI):** `https://hail-yixh.onrender.com/swagger-ui/index.html`  
**Health Check:** `https://hail-yixh.onrender.com/actuator/health`

Hail is a backend ride-dispatch service built with **Java 21**, **Spring Boot 4.1**, and **PostgreSQL 17** focused on one hard systems problem: **assigning the nearest available driver to a ride request at most once under concurrent traffic**, backed by automated concurrency tests running against real PostgreSQL in CI.

## Core Capabilities

- **Driver Lifecycle & Location:** Register drivers, toggle availability (`OFFLINE`, `ONLINE`, `BUSY`), and update GPS coordinates (`lat`, `lng`) with validation.
- **Ride Lifecycle & State Machine:** `REQUESTED` → `ASSIGNED` → `COMPLETED` (or `CANCELLED`), returning `409 Conflict` on illegal transitions and freeing `BUSY` drivers back to `ONLINE` when rides complete or cancel (when a driver was assigned).
- **Spatial Matching + Row Locking:** Pre-filters `ONLINE` drivers with a latitude/longitude bounding box backed by a composite index `(status, lat, lng)`, ranks candidates by the Haversine formula, and locks the winning row with `FOR UPDATE SKIP LOCKED`.
- **Idempotent Ride Creation:** Enforces unique `idempotency_key` handling on ride creation (`POST /rides`) so retries return the existing ride rather than creating duplicates.
- **OpenAPI Spec:** Exposes API documentation via SpringDoc / Swagger UI.

## Architecture & How Concurrency Works

### 1. Nearest-Driver Lookup (`Bounding Box + Haversine`)
Instead of computing trigonometry across every row in the table:
1. Compute `latDelta` and `lngDelta` for the search radius `R` (`~111.32 km` per degree of latitude; `111.32 * cos(lat)` per degree of longitude).
2. Filter `status = 'ONLINE'` within `[minLat, maxLat]` and `[minLng, maxLng]` using the composite B-tree index `idx_drivers_online_geo ON drivers (status, lat, lng)`. Postgres uses the composite B-tree index `idx_drivers_online_geo ON drivers (status, lat, lng)` with `status` as an equality prefix and range conditions on both `lat` and `lng`.
3. Filter and order remaining candidates by exact Haversine distance (`2 * 6371 * asin(sqrt(...))`) and take `LIMIT 1`. Using Haversine instead of the spherical law of cosines (`acos`) avoids floating-point domain errors when a rider and driver share identical coordinates.

### 2. Exactly-Once Assignment (`FOR UPDATE SKIP LOCKED`)
A naive `SELECT` followed by `UPDATE` outside a lock lets multiple threads read the same available driver before either marks the driver `BUSY`.

In `RideService.assignDriverLocked(...)`:
- Runs inside a `@Transactional` boundary at PostgreSQL's default `READ COMMITTED` isolation level.
- Executes the nearest-driver query with `FOR UPDATE SKIP LOCKED`.
- Immediately marks the locked driver `BUSY` and updates the ride to `ASSIGNED` with `driver_id`.
- If a row is already locked by an in-flight transaction, `SKIP LOCKED` bypasses it rather than blocking or raising serialization aborts.

## Query Plan (`EXPLAIN ANALYZE` on 100k Drivers)

With small row counts (e.g., 10 drivers in tests), PostgreSQL's planner chooses a sequential scan. With **100,000 seeded drivers** around Helsinki (`~70% ONLINE`):

```text
Limit  (cost=1970.63..1970.65 rows=1 width=69) (actual time=7.367..7.369 rows=1 loops=1)
   Buffers: shared hit=1111
   ->  LockRows  (cost=1970.63..1989.71 rows=1526 width=69) (actual time=7.364..7.365 rows=1 loops=1)
         Buffers: shared hit=1111
         ->  Sort  (cost=1970.63..1974.45 rows=1526 width=69) (actual time=7.339..7.340 rows=1 loops=1)
               Sort Key: (('12742'::double precision * asin(sqrt((power(sin((radians((lat - '60.1719'::double precision)) / '2'::double precision)), '2'::double precision) + (('0.4973994859789078'::double precision * cos(radians(lat))) * power(sin((radians((lng - '24.9414'::double precision)) / '2'::double precision)), '2'::double precision)))))))
               Sort Method: quicksort  Memory: 404kB
               Buffers: shared hit=1110
               ->  Bitmap Heap Scan on drivers  (cost=542.80..1963.00 rows=1526 width=69) (actual time=1.585..6.430 rows=3581 loops=1)
                     Recheck Cond: (((status)::text = 'ONLINE'::text) AND (lat >= '60.127'::double precision) AND (lat <= '60.217'::double precision) AND (lng >= '24.851'::double precision) AND (lng <= '25.031'::double precision))
                     Filter: (('12742'::double precision * asin(sqrt((power(sin((radians((lat - '60.1719'::double precision)) / '2'::double precision)), '2'::double precision) + (('0.4973994859789078'::double precision * cos(radians(lat))) * power(sin((radians((lng - '24.9414'::double precision)) / '2'::double precision)), '2'::double precision)))))) <= '5'::double precision)
                     Rows Removed by Filter: 953
                     Heap Blocks: exact=1018
                     Buffers: shared hit=1110
                     ->  Bitmap Index Scan on idx_drivers_online_geo  (cost=0.00..542.42 rows=4579 width=0) (actual time=1.428..1.429 rows=4534 loops=1)
                           Index Cond: (((status)::text = 'ONLINE'::text) AND (lat >= '60.127'::double precision) AND (lat <= '60.217'::double precision) AND (lng >= '24.851'::double precision) AND (lng <= '25.031'::double precision))
                           Buffers: shared hit=92
 Planning:
   Buffers: shared hit=41
 Planning Time: 0.498 ms
 Execution Time: 7.419 ms
(21 rows)
```

## Decision Log (Tradeoffs)

| Decision | Chosen | Alternative | Why |
| --- | --- | --- | --- |
| **Data Access** | `JdbcClient` (plain SQL) | Spring Data JPA | Locking queries (`FOR UPDATE SKIP LOCKED`) and exact SQL execution plans stay explicit rather than hidden behind ORM proxies or dirty checking. |
| **Geo Query** | Bounding box + Haversine in SQL | PostGIS | Keeps dependencies minimal while demonstrating composite B-tree filtering + exact great-circle distance ranking. |
| **Concurrency Control** | `FOR UPDATE SKIP LOCKED` (`READ COMMITTED`) | Optimistic locking (`version`) or `SERIALIZABLE` | Under burst contention (50 requests vs. 10 drivers), optimistic locking requires application retry loops on version conflicts, and `SERIALIZABLE` risks serialization-failure aborts. `SKIP LOCKED` resolves assignment in a single pass. *(See Limitations: "nearest unlocked" vs. global nearest.)* |
| **Scope** | Dispatch core, lifecycle, idempotency | Auth, payments, WebSockets, microservices | Depth on transactions and locking beats a broad half-finished clone. |   

## Limitations & What Changes at Scale

1. **"Nearest unlocked" vs. global nearest:** `FOR UPDATE SKIP LOCKED` skips rows locked by concurrent transactions. Under simultaneous burst traffic, a request can be matched to a slightly farther unlocked driver rather than waiting for a closer in-flight transaction to commit or roll back.
2. **False "no driver" under burst contention:** If all in-range drivers are locked by in-flight transactions (such as requests #11–#50 in a 50-request burst against 10 drivers), `SKIP LOCKED` immediately returns 0 rows even if a driver frees up milliseconds later.
3. **Scaling to 100k+ concurrent drivers:** A B-tree index on `(status, lat, lng)` only range-scans on `lat` and degrades near the poles or antimeridian. At scale, I would replace bounding-box B-tree queries with **PostGIS (`GEOGRAPHY` + GiST R-tree index)** or **H3/geohash spatial partitioning**, and add a **dispatch queue with short retry/backoff** (plus driver offer/accept timeouts) to handle burst contention gracefully.   

## How to Run Locally

### Run Full Stack with Docker Compose

    docker compose up --build -d

- API base URL: `http://localhost:8080`
- Health check: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

Stop containers when finished:

    docker compose down

### Run Automated Tests (Testcontainers)

Requires Docker running locally:

    mvn test   

## Test Results & Concurrency Proof

* **Locked Concurrency Proof (`RideConcurrencyLockedTest`):** Fires **50 concurrent requests** against **10 online drivers** on real PostgreSQL 17 via Testcontainers:
  * **Assigned rides:** `10`
  * **Unique drivers assigned:** `10`
  * **Double-bookings:** `0`

* **Naive Race Baseline (`RideConcurrencyNaiveTest`):** Marked `@Disabled` in CI because asserting a race condition is inherently timing-dependent; running it manually in an IDE demonstrates double-booking when `SELECT` and `UPDATE` are unguarded.

* **Lifecycle, Geo & Idempotency Tests:** Verify identical-coordinate Haversine queries, `ASSIGNED` → `COMPLETED` freeing the driver back to `ONLINE`, illegal transitions returning `409 Conflict`, and duplicate idempotency keys returning the same ride.

* **CI:** Runs `mvn -B verify` on `ubuntu-latest` via GitHub Actions (`.github/workflows/ci.yml`).   
