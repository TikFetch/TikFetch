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

import dev.despical.tikfetch.config.AppProperties;
import dev.despical.tikfetch.entity.DownloadStatus;
import dev.despical.tikfetch.repository.DownloadedMediaItemRepository;
import dev.despical.tikfetch.repository.DownloadedVideoRepository;
import dev.despical.tikfetch.storage.LocalFileStorageService;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DownloadedVideoRetentionServiceTest {

    @Test
    void neverDeletesVideosNeededByTheHomepage() {
        AppProperties properties = mock(AppProperties.class);
        AppProperties.Storage storageProperties = mock(AppProperties.Storage.class);
        DownloadedVideoRepository videoRepository = mock(DownloadedVideoRepository.class);
        DownloadedMediaItemRepository mediaItemRepository = mock(DownloadedMediaItemRepository.class);
        LocalFileStorageService storageService = mock(LocalFileStorageService.class);

        when(properties.storage()).thenReturn(storageProperties);
        when(storageProperties.retainedSuccessfulVideos()).thenReturn(3);
        when(properties.latestVideosLimit()).thenReturn(9);
        when(videoRepository.countByStatus(DownloadStatus.SUCCESS)).thenReturn(9L);

        var retention = new DownloadedVideoRetentionService(
            properties,
            videoRepository,
            mediaItemRepository,
            storageService
        );

        assertThat(retention.enforceSuccessfulRetention()).isZero();
        verify(videoRepository, never()).findSuccessfulForRetention(org.mockito.ArgumentMatchers.any());
    }
}
