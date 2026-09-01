/*
 * TikFetch - A clean web app for saving TikTok videos and photo posts.
 * Copyright (C) 2026  Berke Akçen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.despical.tikfetch.service.download;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Despical
 * <p>
 * Created at 30.08.2026
 */
class YtDlpTikTokDownloadServiceTest {

    @Test
    void retriesTemporaryTikTokExtractorFailures() {
        assertThat(YtDlpTikTokDownloadService.isRetryableTikTokError(
            "ERROR: [TikTok] 7678345214133685512: Unexpected response from webpage request"
        )).isTrue();
        assertThat(YtDlpTikTokDownloadService.isRetryableTikTokError(
            "ERROR: [TikTok] Unable to extract universal data for rehydration"
        )).isTrue();
    }

    @Test
    void doesNotRetryPermanentAccessFailures() {
        assertThat(YtDlpTikTokDownloadService.isRetryableTikTokError(
            "ERROR: [TikTok] Your IP address is blocked from accessing this post"
        )).isFalse();
        assertThat(YtDlpTikTokDownloadService.isRetryableTikTokError(null)).isFalse();
    }

    @Test
    void extractsOnlyTheExpectedSssTikMediaHost() {
        assertThat(YtDlpTikTokDownloadService.sssTikVideoUrl(
            "<a href=\"https://tikcdn.io/ssstik/123?st=token&amp;e=123\" class=\"without_watermark\">Download</a>"
        )).contains("https://tikcdn.io/ssstik/123?st=token&e=123");
        assertThat(YtDlpTikTokDownloadService.sssTikVideoUrl(
            "<a href=\"https://example.com/video.mp4\" class=\"without_watermark\">Download</a>"
        )).isEmpty();
    }

    @Test
    void extractsTheSssTikThumbnailHost() {
        assertThat(YtDlpTikTokDownloadService.sssTikThumbnailUrl(
            "<style>.preview { background-image: url(https://tikcdn.io/ssstik/p/preview.jpg?st=token&amp;e=123); }</style>"
        )).contains("https://tikcdn.io/ssstik/p/preview.jpg?st=token&e=123");
    }
}
