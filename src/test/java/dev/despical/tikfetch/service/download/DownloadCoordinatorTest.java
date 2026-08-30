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

import dev.despical.tikfetch.entity.DownloadStatus;
import dev.despical.tikfetch.entity.DownloadedVideo;
import dev.despical.tikfetch.repository.DownloadedMediaItemRepository;
import dev.despical.tikfetch.repository.DownloadedVideoRepository;
import dev.despical.tikfetch.storage.LocalFileStorageService;
import dev.despical.tikfetch.validation.TikTokUrlValidator;
import dev.despical.tikfetch.validation.ValidatedTikTokUrl;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DownloadCoordinatorTest {

    @Test
    void returnsACachedDownloadWithoutSchedulingWork() {
        TikTokUrlValidator validator = mock(TikTokUrlValidator.class);
        TikTokDownloadService downloadService = mock(TikTokDownloadService.class);
        LocalFileStorageService storageService = mock(LocalFileStorageService.class);
        DownloadedMediaItemRepository mediaRepository = mock(DownloadedMediaItemRepository.class);
        DownloadedVideoRepository videoRepository = mock(DownloadedVideoRepository.class);
        DownloadAttemptService attemptService = mock(DownloadAttemptService.class);
        DownloadedVideoRetentionService retentionService = mock(DownloadedVideoRetentionService.class);
        VideoDurationService durationService = mock(VideoDurationService.class);
        TikTokUrlResolver resolver = mock(TikTokUrlResolver.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        RemoteMediaSessionStore remoteMediaSessionStore = mock(RemoteMediaSessionStore.class);
        var url = new ValidatedTikTokUrl(
            "https://www.tiktok.com/@creator/video/1",
            "https://www.tiktok.com/@creator/video/1",
            ValidatedTikTokUrl.MediaKind.VIDEO
        );
        DownloadedVideo existing = new DownloadedVideo();
        existing.setStatus(DownloadStatus.SUCCESS);

        when(validator.validateAndNormalize(url.originalUrl())).thenReturn(url);
        when(resolver.resolveForDownload(url)).thenReturn(url);
        when(videoRepository.findFirstByNormalizedUrlAndStatusOrderByDownloadedAtDesc(
            url.normalizedUrl(),
            DownloadStatus.SUCCESS
        )).thenReturn(Optional.of(existing));

        var coordinator = new DownloadCoordinator(
            validator,
            downloadService,
            storageService,
            mediaRepository,
            videoRepository,
            attemptService,
            retentionService,
            durationService,
            resolver,
            eventPublisher,
            transactionManager,
            remoteMediaSessionStore
        );

        assertThat(coordinator.queue(url.originalUrl(), "127.0.0.1")).isSameAs(existing);
        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(downloadService);
    }

    @Test
    void createsAProcessingRecordAndSchedulesTheDownloadAfterCommit() {
        TikTokUrlValidator validator = mock(TikTokUrlValidator.class);
        TikTokDownloadService downloadService = mock(TikTokDownloadService.class);
        LocalFileStorageService storageService = mock(LocalFileStorageService.class);
        DownloadedMediaItemRepository mediaRepository = mock(DownloadedMediaItemRepository.class);
        DownloadedVideoRepository videoRepository = mock(DownloadedVideoRepository.class);
        DownloadAttemptService attemptService = mock(DownloadAttemptService.class);
        DownloadedVideoRetentionService retentionService = mock(DownloadedVideoRetentionService.class);
        VideoDurationService durationService = mock(VideoDurationService.class);
        TikTokUrlResolver resolver = mock(TikTokUrlResolver.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        RemoteMediaSessionStore remoteMediaSessionStore = mock(RemoteMediaSessionStore.class);
        var url = new ValidatedTikTokUrl(
            "https://www.tiktok.com/@creator/video/1",
            "https://www.tiktok.com/@creator/video/1",
            ValidatedTikTokUrl.MediaKind.VIDEO
        );

        when(validator.validateAndNormalize(url.originalUrl())).thenReturn(url);
        when(resolver.resolveForDownload(url)).thenReturn(url);
        when(videoRepository.findFirstByNormalizedUrlAndStatusOrderByDownloadedAtDesc(
            url.normalizedUrl(),
            DownloadStatus.SUCCESS
        )).thenReturn(Optional.empty());
        when(videoRepository.findFirstByNormalizedUrlAndStatusOrderByCreatedAtDesc(
            url.normalizedUrl(),
            DownloadStatus.PROCESSING
        )).thenReturn(Optional.empty());
        when(videoRepository.saveAndFlush(any(DownloadedVideo.class))).thenAnswer(invocation -> {
            DownloadedVideo video = invocation.getArgument(0);
            video.setId(42L);
            return video;
        });

        var coordinator = new DownloadCoordinator(
            validator,
            downloadService,
            storageService,
            mediaRepository,
            videoRepository,
            attemptService,
            retentionService,
            durationService,
            resolver,
            eventPublisher,
            transactionManager,
            remoteMediaSessionStore
        );

        DownloadedVideo queued = coordinator.queue(url.originalUrl(), "127.0.0.1");

        assertThat(queued.getId()).isEqualTo(42L);
        assertThat(queued.getStatus()).isEqualTo(DownloadStatus.PROCESSING);
        verify(eventPublisher).publishEvent((Object) argThat(event ->
            event instanceof DownloadRequestedEvent requested
                && requested.videoId().equals(42L)
                && requested.url().equals(url)
        ));
        verifyNoInteractions(downloadService);
    }
}
