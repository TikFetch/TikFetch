ALTER TABLE downloaded_videos
    ADD COLUMN audio_path VARCHAR(1024) NULL AFTER file_size,
    ADD COLUMN audio_mime_type VARCHAR(120) NULL AFTER audio_path,
    ADD COLUMN audio_file_size BIGINT NULL AFTER audio_mime_type;
