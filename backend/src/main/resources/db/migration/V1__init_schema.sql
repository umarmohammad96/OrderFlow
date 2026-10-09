-- OrderFlow schema (blueprint §5) plus supporting tables for categories, order history,
-- transactional outbox and notification log.

CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(100)  NOT NULL,
    email         VARCHAR(255)  NOT NULL,
    password_hash VARCHAR(100)  NOT NULL,
    role          VARCHAR(20)   NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN')),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

CREATE TABLE categories (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(200)   NOT NULL,
    description VARCHAR(2000),
    price       NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    category_id BIGINT         NOT NULL REFERENCES categories (id),
    active      BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_products_category_active ON products (category_id, active);
CREATE INDEX ix_products_active_created ON products (active, created_at DESC);

-- CHECK constraints are a last line of defence: even a buggy code path cannot oversell.
CREATE TABLE inventory (
    product_id         BIGINT PRIMARY KEY REFERENCES products (id),
    available_quantity INTEGER NOT NULL CHECK (available_quantity >= 0),
    reserved_quantity  INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    version            BIGINT  NOT NULL DEFAULT 0
);

CREATE TABLE carts (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cart_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cart_id    BIGINT  NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    product_id BIGINT  NOT NULL REFERENCES products (id),
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    CONSTRAINT ux_cart_items_cart_product UNIQUE (cart_id, product_id)
);

CREATE TABLE orders (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT         NOT NULL REFERENCES users (id),
    total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    status       VARCHAR(20)    NOT NULL CHECK (status IN ('CREATED', 'PAYMENT_PENDING', 'CONFIRMED',
                                                           'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version      BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_orders_user_created ON orders (user_id, created_at DESC);

CREATE TABLE order_items (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id     BIGINT         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id   BIGINT         NOT NULL REFERENCES products (id),
    product_name VARCHAR(200)   NOT NULL,
    quantity     INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0)
);
CREATE INDEX ix_order_items_order ON order_items (order_id);

CREATE TABLE order_status_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    BIGINT      NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_order_status_history_order ON order_status_history (order_id);

CREATE TABLE payments (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id              BIGINT         NOT NULL REFERENCES orders (id),
    amount                NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    status                VARCHAR(20)    NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'VOIDED')),
    transaction_reference VARCHAR(64)    NOT NULL UNIQUE,
    failure_reason        VARCHAR(255),
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_payments_order ON payments (order_id);
-- At most one in-flight and at most one successful payment per order.
CREATE UNIQUE INDEX ux_payments_order_pending ON payments (order_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX ux_payments_order_succeeded ON payments (order_id) WHERE status = 'SUCCEEDED';

CREATE TABLE idempotency_keys (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES users (id),
    request_key       VARCHAR(100) NOT NULL,
    request_hash      CHAR(64)    NOT NULL,
    response_snapshot JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_idempotency_user_key UNIQUE (user_id, request_key)
);
CREATE INDEX ix_idempotency_created ON idempotency_keys (created_at);

CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   VARCHAR(50)  NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    routing_key    VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     VARCHAR(500)
);
CREATE INDEX ix_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

CREATE TABLE notifications (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id   UUID         NOT NULL,
    order_id   BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    channel    VARCHAR(10)  NOT NULL CHECK (channel IN ('EMAIL', 'SMS')),
    recipient  VARCHAR(255) NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_notifications_event_channel UNIQUE (event_id, channel)
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);
