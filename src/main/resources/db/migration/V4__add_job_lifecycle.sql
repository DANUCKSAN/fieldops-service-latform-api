-- Expand the lifecycle state machine. Existing rows remain valid because every
-- pre-V4 job has status ALLOCATED.
ALTER TABLE field_jobs
    DROP CONSTRAINT ck_field_jobs_status;

ALTER TABLE field_jobs
    ADD CONSTRAINT ck_field_jobs_status
        CHECK (status IN (
            'ALLOCATED',
            'DISPATCHED',
            'COMPLETED',
            'CANCELLED'
        ));

-- Support the two inventory effects introduced by the lifecycle:
-- cancellation releases a reservation, while dispatch consumes both the
-- physical stock and its reservation.
ALTER TABLE stock_movements
    DROP CONSTRAINT ck_stock_movements_type;

ALTER TABLE stock_movements
    ADD CONSTRAINT ck_stock_movements_type
        CHECK (movement_type IN (
            'STOCK_RECEIPT',
            'JOB_RESERVATION',
            'JOB_RESERVATION_RELEASE',
            'JOB_DISPATCH'
        ));

ALTER TABLE stock_movements
    DROP CONSTRAINT ck_stock_movements_shape;

ALTER TABLE stock_movements
    ADD CONSTRAINT ck_stock_movements_shape
        CHECK (
            (movement_type = 'STOCK_RECEIPT'
                AND on_hand_delta > 0
                AND reserved_delta = 0
                AND receipt_id IS NOT NULL
                AND job_id IS NULL)
            OR
            (movement_type = 'JOB_RESERVATION'
                AND on_hand_delta = 0
                AND reserved_delta > 0
                AND receipt_id IS NULL
                AND job_id IS NOT NULL)
            OR
            (movement_type = 'JOB_RESERVATION_RELEASE'
                AND on_hand_delta = 0
                AND reserved_delta < 0
                AND receipt_id IS NULL
                AND job_id IS NOT NULL
                AND note IS NOT NULL)
            OR
            (movement_type = 'JOB_DISPATCH'
                AND on_hand_delta < 0
                AND reserved_delta = on_hand_delta
                AND receipt_id IS NULL
                AND job_id IS NOT NULL
                AND reference IS NOT NULL)
        );

-- V3 allowed only one job-related movement per product. V4 needs one movement
-- of each lifecycle type, while still preventing the same effect from being
-- applied twice during a retry.
DROP INDEX uq_stock_movements_job_product;

CREATE UNIQUE INDEX uq_stock_movements_job_product_type
    ON stock_movements (job_id, product_id, movement_type)
    WHERE job_id IS NOT NULL;

CREATE INDEX ix_stock_movements_job_created
    ON stock_movements (job_id, created_at DESC, id DESC)
    WHERE job_id IS NOT NULL;

-- Keep a separate append-only audit trail for state changes. previous_status
-- is nullable only for the initial transition into ALLOCATED. Existing V3 jobs
-- are not backfilled; their created_by_user_id and created_at remain their
-- allocation audit record.
CREATE TABLE job_status_history
(
    id                   UUID         NOT NULL,
    job_id               UUID         NOT NULL,
    previous_status      VARCHAR(30),
    new_status           VARCHAR(30)  NOT NULL,
    reference            VARCHAR(80),
    note                 VARCHAR(500),
    performed_by_user_id UUID         NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_job_status_history PRIMARY KEY (id),
    CONSTRAINT fk_job_status_history_job
        FOREIGN KEY (job_id) REFERENCES field_jobs (id),
    CONSTRAINT fk_job_status_history_performed_by
        FOREIGN KEY (performed_by_user_id) REFERENCES app_users (id),
    CONSTRAINT ck_job_status_history_previous_status
        CHECK (
            previous_status IS NULL
            OR previous_status IN (
                'ALLOCATED',
                'DISPATCHED',
                'COMPLETED',
                'CANCELLED'
            )
        ),
    CONSTRAINT ck_job_status_history_new_status
        CHECK (new_status IN (
            'ALLOCATED',
            'DISPATCHED',
            'COMPLETED',
            'CANCELLED'
        )),
    CONSTRAINT ck_job_status_history_transition
        CHECK (
            (previous_status IS NULL AND new_status = 'ALLOCATED')
            OR
            (previous_status = 'ALLOCATED'
                AND new_status IN ('DISPATCHED', 'CANCELLED'))
            OR
            (previous_status = 'DISPATCHED'
                AND new_status = 'COMPLETED')
        ),
    CONSTRAINT ck_job_status_history_details
        CHECK (
            (new_status = 'ALLOCATED')
            OR
            (new_status = 'DISPATCHED' AND reference IS NOT NULL)
            OR
            (new_status = 'CANCELLED' AND note IS NOT NULL)
            OR
            (new_status = 'COMPLETED')
        )
);

CREATE UNIQUE INDEX uq_job_status_history_job_new_status
    ON job_status_history (job_id, new_status);

CREATE INDEX ix_job_status_history_job_created
    ON job_status_history (job_id, created_at, id);

CREATE INDEX ix_job_status_history_performed_by
    ON job_status_history (performed_by_user_id);

CREATE INDEX ix_field_jobs_status_page
    ON field_jobs (status, job_date DESC, created_at DESC, id DESC);

CREATE FUNCTION reject_job_status_history_changes()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'job_status_history is append-only'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_job_status_history_append_only
    BEFORE UPDATE OR DELETE ON job_status_history
    FOR EACH ROW
    EXECUTE FUNCTION reject_job_status_history_changes();
