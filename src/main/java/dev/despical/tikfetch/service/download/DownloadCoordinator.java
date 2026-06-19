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
import dev.despical.tikfetch.validation.TikTokUrlValidator;
import dev.despical.tikfetch.validation.ValidatedTikTokUrl;

import java.nio.file.Path;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Service
@RequiredArgsConstructor
public class DownloadCoordinator {

    private final TikTokUrlValidator urlValidator;
    private final TikTokDownloadService tikTokDownloadService;
    private final LocalFileStorageService storageService;
    private final DownloadedMediaItemRepository mediaItemRepository;
    private final DownloadedVideoRepository videoRepository;
    private final DownloadAttemptService attemptService;
    private final DownloadedVideoRetentionService retentionService;
    private final VideoDurationService videoDurationService;
    private final TikTokUrlResolver urlResolver;

    @Transactional(noRollbackFor = UserFacingException.class)
    public DownloadedVideo download(String rawUrl, String clientIp) {
        ValidatedTikTokUrl validatedUrl = validate(rawUrl, clientIp);
        ValidatedTikTokUrl downloadUrl = urlResolver.resolveForDownload(validatedUrl);

        return videoRepository.findFirstByNormalizedUrlAndStatusOrderByDownloadedAtDesc(downloadUrl.normalizedUrl(), DownloadStatus.SUCCESS)
            .orElseGet(() -> performDownload(downloadUrl, clientIp));
    }

    private ValidatedTikTokUrl validate(String rawUrl, String clientIp) {
        try {
            return urlValidator.validateAndNormalize(rawUrl);
        } catch (IllegalArgumentException exception) {
            attemptService.record(rawUrl, null, DownloadStatus.FAILED, exception.getMessage(), clientIp);
            throw new UserFacingException(exception.getMessage(), exception);
        }
    }

    private DownloadedVideo performDownload(ValidatedTikTokUrl validatedUrl, String clientIp) {
        DownloadedVideo video = new DownloadedVideo();
        video.setOriginalUrl(validatedUrl.originalUrl());
        video.setNormalizedUrl(validatedUrl.normalizedUrl());
        video.setTitle("TikTok video");
        video.setStatus(DownloadStatus.PROCESSING);
        video = videoRepository.saveAndFlush(video);

        try {
            DownloadedTikTokVideo downloaded = tikTokDownloadService.download(validatedUrl);

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
                video.setMimeType(storedVideo.mimeType());
                video.setFileSize(storedVideo.size());
                video.setAudioPath(storedAudio == null ? null : storedAudio.relativePath());
                video.setAudioMimeType(storedAudio == null ? null : storedAudio.mimeType());
                video.setAudioFileSize(storedAudio == null ? null : storedAudio.size());
                video.setThumbnailPath(storedThumbnail == null ? null : storedThumbnail.relativePath());
                video.setStatus(DownloadStatus.SUCCESS);
                video.setDownloadedAt(Instant.now());
                video = videoRepository.save(video);

                storeGalleryItems(video, downloaded, storedVideo);

                attemptService.record(validatedUrl.originalUrl(), validatedUrl.normalizedUrl(), DownloadStatus.SUCCESS, "Downloaded successfully.", clientIp);
                retentionService.enforceSuccessfulRetention();
                return video;
            } finally {
                storageService.deleteDirectoryQuietly(downloaded.temporaryDirectory());
            }
        } catch (UserFacingException exception) {
            video.setStatus(DownloadStatus.FAILED);
            video.setErrorMessage(exception.getMessage());

            videoRepository.save(video);
            attemptService.record(validatedUrl.originalUrl(), validatedUrl.normalizedUrl(), DownloadStatus.FAILED, exception.getMessage(), clientIp);
            throw exception;
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
