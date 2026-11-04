CREATE TABLE orders (
    id         UUID           PRIMARY KEY,
    item       VARCHAR(200)   NOT NULL,
    amount     NUMERIC(12, 2) NOT NULL,
    status     VARCHAR(20)    NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- One row per money movement: duplicates here are the double charge we want to prevent.
CREATE TABLE charges (
    id         BIGSERIAL      PRIMARY KEY,
    order_id   UUID           NOT NULL REFERENCES orders (id),
    amount     NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- Required by quarkus.idempotency.store=jdbc; the extension never issues DDL (PostgreSQL variant from its docs).
CREATE TABLE idempotency_entry (
    id                  VARCHAR(255) PRIMARY KEY,
    fingerprint         VARCHAR(255),
    in_flight           SMALLINT     NOT NULL,
    response            BYTEA,
    lock_expires_at     BIGINT       NOT NULL,
    response_expires_at BIGINT
);
