CREATE TABLE products
(
    id            UUID           NOT NULL,
    sku           VARCHAR(80)    NOT NULL,
    category      VARCHAR(20)    NOT NULL,
    brand         VARCHAR(30)    NOT NULL,
    model         VARCHAR(160)   NOT NULL,
    capacity      NUMERIC(10, 3) NOT NULL,
    capacity_unit VARCHAR(10)    NOT NULL,
    active        BOOLEAN        NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL,
    version       BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT ck_products_category
        CHECK (category IN ('PANEL', 'BATTERY', 'INVERTER')),
    CONSTRAINT ck_products_brand
        CHECK (brand IN (
            'JINKO', 'AIKO', 'TESLA', 'SIGEN', 'ANKER_SOLIX',
            'SUNGROW', 'GOODWE'
        )),
    CONSTRAINT ck_products_brand_category
        CHECK (
            (category = 'PANEL' AND brand IN ('JINKO', 'AIKO'))
            OR
            (category IN ('BATTERY', 'INVERTER') AND brand IN (
                'TESLA', 'SIGEN', 'ANKER_SOLIX', 'SUNGROW', 'GOODWE'
            ))
        ),
    CONSTRAINT ck_products_capacity_unit
        CHECK (
            (category = 'PANEL' AND capacity_unit = 'W')
            OR (category = 'BATTERY' AND capacity_unit = 'KWH')
            OR (category = 'INVERTER' AND capacity_unit = 'KW')
        ),
    CONSTRAINT ck_products_capacity_positive CHECK (capacity > 0)
);

CREATE UNIQUE INDEX uq_products_sku ON products (UPPER(sku));
CREATE INDEX ix_products_active_category
    ON products (active, category, brand, model);

CREATE TABLE inventory_balances
(
    id               UUID        NOT NULL,
    product_id       UUID        NOT NULL,
    on_hand_quantity  INTEGER     NOT NULL,
    reserved_quantity INTEGER     NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_inventory_balances PRIMARY KEY (id),
    CONSTRAINT fk_inventory_balances_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uq_inventory_balances_product UNIQUE (product_id),
    CONSTRAINT ck_inventory_balances_nonnegative
        CHECK (on_hand_quantity >= 0 AND reserved_quantity >= 0),
    CONSTRAINT ck_inventory_balances_reservation
        CHECK (reserved_quantity <= on_hand_quantity)
);

