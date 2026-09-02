CREATE TABLE app_users
(
    id           UUID         NOT NULL,
    first_name    VARCHAR(120) NOT NULL,
    last_name     VARCHAR(120) NOT NULL,
    email        VARCHAR(254) NOT NULL,
    role         VARCHAR(30)  NOT NULL,
    status       VARCHAR(30)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_app_users PRIMARY KEY (id),

    CONSTRAINT ck_app_users_role
        CHECK (role IN ('ADMIN', 'WAREHOUSE_OPR')),

    CONSTRAINT ck_app_users_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX uq_app_users_email
    ON app_users (LOWER(email));