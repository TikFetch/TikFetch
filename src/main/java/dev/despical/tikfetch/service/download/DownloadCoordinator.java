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

package dev.despical.tikfetch.service.download;

import dev.despical.tikfetch.entity.DownloadStatus;
import dev.despical.tikfetch.entity.DownloadedMediaItem;
import dev.despical.tikfetch.entity.DownloadedVideo;
import dev.despical.tikfetch.repository.DownloadedMediaItemRepository;
import dev.despical.tikfetch.exception.UserFacingException;
import dev.despical.tikfetch.repository.DownloadedVideoRepository;
import dev.despical.tikfetch.storage.LocalFileStorageService;
import dev.despical.tikfetch.storage.StoredFile;
import dev.despical.tikfetch.service.LatestVideosChangedEvent;
import dev.despical.tikfetch.validation.TikTokUrlValidator;
import dev.despical.tikfetch.validation.ValidatedTikTokUrl;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Service
@RequiredArgsConstructor
public class DownloadCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadCoordinator.class);
    private static final Duration STALE_PROCESSING_AGE = Duration.ofMinutes(15);

    private final TikTokUrlValidator urlValidator;
    private final TikTokDownloadService tikTokDownloadService;
    private final LocalFileStorageService storageService;
    private final DownloadedMediaItemRepository mediaItemRepository;
    private final DownloadedVideoRepository videoRepository;
    private final DownloadAttemptService attemptService;
    private final DownloadedVideoRetentionService retentionService;
    private final VideoDurationService videoDurationService;
    private final TikTokUrlResolver urlResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;
    private final RemoteMediaSessionStore remoteMediaSessionStore;

    @Transactional
    public DownloadedVideo queue(String rawUrl, String clientIp) {
        ValidatedTikTokUrl validatedUrl = validate(rawUrl, clientIp);
        ValidatedTikTokUrl downloadUrl = urlResolver.resolveForDownload(validatedUrl);

        var existing = videoRepository.findFirstByNormalizedUrlAndStatusOrderByDownloadedAtDesc(
                downloadUrl.normalizedUrl(),
                DownloadStatus.SUCCESS
            );

        if (existing.isPresent()) {
            return existing.get();
        }

        var ready = videoRepository.findFirstByNormalizedUrlAndStatusOrderByCreatedAtDesc(
            downloadUrl.normalizedUrl(),
            DownloadStatus.READY
        );

        if (ready.isPresent() && !isStale(ready.get())) {
            return ready.get();
        }

        ready.ifPresent(this::markInterrupted);

        var processing = videoRepository.findFirstByNormalizedUrlAndStatusOrderByCreatedAtDesc(
            downloadUrl.normalizedUrl(),
            DownloadStatus.PROCESSING
        );

        if (processing.isPresent() && !isStale(processing.get())) {
            return processing.get();
        }

        processing.ifPresent(this::markInterrupted);

        DownloadedVideo video = new DownloadedVideo();
        video.setOriginalUrl(downloadUrl.originalUrl());
        video.setNormalizedUrl(downloadUrl.normalizedUrl());
        video.setTitle("Preparing TikTok media");
        video.setStatus(DownloadStatus.PROCESSING);
        video = videoRepository.saveAndFlush(video);

        eventPublisher.publishEvent(new DownloadRequestedEvent(video.getId(), downloadUrl, clientIp));
        return video;
    }

    public void process(DownloadRequestedEvent event) {
        try {
            var resolved = tikTokDownloadService.resolveForFastStart(event.url());

            if (resolved.isPresent()) {
                processFastStart(event, resolved.get());
                return;
            }
        } catch (UserFacingException exception) {
            markFailed(event, exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            markFailed(event, "The media could not be prepared. Please try again later.");
            throw exception;
        }

        transactionTemplate().executeWithoutResult(_ -> {
            DownloadedVideo video = processingVideo(event.videoId());

            if (video != null) {
                performDownload(video, event.url(), event.clientIp());
            }
        });
        eventPublisher.publishEvent(new LatestVideosChangedEvent());
    }

    private void processFastStart(DownloadRequestedEvent event, ResolvedTikTokVideo resolved) {
        remoteMediaSessionStore.put(event.videoId(), resolved.cookieHeader());
        Boolean markedReady;

        try {
            markedReady = transactionTemplate().execute(_ -> {
                DownloadedVideo video = processingVideo(event.videoId());

                if (video == null) {
                    return false;
                }

                video.setTitle(resolved.title() == null || resolved.title().isBlank() ? "TikTok video" : resolved.title());
                video.setAuthor(resolved.author());
                video.setAuthorUrl(resolved.authorUrl());
                video.setSourceVideoId(resolved.sourceVideoId());
                video.setDurationSeconds(resolved.durationSeconds());
                video.setLikeCount(resolved.likeCount());
                video.setCommentCount(resolved.commentCount());
                video.setRemoteVideoUrl(resolved.videoUrl());
                video.setRemoteThumbnailUrl(resolved.thumbnailUrl());
                video.setMimeType("video/mp4");
                video.setFileSize(resolved.fileSize());
                video.setStatus(DownloadStatus.READY);
                video.setDownloadedAt(Instant.now());
                videoRepository.saveAndFlush(video);
                return true;
            });
        } catch (RuntimeException exception) {
            remoteMediaSessionStore.remove(event.videoId());
            throw exception;
        }

        if (!Boolean.TRUE.equals(markedReady)) {
            remoteMediaSessionStore.remove(event.videoId());
            return;
        }

        try {
            DownloadedTikTokVideo downloaded = tikTokDownloadService.download(resolved);
            transactionTemplate().executeWithoutResult(_ -> {
                DownloadedVideo video = videoRepository.findById(event.videoId())
                    .filter(item -> item.getStatus() == DownloadStatus.READY)
                    .orElse(null);

                if (video != null) {
                    storeDownloadedVideo(video, downloaded, event.url(), event.clientIp());
                }
            });
            remoteMediaSessionStore.remove(event.videoId());
            eventPublisher.publishEvent(new LatestVideosChangedEvent());
        } catch (RuntimeException exception) {
            LOGGER.warn("Download {} is ready through its temporary source URL, but local caching failed: {}",
                event.videoId(), exception.getMessage());
        }
    }

    private DownloadedVideo processingVideo(Long id) {
        return videoRepository.findById(id)
            .filter(item -> item.getStatus() == DownloadStatus.PROCESSING)
            .orElse(null);
    }

    private void markFailed(DownloadRequestedEvent event, String message) {
        transactionTemplate().executeWithoutResult(_ -> {
            DownloadedVideo video = processingVideo(event.videoId());

            if (video == null) {
                return;
            }

            video.setStatus(DownloadStatus.FAILED);
            video.setErrorMessage(message);
            videoRepository.save(video);
            attemptService.record(event.url().originalUrl(), event.url().normalizedUrl(), DownloadStatus.FAILED, message, event.clientIp());
        });
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private ValidatedTikTokUrl validate(String rawUrl, String clientIp) {
        try {
            return urlValidator.validateAndNormalize(rawUrl);
        } catch (IllegalArgumentException exception) {
            attemptService.record(rawUrl, null, DownloadStatus.FAILED, exception.getMessage(), clientIp);
            throw new UserFacingException(exception.getMessage(), exception);
        }
    }

    private boolean isStale(DownloadedVideo video) {
        return video.getCreatedAt() == null
            || video.getCreatedAt().isBefore(Instant.now().minus(STALE_PROCESSING_AGE));
    }

    private void markInterrupted(DownloadedVideo video) {
        video.setStatus(DownloadStatus.FAILED);
        video.setErrorMessage("The previous download was interrupted. Please try again.");
        videoRepository.save(video);
    }

    private void performDownload(DownloadedVideo video, ValidatedTikTokUrl validatedUrl, String clientIp) {
        try {
            DownloadedTikTokVideo downloaded = tikTokDownloadService.download(validatedUrl);
            storeDownloadedVideo(video, downloaded, validatedUrl, clientIp);
        } catch (UserFacingException exception) {
            video.setStatus(DownloadStatus.FAILED);
            video.setErrorMessage(exception.getMessage());

            videoRepository.save(video);
            attemptService.record(validatedUrl.originalUrl(), validatedUrl.normalizedUrl(), DownloadStatus.FAILED, exception.getMessage(), clientIp);
            throw exception;
        } catch (RuntimeException exception) {
            String message = "The media could not be prepared. Please try again later.";
            video.setStatus(DownloadStatus.FAILED);
            video.setErrorMessage(message);

            videoRepository.save(video);
            attemptService.record(validatedUrl.originalUrl(), validatedUrl.normalizedUrl(), DownloadStatus.FAILED, message, clientIp);
            throw new UserFacingException(message, exception);
        }
    }

    private void storeDownloadedVideo(DownloadedVideo video, DownloadedTikTokVideo downloaded, ValidatedTikTokUrl validatedUrl, String clientIp) {
        try {
            StoredFile storedVideo = storageService.storeVideo(downloaded.videoFile(), downloaded.sourceVideoId());
            StoredFile storedThumbnail = downloaded.thumbnailFile() == null
                ? null
                : storageService.storeThumbnail(downloaded.thumbnailFile(), downloaded.sourceVideoId());
            StoredFile storedAudio = downloaded.audioFile() == null
                ? null
                : storageService.storeAudio(downloaded.audioFile(), downloaded.sourceVideoId());

            video.setTitle(downloaded.title() == null || downloaded.title().isBlank() ? "TikTok video" : downloaded.title());
            video.setAuthor(downloaded.author());
            video.setAuthorUrl(downloaded.authorUrl());
            video.setSourceVideoId(downloaded.sourceVideoId());
            video.setDurationSeconds(downloaded.image() ? null : resolveDuration(downloaded.durationSeconds(), storedVideo.relativePath()));
            video.setLikeCount(downloaded.likeCount());
            video.setCommentCount(downloaded.commentCount());
            video.setVideoPath(storedVideo.relativePath());
            video.setRemoteVideoUrl(null);
            video.setMimeType(storedVideo.mimeType());
            video.setFileSize(storedVideo.size());
            video.setAudioPath(storedAudio == null ? null : storedAudio.relativePath());
            video.setAudioMimeType(storedAudio == null ? null : storedAudio.mimeType());
            video.setAudioFileSize(storedAudio == null ? null : storedAudio.size());
            video.setThumbnailPath(storedThumbnail == null ? null : storedThumbnail.relativePath());
            video.setRemoteThumbnailUrl(null);
            video.setStatus(DownloadStatus.SUCCESS);
            video.setDownloadedAt(Instant.now());
            video = videoRepository.save(video);

            storeGalleryItems(video, downloaded, storedVideo);
            attemptService.record(validatedUrl.originalUrl(), validatedUrl.normalizedUrl(), DownloadStatus.SUCCESS, "Downloaded successfully.", clientIp);
            retentionService.enforceSuccessfulRetention();
        } finally {
            storageService.deleteDirectoryQuietly(downloaded.temporaryDirectory());
        }
    }

    private Long resolveDuration(Long metadataDuration, String storedVideoPath) {
        if (metadataDuration != null && metadataDuration > 0) {
            return metadataDuration;
        }

        return videoDurationService.detectDurationSeconds(storageService.resolveStoredPath(storedVideoPath));
    }

    private void storeGalleryItems(DownloadedVideo video, DownloadedTikTokVideo downloaded, StoredFile storedPrimary) {
        if (!downloaded.image()) {
            return;
        }

        DownloadedMediaItem first = new DownloadedMediaItem();
        first.setVideo(video);
        first.setPositionIndex(0);
        first.setMediaPath(storedPrimary.relativePath());
        first.setMimeType(storedPrimary.mimeType());
        first.setFileSize(storedPrimary.size());

        mediaItemRepository.save(first);

        int position = 1;

        for (Path imageFile : downloaded.galleryImageFiles()) {
            if (imageFile.equals(downloaded.videoFile())) {
                continue;
            }

            StoredFile storedImage = storageService.storeVideo(imageFile, downloaded.sourceVideoId());
            DownloadedMediaItem item = new DownloadedMediaItem();
            item.setVideo(video);
            item.setPositionIndex(position++);
            item.setMediaPath(storedImage.relativePath());
            item.setMimeType(storedImage.mimeType());
            item.setFileSize(storedImage.size());

            mediaItemRepository.save(item);
        }
    }
}
