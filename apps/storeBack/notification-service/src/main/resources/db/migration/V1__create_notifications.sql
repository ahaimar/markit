CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    event_id VARCHAR(255),
    recipient VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    subject VARCHAR(255),
    message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_recipient ON notifications (recipient);