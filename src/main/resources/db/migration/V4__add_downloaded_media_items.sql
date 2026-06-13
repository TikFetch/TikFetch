CREATE TABLE downloaded_media_items
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id       BIGINT        NOT NULL,
    position_index INT           NOT NULL,
    media_path     VARCHAR(1024) NOT NULL,
    mime_type      VARCHAR(120) NULL,
    file_size      BIGINT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_downloaded_media_items_video FOREIGN KEY (video_id) REFERENCES downloaded_videos (id) ON DELETE CASCADE,
    CONSTRAINT uq_downloaded_media_items_position UNIQUE (video_id, position_index)
);
