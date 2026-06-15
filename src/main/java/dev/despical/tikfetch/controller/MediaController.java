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

package dev.despical.tikfetch.controller;

import dev.despical.tikfetch.entity.DownloadStatus;
import dev.despical.tikfetch.exception.UserFacingException;
import dev.despical.tikfetch.repository.DownloadedMediaItemRepository;
import dev.despical.tikfetch.repository.DownloadedVideoRepository;
import dev.despical.tikfetch.storage.LocalFileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Controller
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private final DownloadedVideoRepository videoRepository;
    private final DownloadedMediaItemRepository mediaItemRepository;
    private final LocalFileStorageService storageService;

    @GetMapping("/videos/{id}")
    public ResponseEntity<Resource> video(@PathVariable Long id) {
        var video = videoRepository.findById(id)
            .filter(item -> item.getStatus() == DownloadStatus.SUCCESS)
            .orElseThrow(() -> new UserFacingException("Video not found."));
        Resource resource = storageService.loadAsResource(video.getVideoPath());

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(video.getMimeType() == null ? "application/octet-stream" : video.getMimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(downloadFileName(video.getId()))
                .build()
                .toString())
            .body(resource);
    }

    @GetMapping("/audio/{id}")
    public ResponseEntity<Resource> audio(@PathVariable Long id) {
        var video = videoRepository.findById(id)
            .filter(item -> item.getStatus() == DownloadStatus.SUCCESS)
            .filter(item -> item.getAudioPath() != null)
            .orElseThrow(() -> new UserFacingException("MP3 audio is not available for this download."));
        Resource resource = storageService.loadAsResource(video.getAudioPath());

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(video.getAudioMimeType() == null ? "audio/mpeg" : video.getAudioMimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("tikfetch.despical.dev-audio-%s.mp3".formatted(video.getId()))
                .build()
                .toString())
            .body(resource);
    }

    @GetMapping("/stream/{id}")
    public ResponseEntity<Resource> stream(@PathVariable Long id, @RequestParam(required = false) Integer position) {
        var video = videoRepository.findById(id)
            .filter(item -> item.getStatus() == DownloadStatus.SUCCESS)
            .orElseThrow(() -> new UserFacingException("Video not found."));

        if (position != null) {
            var item = mediaItemRepository.findByVideoAndPositionIndex(video, position)
                .orElseThrow(() -> new UserFacingException("Photo not found."));
            Resource resource = storageService.loadAsResource(item.getMediaPath());

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(item.getMimeType() == null ? "image/jpeg" : item.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                    .filename("tikfetch.despical.dev-photo-%s-%s.%s".formatted(id, position + 1, extensionOf(item.getMediaPath())))
                    .build()
                    .toString())
                .body(resource);
        }

        Resource resource = storageService.loadAsResource(video.getVideoPath());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(video.getMimeType() == null ? "video/mp4" : video.getMimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                .filename(downloadFileName(video.getId()))
                .build()
                .toString())
            .body(resource);
    }

    @GetMapping("/thumbnails/{id}")
    public ResponseEntity<Resource> thumbnail(@PathVariable Long id) {
        var video = videoRepository.findById(id)
            .filter(item -> item.getStatus() == DownloadStatus.SUCCESS)
            .filter(item -> item.getThumbnailPath() != null)
            .orElseThrow(() -> new UserFacingException("Thumbnail not found."));

        Resource resource = storageService.loadAsResource(video.getThumbnailPath());
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.IMAGE_JPEG);
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }

    @GetMapping("/gallery/{id}/{position}")
    public ResponseEntity<Resource> galleryImage(@PathVariable Long id, @PathVariable Integer position) {
        var video = videoRepository.findById(id)
            .filter(item -> item.getStatus() == DownloadStatus.SUCCESS)
            .orElseThrow(() -> new UserFacingException("Photo not found."));

        var item = mediaItemRepository.findByVideoAndPositionIndex(video, position)
            .orElseThrow(() -> new UserFacingException("Photo not found."));

        Resource resource = storageService.loadAsResource(item.getMediaPath());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(item.getMimeType() == null ? "image/jpeg" : item.getMimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("tikfetch.despical.dev-photo-%s-%s.%s".formatted(id, position + 1, extensionOf(item.getMediaPath())))
                .build()
                .toString())
            .body(resource);
    }

    @GetMapping("/gallery/{id}/all")
    public ResponseEntity<StreamingResponseBody> galleryArchive(@PathVariable Long id) {
        var video = videoRepository.findById(id)
            .filter(item -> item.getStatus() == DownloadStatus.SUCCESS)
            .orElseThrow(() -> new UserFacingException("Photo gallery not found."));
        var items = mediaItemRepository.findByVideoOrderByPositionIndexAsc(video);

        if (items.isEmpty()) {
            throw new UserFacingException("Photo gallery not found.");
        }

        StreamingResponseBody body = outputStream -> {
            try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
                for (var item : items) {
                    Path path = storageService.resolveStoredPath(item.getMediaPath());
                    ZipEntry entry = new ZipEntry("tikfetch-photo-%02d.%s".formatted(item.getPositionIndex() + 1, extensionOf(item.getMediaPath())));
                    zip.putNextEntry(entry);
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            } catch (IOException exception) {
                throw new UserFacingException("Could not create photo gallery archive.", exception);
            }
        };

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("tikfetch.despical.dev-gallery-%s.zip".formatted(video.getId()))
                .build()
                .toString())
            .body(body);
    }

    private String downloadFileName(Long id) {
        var video = videoRepository.findById(id).orElse(null);
        String extension = "mp4";

        if (video != null && video.getVideoPath() != null) {
            String path = video.getVideoPath();
            int dot = path.lastIndexOf('.');

            if (dot >= 0 && dot < path.length() - 1) {
                extension = path.substring(dot + 1);
            }
        }

        return "tikfetch.despical.dev-media-%s.%s".formatted(id, extension);
    }

    private String extensionOf(String path) {
        int dot = path == null ? -1 : path.lastIndexOf('.');
        return dot >= 0 && dot < path.length() - 1 ? path.substring(dot + 1) : "jpg";
    }
}
