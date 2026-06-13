CREATE TABLE admins
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP(6) NOT NULL,
    updated_at    TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_admins_username UNIQUE (username),
    CONSTRAINT uq_admins_email UNIQUE (email)
);

CREATE TABLE refresh_tokens
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    admin_id   BIGINT       NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_admin FOREIGN KEY (admin_id) REFERENCES admins (id) ON DELETE CASCADE,
    INDEX idx_refresh_tokens_admin_id (admin_id),
    INDEX idx_refresh_tokens_expires_at (expires_at)
);

CREATE TABLE downloaded_videos
(
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    original_url    VARCHAR(2048) NOT NULL,
    normalized_url  VARCHAR(2048) NOT NULL,
    source_video_id VARCHAR(128)  NULL,
    title           VARCHAR(255)  NOT NULL,
    author          VARCHAR(255)  NULL,
    thumbnail_path  VARCHAR(1024) NULL,
    video_path      VARCHAR(1024) NULL,
    mime_type       VARCHAR(120)  NULL,
    file_size       BIGINT        NULL,
    status          VARCHAR(32)   NOT NULL,
    error_message   VARCHAR(1024) NULL,
    downloaded_at   TIMESTAMP(6)  NULL,
    created_at      TIMESTAMP(6)  NOT NULL,
    updated_at      TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_downloaded_videos_status_downloaded_at (status, downloaded_at),
    INDEX idx_downloaded_videos_normalized_status (normalized_url(255), status)
);

CREATE TABLE download_attempts
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    submitted_url  VARCHAR(2048) NOT NULL,
    normalized_url VARCHAR(2048) NULL,
    status         VARCHAR(32)   NOT NULL,
    message        VARCHAR(1024) NULL,
    client_ip      VARCHAR(64)   NULL,
    created_at     TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_download_attempts_created_at (created_at),
    INDEX idx_download_attempts_status_created_at (status, created_at)
);

CREATE TABLE site_settings
(
    setting_key   VARCHAR(120)  NOT NULL,
    setting_value VARCHAR(2048) NULL,
    created_at    TIMESTAMP(6)  NOT NULL,
    updated_at    TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (setting_key)
);
