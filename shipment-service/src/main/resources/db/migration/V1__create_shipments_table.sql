CREATE TABLE shipments (
    id             BIGSERIAL                NOT NULL PRIMARY KEY,
    order_id       BIGINT                   NOT NULL UNIQUE,
    order_number   VARCHAR(64)              NOT NULL,
    status         VARCHAR(16)              NOT NULL,
    failure_reason VARCHAR(255),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);
