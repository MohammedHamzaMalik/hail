DROP INDEX IF EXISTS idx_drivers_status;
CREATE INDEX idx_drivers_online_geo ON drivers (status, lat, lng);