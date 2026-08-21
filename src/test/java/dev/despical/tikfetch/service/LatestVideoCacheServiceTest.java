/*
 * TikFetch - A clean web app for saving TikTok videos and photo posts.
 * Copyright (C) 2026  Berke Akçen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.despical.tikfetch.service;

import dev.despical.tikfetch.config.AppProperties;
import dev.despical.tikfetch.dto.LatestVideoView;
import dev.despical.tikfetch.entity.DownloadStatus;
import dev.despical.tikfetch.entity.DownloadedVideo;
import dev.despical.tikfetch.mapper.VideoViewMapper;
import dev.despical.tikfetch.repository.DownloadedVideoRepository;
import dev.despical.tikfetch.service.download.VideoDurationService;
import dev.despical.tikfetch.storage.LocalFileStorageService;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LatestVideoCacheServiceTest {

    @Test
    void refreshPublishesAReadyToRenderSnapshot() {
        AppProperties properties = mock(AppProperties.class);
        DownloadedVideoRepository repository = mock(DownloadedVideoRepository.class);
        VideoViewMapper mapper = mock(VideoViewMapper.class);
        LocalFileStorageService storage = mock(LocalFileStorageService.class);
        VideoDurationService durationService = mock(VideoDurationService.class);
        DownloadedVideo video = new DownloadedVideo();
        video.setDurationSeconds(12L);
        LatestVideoView view = new LatestVideoView(
            1L, "Ready", "/thumbnail", "https://www.tiktok.com/video/1", "/stream", null,
            false, null, "Creator", "0:12", "1.0 MB", "1.0K", "20"
        );

        when(properties.latestVideosLimit()).thenReturn(10);
        when(repository.findByStatusOrderByDownloadedAtDesc(DownloadStatus.SUCCESS, PageRequest.of(0, 10)))
            .thenReturn(List.of(video));
        when(mapper.toLatestView(video)).thenReturn(view);

        var cache = new LatestVideoCacheService(properties, repository, mapper, storage, durationService);
        cache.refresh();

        assertThat(cache.current()).containsExactly(view);
        verify(repository).findByStatusOrderByDownloadedAtDesc(DownloadStatus.SUCCESS, PageRequest.of(0, 10));
    }

    @Test
    void rebuildsTheBackendSnapshotWhenVideosChange() {
        AppProperties properties = mock(AppProperties.class);
        DownloadedVideoRepository repository = mock(DownloadedVideoRepository.class);
        VideoViewMapper mapper = mock(VideoViewMapper.class);
        LocalFileStorageService storage = mock(LocalFileStorageService.class);
        VideoDurationService durationService = mock(VideoDurationService.class);

        when(properties.latestVideosLimit()).thenReturn(9);
        when(repository.findByStatusOrderByDownloadedAtDesc(DownloadStatus.SUCCESS, PageRequest.of(0, 9)))
            .thenReturn(List.of());

        var cache = new LatestVideoCacheService(properties, repository, mapper, storage, durationService);
        cache.refreshAfterChange(new LatestVideosChangedEvent());

        assertThat(cache.current()).isEmpty();
        verify(repository).findByStatusOrderByDownloadedAtDesc(DownloadStatus.SUCCESS, PageRequest.of(0, 9));
    }
}
