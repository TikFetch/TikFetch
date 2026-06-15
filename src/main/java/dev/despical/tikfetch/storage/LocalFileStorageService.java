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

package dev.despical.tikfetch.storage;

import dev.despical.tikfetch.config.AppProperties;
import dev.despical.tikfetch.exception.UserFacingException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import dev.despical.tikfetch.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Service
public class LocalFileStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFileStorageService.class);

    private static final Set<String> MEDIA_EXTENSIONS = Set.of("mp4", "webm", "mov", "mkv", "jpg", "jpeg", "png", "webp", "image");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "image");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3");

    private final Path storageRoot;

    public LocalFileStorageService(AppProperties properties) {
        this.storageRoot = Path.of(properties.storage().directory()).toAbsolutePath().normalize();
    }

    public void ensureStorageExists() {
        try {
            Files.createDirectories(storageRoot);
            Files.createDirectories(storageRoot.resolve("videos"));
            Files.createDirectories(storageRoot.resolve("audio"));
            Files.createDirectories(storageRoot.resolve("thumbnails"));
            Files.createDirectories(storageRoot.resolve("tmp"));
        } catch (IOException exception) {
            throw new UserFacingException("Storage is not writable. Please check server configuration.", exception);
        }
    }

    public Path createTempDirectory() {
        ensureStorageExists();

        try {
            return Files.createTempDirectory(storageRoot.resolve("tmp"), "download-");
        } catch (IOException exception) {
            throw new UserFacingException("Could not create a temporary download directory.", exception);
        }
    }

    public StoredFile storeVideo(Path sourceFile, String sourceId) {
        return store(sourceFile, "videos", sourceId, MEDIA_EXTENSIONS, "application/octet-stream");
    }

    public StoredFile storeThumbnail(Path sourceFile, String sourceId) {
        return store(sourceFile, "thumbnails", sourceId, IMAGE_EXTENSIONS, "image/jpeg");
    }

    public StoredFile storeAudio(Path sourceFile, String sourceId) {
        return store(sourceFile, "audio", sourceId, AUDIO_EXTENSIONS, "audio/mpeg");
    }

    public Resource loadAsResource(String relativePath) {
        Path path = resolveStoredPath(relativePath);

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new UserFacingException("The requested file is no longer available.");
        }

        return new FileSystemResource(path);
    }

    public Path resolveStoredPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new UserFacingException("Missing stored file path.");
        }

        Path resolved = storageRoot.resolve(relativePath).normalize();

        if (!resolved.startsWith(storageRoot)) {
            throw new UserFacingException("Invalid stored file path.");
        }

        return resolved;
    }

    public boolean deleteIfStored(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return true;
        }

        try {
            Path path = resolveStoredPath(relativePath);
            return Files.deleteIfExists(path);
        } catch (Exception exception) {
            LOGGER.error("Could not delete stored file '{}'", relativePath, exception);
            return false;
        }
    }

    public Path storageRoot() {
        return storageRoot;
    }

    public void deleteDirectoryQuietly(Path directory) {
        if (directory == null) {
            return;
        }

        Path normalized = directory.toAbsolutePath().normalize();

        if (!normalized.startsWith(storageRoot.resolve("tmp"))) {
            return;
        }

        try (var files = Files.walk(normalized)) {
            files.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        LOGGER.warn("Could not delete temporary file '{}'", path, exception);
                    }
                });
        } catch (IOException exception) {
            LOGGER.warn("Could not clean temporary directory '{}'", normalized, exception);
        }
    }

    private StoredFile store(Path sourceFile, String category, String sourceId, Set<String> allowedExtensions, String fallbackMimeType) {
        if (sourceFile == null || !Files.isRegularFile(sourceFile)) {
            throw new UserFacingException("The downloaded file could not be found.");
        }

        String extension = normalizeExtension(FileUtils.extensionOf(sourceFile));

        if (!allowedExtensions.contains(extension)) {
            throw new UserFacingException("The downloaded file type is not supported.");
        }

        YearMonth now = YearMonth.now();
        String safeId = safeId(sourceId);
        String fileName = safeId + "-" + UUID.randomUUID() + "." + extension;
        Path relative = Path.of(category, String.valueOf(now.getYear()), "%02d".formatted(now.getMonthValue()), fileName);
        Path target = storageRoot.resolve(relative).normalize();

        if (!target.startsWith(storageRoot)) {
            throw new UserFacingException("Invalid storage target path.");
        }

        try {
            Files.createDirectories(target.getParent());
            Files.move(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
            String mimeType = Files.probeContentType(target);

            return new StoredFile(relative.toString().replace('\\', '/'), Files.size(target), mimeType == null ? fallbackMimeType : mimeType);
        } catch (IOException exception) {
            throw new UserFacingException("Could not save the downloaded file.", exception);
        }
    }

    private String safeId(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return UUID.randomUUID().toString();
        }

        String normalized = sourceId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private String normalizeExtension(String extension) {
        return "image".equals(extension) ? "jpg" : extension;
    }
}
