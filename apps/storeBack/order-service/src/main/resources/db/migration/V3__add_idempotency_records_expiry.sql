ALTER TABLE idempotency_records ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_idempotency_expires_at ON idempotency_records (expires_at);