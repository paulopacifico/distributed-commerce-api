CREATE TABLE processed_order_events (
    event_id UUID PRIMARY KEY,
    order_id BIGINT NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
