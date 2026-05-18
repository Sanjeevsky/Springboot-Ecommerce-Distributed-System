-- Integration test schema for H2 (cart_id nullable to allow Hibernate's two-step FK insert)
CREATE TABLE IF NOT EXISTS cart (
    id           VARCHAR(255) NOT NULL,
    user_id      VARCHAR(255) NOT NULL UNIQUE,
    total_amount DOUBLE,
    created_at   DATETIME,
    updated_at   DATETIME,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS cart_item (
    id           VARCHAR(255) NOT NULL,
    cart_id      VARCHAR(255),
    product_id   VARCHAR(255) NOT NULL,
    variant_id   VARCHAR(255),
    product_name VARCHAR(255) NOT NULL,
    unit_price   DOUBLE       NOT NULL,
    qty          INTEGER      NOT NULL,
    added_at     DATETIME,
    PRIMARY KEY (id)
);
