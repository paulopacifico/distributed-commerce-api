CREATE TABLE processed_payment_events (
    event_id     UUID                     NOT NULL PRIMARY KEY,
    order_id     BIGINT                   NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
