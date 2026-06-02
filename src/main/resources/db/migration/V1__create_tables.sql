-- Stocks: current available quantity per account+SKU
CREATE TABLE stocks (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    account_id        VARCHAR(255) NOT NULL,
    sku               VARCHAR(255) NOT NULL,
    available_quantity INTEGER      NOT NULL DEFAULT 0,
    last_updated_at   TIMESTAMP    NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_stocks PRIMARY KEY (id),
    CONSTRAINT uk_stocks_account_sku UNIQUE (account_id, sku)
);

CREATE INDEX idx_stocks_account_sku ON stocks (account_id, sku);

-- Stock events: every received event (idempotency + audit)
CREATE TABLE stock_events (
    event_id         VARCHAR(255) NOT NULL,
    type             VARCHAR(50)  NOT NULL,
    status           VARCHAR(50)  NOT NULL,
    status_reason    TEXT,
    account_id       VARCHAR(255) NOT NULL,
    sku              VARCHAR(255) NOT NULL,
    marketplace      VARCHAR(100),
    external_order_id VARCHAR(255),
    quantity         INTEGER,
    available        INTEGER,
    quantity_sent    INTEGER,
    reason           VARCHAR(500),
    occurred_at      TIMESTAMP    NOT NULL,
    processed_at     TIMESTAMP,
    CONSTRAINT pk_stock_events PRIMARY KEY (event_id)
);

CREATE INDEX idx_stock_events_status ON stock_events (status);
CREATE INDEX idx_stock_events_order ON stock_events (marketplace, account_id, external_order_id, sku);

-- Stock history: audit trail of every stock change
CREATE TABLE stock_history (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    account_id       VARCHAR(255) NOT NULL,
    sku              VARCHAR(255) NOT NULL,
    event_id         VARCHAR(255) NOT NULL,
    event_type       VARCHAR(50)  NOT NULL,
    quantity_before  INTEGER      NOT NULL,
    quantity_after   INTEGER      NOT NULL,
    delta            INTEGER      NOT NULL,
    marketplace      VARCHAR(100),
    external_order_id VARCHAR(255),
    reason           VARCHAR(500),
    occurred_at      TIMESTAMP    NOT NULL,
    processed_at     TIMESTAMP    NOT NULL,
    CONSTRAINT pk_stock_history PRIMARY KEY (id)
);

CREATE INDEX idx_stock_history_account_sku ON stock_history (account_id, sku, processed_at);

-- Orders: tracks order state for cancellation idempotency
CREATE TABLE orders (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    marketplace      VARCHAR(100) NOT NULL,
    account_id       VARCHAR(255) NOT NULL,
    external_order_id VARCHAR(255) NOT NULL,
    sku              VARCHAR(255) NOT NULL,
    quantity         INTEGER      NOT NULL,
    status           VARCHAR(50)  NOT NULL,
    stock_restored   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uk_orders_key UNIQUE (marketplace, account_id, external_order_id, sku)
);

CREATE INDEX idx_orders_key ON orders (marketplace, account_id, external_order_id, sku);

-- Inconsistencies: problematic or ambiguous events
CREATE TABLE inconsistencies (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    event_id         VARCHAR(255) NOT NULL,
    event_type       VARCHAR(50)  NOT NULL,
    account_id       VARCHAR(255) NOT NULL,
    sku              VARCHAR(255) NOT NULL,
    marketplace      VARCHAR(100),
    external_order_id VARCHAR(255),
    description      VARCHAR(1000) NOT NULL,
    occurred_at      TIMESTAMP    NOT NULL,
    detected_at      TIMESTAMP    NOT NULL,
    CONSTRAINT pk_inconsistencies PRIMARY KEY (id)
);

CREATE INDEX idx_inconsistencies_account_sku ON inconsistencies (account_id, sku);
