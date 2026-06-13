ALTER TABLE downloaded_videos
    ADD COLUMN like_count BIGINT NULL AFTER duration_seconds,
    ADD COLUMN comment_count BIGINT NULL AFTER like_count;
