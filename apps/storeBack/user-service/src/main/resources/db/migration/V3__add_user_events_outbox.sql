CREATE TABLE user_events_outbox (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_user_outbox_published ON user_events_outbox (published);