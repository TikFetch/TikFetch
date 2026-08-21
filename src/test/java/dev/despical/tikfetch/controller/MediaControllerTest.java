/*
 * TikFetch - A clean web app for saving TikTok videos and photo posts.
 * Copyright (C) 2026  Berke Akçen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.despical.tikfetch.controller;

import dev.despical.tikfetch.entity.DownloadStatus;
import dev.despical.tikfetch.entity.DownloadedVideo;
import dev.despical.tikfetch.repository.DownloadedMediaItemRepository;
import dev.despical.tikfetch.repository.DownloadedVideoRepository;
import dev.despical.tikfetch.storage.LocalFileStorageService;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaControllerTest {

    @Test
    void servesImmutableMediaWithASevenDayPublicCache() {
        DownloadedVideoRepository videoRepository = mock(DownloadedVideoRepository.class);
        DownloadedMediaItemRepository mediaRepository = mock(DownloadedMediaItemRepository.class);
        LocalFileStorageService storageService = mock(LocalFileStorageService.class);
        DownloadedVideo video = new DownloadedVideo();
        video.setId(49L);
        video.setStatus(DownloadStatus.SUCCESS);
        video.setVideoPath("videos/example.mp4");
        video.setMimeType("video/mp4");

        when(videoRepository.findById(49L)).thenReturn(Optional.of(video));
        when(storageService.loadAsResource(video.getVideoPath()))
            .thenReturn(new ByteArrayResource(new byte[] {1}));

        var controller = new MediaController(videoRepository, mediaRepository, storageService);
        var response = controller.stream(49L, null);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo(
            CacheControl.maxAge(Duration.ofDays(7)).cachePublic().immutable().getHeaderValue()
        );
    }
}
