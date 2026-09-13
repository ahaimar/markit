ALTER TABLE products ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_products_deleted_at ON products (deleted_at);

CREATE TABLE product_events_outbox (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_product_outbox_published ON product_events_outbox (published);