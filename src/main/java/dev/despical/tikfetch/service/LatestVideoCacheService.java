/*
 * TikFetch - A clean web app for saving TikTok videos and photo posts.
 * Copyright (C) 2026  Berke Akçen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
import java.util.concurrent.atomic.AtomicReference;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps the homepage video models ready in memory so public requests never wait
 * for the latest-video query or duration probing.
 *
 * @author Despical
 */
@Service
@RequiredArgsConstructor
public class LatestVideoCacheService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LatestVideoCacheService.class);

    private final AppProperties properties;
    private final DownloadedVideoRepository videoRepository;
    private final VideoViewMapper viewMapper;
    private final LocalFileStorageService storageService;
    private final VideoDurationService videoDurationService;
    private final AtomicReference<List<LatestVideoView>> snapshot = new AtomicReference<>(List.of());

    public List<LatestVideoView> current() {
        return snapshot.get();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmOnStartup() {
        refresh();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void refreshAfterChange(LatestVideosChangedEvent event) {
        refresh();
    }

    public synchronized void refresh() {
        try {
            List<LatestVideoView> latest = videoRepository.findByStatusOrderByDownloadedAtDesc(
                    DownloadStatus.SUCCESS,
                    PageRequest.of(0, properties.latestVideosLimit())
                )
                .stream()
                .map(this::ensureDuration)
                .map(viewMapper::toLatestView)
                .toList();

            snapshot.set(latest);
            LOGGER.info("Refreshed the latest-video backend cache with {} entries.", latest.size());
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not refresh the latest-video cache; keeping the previous snapshot.", exception);
        }
    }

    private DownloadedVideo ensureDuration(DownloadedVideo video) {
        if (video.getDurationSeconds() != null || video.getVideoPath() == null) {
            return video;
        }

        try {
            Long duration = videoDurationService.detectDurationSeconds(storageService.resolveStoredPath(video.getVideoPath()));

            if (duration != null && duration > 0) {
                video.setDurationSeconds(duration);
                return videoRepository.save(video);
            }
        } catch (RuntimeException exception) {
            LOGGER.debug("Could not determine duration for cached video id {}.", video.getId(), exception);
        }

        return video;
    }
}
