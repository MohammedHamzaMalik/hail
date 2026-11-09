CREATE TABLE drivers (
                         id UUID PRIMARY KEY,
                         lat FLOAT8 NOT NULL,
                         lng FLOAT8 NOT NULL,
                         status VARCHAR(20) NOT NULL, -- OFFLINE, ONLINE, BUSY
                         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                         updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_drivers_status ON drivers(status);

CREATE TABLE rides (
                       id UUID PRIMARY KEY,
                       driver_id UUID REFERENCES drivers(id),
                       pickup_lat FLOAT8 NOT NULL,
                       pickup_lng FLOAT8 NOT NULL,
                       status VARCHAR(20) NOT NULL, -- REQUESTED, ASSIGNED, COMPLETED, CANCELLED
                       idempotency_key VARCHAR(100) NOT NULL UNIQUE,
                       origin VARCHAR(20) NOT NULL, -- APP, AGENT, API
                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                       updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_rides_driver_id ON rides(driver_id);
-- Unique constraint inherently creates an index, but explicitly declaring it for clarity
CREATE INDEX idx_rides_idempotency_key ON rides(idempotency_key);