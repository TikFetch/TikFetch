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