CREATE TABLE installers
(
    id           UUID         NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    active       BOOLEAN      NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_installers PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_installers_display_name
    ON installers (UPPER(display_name));
CREATE INDEX ix_installers_active_display_name
    ON installers (active, display_name);

CREATE TABLE field_jobs
(
    id                 UUID         NOT NULL,
    job_reference      VARCHAR(80)  NOT NULL,
    customer_name      VARCHAR(160) NOT NULL,
    location           VARCHAR(500) NOT NULL,
    job_date           DATE         NOT NULL,
    installer_id       UUID         NOT NULL,
    status             VARCHAR(30)  NOT NULL,
    created_by_user_id UUID         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_field_jobs PRIMARY KEY (id),
    CONSTRAINT fk_field_jobs_installer
        FOREIGN KEY (installer_id) REFERENCES installers (id),
    CONSTRAINT fk_field_jobs_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES app_users (id),
    CONSTRAINT ck_field_jobs_status CHECK (status IN ('ALLOCATED'))
);

CREATE UNIQUE INDEX uq_field_jobs_reference
    ON field_jobs (UPPER(job_reference));
CREATE INDEX ix_field_jobs_page
    ON field_jobs (job_date DESC, created_at DESC, id DESC);
CREATE INDEX ix_field_jobs_installer ON field_jobs (installer_id);

CREATE TABLE job_allocations
(
    id                     UUID           NOT NULL,
    job_id                 UUID           NOT NULL,
    product_id             UUID           NOT NULL,
    category               VARCHAR(20)    NOT NULL,
    quantity               INTEGER        NOT NULL,
    product_sku            VARCHAR(80)    NOT NULL,
    product_brand          VARCHAR(30)    NOT NULL,
    product_model          VARCHAR(160)   NOT NULL,
    product_capacity       NUMERIC(10, 3) NOT NULL,
    product_capacity_unit  VARCHAR(10)    NOT NULL,

    CONSTRAINT pk_job_allocations PRIMARY KEY (id),
    CONSTRAINT fk_job_allocations_job
        FOREIGN KEY (job_id) REFERENCES field_jobs (id),
    CONSTRAINT fk_job_allocations_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_job_allocations_category
        CHECK (category IN ('PANEL', 'BATTERY', 'INVERTER')),
    CONSTRAINT ck_job_allocations_brand
        CHECK (product_brand IN (
            'JINKO', 'AIKO', 'TESLA', 'SIGEN', 'ANKER_SOLIX',
            'SUNGROW', 'GOODWE'
        )),
    CONSTRAINT ck_job_allocations_capacity_unit
        CHECK (product_capacity_unit IN ('W', 'KW', 'KWH')),
    CONSTRAINT ck_job_allocations_capacity_positive
        CHECK (product_capacity > 0),
    CONSTRAINT ck_job_allocations_snapshot_shape
        CHECK (
            (category = 'PANEL'
                AND product_brand IN ('JINKO', 'AIKO')
                AND product_capacity_unit = 'W')
            OR
            (category = 'BATTERY'
                AND product_brand IN (
                    'TESLA', 'SIGEN', 'ANKER_SOLIX', 'SUNGROW', 'GOODWE'
                )
                AND product_capacity_unit = 'KWH')
            OR
            (category = 'INVERTER'
                AND product_brand IN (
                    'TESLA', 'SIGEN', 'ANKER_SOLIX', 'SUNGROW', 'GOODWE'
                )
                AND product_capacity_unit = 'KW')
        ),
    CONSTRAINT ck_job_allocations_quantity_positive CHECK (quantity > 0),
    CONSTRAINT uq_job_allocations_category UNIQUE (job_id, category),
    CONSTRAINT uq_job_allocations_product UNIQUE (job_id, product_id)
);

CREATE INDEX ix_job_allocations_product ON job_allocations (product_id);

CREATE TABLE stock_receipts
(
    id                   UUID         NOT NULL,
    receipt_reference    VARCHAR(80)  NOT NULL,
    received_at          TIMESTAMPTZ  NOT NULL,
    note                 VARCHAR(500),
    performed_by_user_id UUID         NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_stock_receipts PRIMARY KEY (id),
    CONSTRAINT fk_stock_receipts_performed_by
        FOREIGN KEY (performed_by_user_id) REFERENCES app_users (id)
);

CREATE UNIQUE INDEX uq_stock_receipts_reference
    ON stock_receipts (UPPER(receipt_reference));

CREATE TABLE stock_movements
(
    id                   UUID         NOT NULL,
    product_id           UUID         NOT NULL,
    job_id               UUID,
    receipt_id           UUID,
    movement_type        VARCHAR(30)  NOT NULL,
    on_hand_delta        INTEGER      NOT NULL,
    reserved_delta       INTEGER      NOT NULL,
    on_hand_after        INTEGER      NOT NULL,
    reserved_after       INTEGER      NOT NULL,
    reference            VARCHAR(80),
    note                 VARCHAR(500),
    performed_by_user_id UUID         NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_stock_movements PRIMARY KEY (id),
    CONSTRAINT fk_stock_movements_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_stock_movements_job
        FOREIGN KEY (job_id) REFERENCES field_jobs (id),
    CONSTRAINT fk_stock_movements_receipt
        FOREIGN KEY (receipt_id) REFERENCES stock_receipts (id),
    CONSTRAINT fk_stock_movements_performed_by
        FOREIGN KEY (performed_by_user_id) REFERENCES app_users (id),
    CONSTRAINT ck_stock_movements_type
        CHECK (movement_type IN ('STOCK_RECEIPT', 'JOB_RESERVATION')),
    CONSTRAINT ck_stock_movements_quantity_nonzero
        CHECK (on_hand_delta <> 0 OR reserved_delta <> 0),
    CONSTRAINT ck_stock_movements_balance_nonnegative
        CHECK (
            on_hand_after >= 0
            AND reserved_after >= 0
            AND reserved_after <= on_hand_after
        ),
    CONSTRAINT ck_stock_movements_shape
        CHECK (
            (movement_type = 'STOCK_RECEIPT'
                AND on_hand_delta > 0 AND reserved_delta = 0
                AND receipt_id IS NOT NULL AND job_id IS NULL)
            OR
            (movement_type = 'JOB_RESERVATION'
                AND on_hand_delta = 0 AND reserved_delta > 0
                AND receipt_id IS NULL AND job_id IS NOT NULL)
        )
);

CREATE INDEX ix_stock_movements_product_created
    ON stock_movements (product_id, created_at DESC);
CREATE UNIQUE INDEX uq_stock_movements_receipt_product
    ON stock_movements (receipt_id, product_id)
    WHERE receipt_id IS NOT NULL;
CREATE UNIQUE INDEX uq_stock_movements_job_product
    ON stock_movements (job_id, product_id)
    WHERE job_id IS NOT NULL;

CREATE INDEX ix_field_jobs_created_by ON field_jobs (created_by_user_id);
CREATE INDEX ix_stock_receipts_performed_by
    ON stock_receipts (performed_by_user_id);
CREATE INDEX ix_stock_movements_performed_by
    ON stock_movements (performed_by_user_id);

CREATE FUNCTION reject_stock_movement_changes()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'stock_movements is append-only'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_stock_movements_append_only
    BEFORE UPDATE OR DELETE ON stock_movements
    FOR EACH ROW
    EXECUTE FUNCTION reject_stock_movement_changes();
