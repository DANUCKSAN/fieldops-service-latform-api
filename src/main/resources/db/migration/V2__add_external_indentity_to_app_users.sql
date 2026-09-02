ALTER TABLE app_users
    ADD COLUMN identity_issuer VARCHAR(512),
    ADD COLUMN identity_subject VARCHAR(255);

ALTER TABLE app_users
    ADD CONSTRAINT ck_app_users_identity_pair
        CHECK (
            (identity_issuer IS NULL AND identity_subject IS NULL)
                OR
            (identity_issuer IS NOT NULL AND identity_subject IS NOT NULL)
            );

CREATE UNIQUE INDEX ux_app_users_identity
    ON app_users (identity_issuer, identity_subject)
    WHERE identity_issuer IS NOT NULL
      AND identity_subject IS NOT NULL;