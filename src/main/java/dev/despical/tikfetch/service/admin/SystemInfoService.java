package dev.despical.tikfetch.service.admin;

import dev.despical.tikfetch.dto.SystemInfoView;
import dev.despical.tikfetch.entity.DownloadStatus;
import dev.despical.tikfetch.mapper.VideoViewMapper;
import dev.despical.tikfetch.repository.DownloadAttemptRepository;
import dev.despical.tikfetch.repository.DownloadedVideoRepository;
import dev.despical.tikfetch.storage.LocalFileStorageService;

import java.io.File;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Service
@RequiredArgsConstructor
public class SystemInfoService {

    private final LocalFileStorageService storageService;
    private final DownloadedVideoRepository videoRepository;
    private final DownloadAttemptRepository attemptRepository;
    private final VideoViewMapper mapper;

    public SystemInfoView current() {
        File root = storageService.storageRoot().toFile();

        long successfulVideos = 0;
        long attempts = 0;
        String healthStatus = "UP";

        try {
            successfulVideos = videoRepository.countByStatus(DownloadStatus.SUCCESS);
            attempts = attemptRepository.count();
        } catch (RuntimeException _) {
            healthStatus = "DOWN";
        }

        return new SystemInfoView(
            storageService.storageRoot().toString(),
            format(root.getTotalSpace()),
            format(root.getUsableSpace()),
            healthStatus,
            successfulVideos,
            attempts
        );
    }

    private String format(long bytes) {
        return mapper.humanReadableByteCount(bytes);
    }
}
