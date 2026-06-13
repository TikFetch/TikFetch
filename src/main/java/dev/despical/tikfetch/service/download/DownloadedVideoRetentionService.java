package dev.despical.tikfetch.service.download;

import dev.despical.tikfetch.config.AppProperties;
import dev.despical.tikfetch.entity.DownloadStatus;
import dev.despical.tikfetch.entity.DownloadedVideo;
import dev.despical.tikfetch.repository.DownloadedMediaItemRepository;
import dev.despical.tikfetch.repository.DownloadedVideoRepository;
import dev.despical.tikfetch.storage.LocalFileStorageService;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Service
@RequiredArgsConstructor
public class DownloadedVideoRetentionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadedVideoRetentionService.class);

    private final AppProperties properties;
    private final DownloadedVideoRepository videoRepository;
    private final DownloadedMediaItemRepository mediaItemRepository;
    private final LocalFileStorageService storageService;

    @Transactional
    public void enforceSuccessfulRetention() {
        int retained = properties.storage().retainedSuccessfulVideos();
        long count = videoRepository.countByStatus(DownloadStatus.SUCCESS);

        if (count <= retained) {
            return;
        }

        List<DownloadedVideo> ordered = videoRepository.findSuccessfulForRetention(PageRequest.of(0, (int) count));
        ordered.stream().skip(retained).forEach(this::deleteVideoAndFiles);
    }

    @Transactional
    public void deleteVideoAndFiles(DownloadedVideo video) {
        mediaItemRepository.findByVideoOrderByPositionIndexAsc(video)
            .stream()
            .filter(item -> !item.getMediaPath().equals(video.getVideoPath()))
            .forEach(item -> storageService.deleteIfStored(item.getMediaPath()));

        mediaItemRepository.deleteByVideo(video);

        boolean videoDeleted = storageService.deleteIfStored(video.getVideoPath());
        boolean thumbnailDeleted = storageService.deleteIfStored(video.getThumbnailPath());

        if (!videoDeleted || !thumbnailDeleted) {
            LOGGER.warn("One or more files could not be deleted for downloaded video id {}", video.getId());
        }

        videoRepository.delete(video);
    }
}
