# Hail — Race-Free Ride Dispatch API

Hail is a backend ride-dispatch service built with **Java 21**, **Spring Boot 3.5**, and **PostgreSQL 17** focused on one hard systems problem: **assigning the nearest available driver to a ride request at most once under concurrent traffic**, backed by automated concurrency tests running against real PostgreSQL in CI.

## Core Capabilities

- **Driver Lifecycle & Location:** Register drivers, toggle availability (`OFFLINE`, `ONLINE`, `BUSY`), and update GPS coordinates (`lat`, `lng`) with validation.
- **Ride Lifecycle & State Machine:** `REQUESTED` → `ASSIGNED` → `COMPLETED` (or `CANCELLED`), returning `409 Conflict` on illegal transitions and freeing `BUSY` drivers back to `ONLINE` when rides complete or cancel (when a driver was assigned).
- **Spatial Matching + Row Locking:** Pre-filters `ONLINE` drivers with a latitude/longitude bounding box backed by a composite index `(status, lat, lng)`, ranks candidates by Haversine distance, and locks the winning row with `FOR UPDATE SKIP LOCKED`.
- **Idempotent Ride Creation:** Enforces unique `idempotency_key` handling on ride creation (`POST /rides`) so retries return the existing ride rather than creating duplicates.
- **OpenAPI Spec:** Exposes API documentation via SpringDoc / Swagger UI.

## Architecture & How Concurrency Works

### 1. Nearest-Driver Lookup (Bounding Box + Haversine)
Instead of scanning every driver row and computing trigonometry across the full table:
1. Compute `latDelta` and `lngDelta` for the search radius `R` (using `~111.32 km` per degree of latitude and `111.32 * cos(lat)` per degree of longitude).
2. Filter `status = 'ONLINE'` within `[minLat, maxLat]` and `[minLng, maxLng]` using the composite index `idx_drivers_online_geo ON drivers (status, lat, lng)`.
3. Filter and order remaining candidates by exact Haversine distance (`6371 * acos(...)`) and take `LIMIT 1`.

### 2. Exactly-Once Assignment (FOR UPDATE SKIP LOCKED)
A naive `SELECT` followed by `UPDATE` outside a lock lets multiple threads read the same available driver before either marks the driver `BUSY`.

In `RideService.assignDriverLocked(...)`:
- Runs inside a `@Transactional` boundary at Postgres's default `READ COMMITTED` isolation level.
- Executes the nearest-driver query with `FOR UPDATE SKIP LOCKED`.
- Immediately marks the locked driver `BUSY` and updates the ride to `ASSIGNED` with `driver_id`.
- If a row is already locked by an in-flight transaction, `SKIP LOCKED` skips it rather than blocking or causing serialization aborts.

## Decision Log (Tradeoffs)

| Decision | Chosen | Alternative | Why |
|---|---|---|---|
| **Data Access** | `JdbcClient` (plain SQL) | Spring Data JPA | Locking queries (`FOR UPDATE SKIP LOCKED`) and exact SQL execution plans should be explicit, not hidden behind ORM proxies or dirty checking. |
| **Geo Query** | Bounding box + Haversine in SQL | PostGIS | Keeps dependencies light and avoids spatial extension overhead while demonstrating index range scans + exact distance filtering. |
| **Concurrency Control** | `FOR UPDATE SKIP LOCKED` (`READ COMMITTED`) | Optimistic locking (`version`) or `SERIALIZABLE` | High contention (50 requests vs. 10 drivers) causes retry storms with optimistic locking and unnecessary aborts under `SERIALIZABLE`. |
| **Scope** | Dispatch core, lifecycle, idempotency | Auth, payments, WebSockets, microservices | Depth on transactions and locking beats a broad half-finished clone. Future ideas stay out of scope. |

## How to Run Locally

### Run Full Stack with Docker Compose

```bash
docker compose up --build -d
```

- API base URL: `http://localhost:8080`
- Health check: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

Stop containers when finished:

```bash
docker compose down
```

### Run Automated Tests (Testcontainers)

Requires Docker running locally:

```bash
mvn test
```

## Test Results & Concurrency Proof

- **Naive Baseline (`RideConcurrencyNaiveTest`):** Demonstrates race conditions when 50 concurrent requests hit 10 drivers without row locking across read and write.
- **Locked Implementation (`RideConcurrencyLockedTest`):** Fires **50 concurrent requests** against **10 online drivers** on real PostgreSQL 17 via Testcontainers:
    - **Assigned rides:** `10`
    - **Unique drivers assigned:** `10`
    - **Double-bookings:** `0`
- **Lifecycle & Idempotency Tests:** Verify `ASSIGNED` → `COMPLETED` frees the driver back to `ONLINE`, illegal transitions throw `InvalidRideTransitionException` (`409 Conflict`), and duplicate idempotency keys return the same ride.
- **CI:** Runs `mvn -B verify` on `ubuntu-latest` via GitHub Actions (`.github/workflows/ci.yml`).   