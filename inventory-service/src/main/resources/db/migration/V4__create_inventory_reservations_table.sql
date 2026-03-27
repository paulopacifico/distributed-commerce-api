CREATE TABLE inventory_reservations (
    order_id          BIGINT                   NOT NULL PRIMARY KEY,
    sku_code          VARCHAR(64)              NOT NULL,
    reserved_quantity INTEGER                  NOT NULL,
    reserved_at       TIMESTAMP WITH TIME ZONE NOT NULL
);
