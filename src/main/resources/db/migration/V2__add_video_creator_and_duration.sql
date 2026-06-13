ALTER TABLE downloaded_videos
    ADD COLUMN author_url VARCHAR(2048) NULL AFTER author,
    ADD COLUMN duration_seconds BIGINT NULL AFTER file_size;
