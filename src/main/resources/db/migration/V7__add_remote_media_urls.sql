ALTER TABLE downloaded_videos
    ADD COLUMN remote_video_url TEXT NULL AFTER video_path,
    ADD COLUMN remote_thumbnail_url TEXT NULL AFTER remote_video_url;
