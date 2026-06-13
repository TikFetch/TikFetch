CREATE TABLE admin_passkeys
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    admin_id        BIGINT       NOT NULL,
    label           VARCHAR(120) NOT NULL,
    credential_id   BLOB         NOT NULL,
    user_handle     BLOB         NOT NULL,
    public_key_cose BLOB         NOT NULL,
    signature_count BIGINT       NOT NULL DEFAULT 0,
    backup_eligible BOOLEAN      NULL,
    backed_up       BOOLEAN      NULL,
    last_used_at    TIMESTAMP(6) NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_admin_passkeys_admin FOREIGN KEY (admin_id) REFERENCES admins (id) ON DELETE CASCADE,
    INDEX idx_admin_passkeys_admin_id (admin_id),
    INDEX idx_admin_passkeys_credential_id (credential_id(255)),
    INDEX idx_admin_passkeys_user_handle (user_handle(255))
);
