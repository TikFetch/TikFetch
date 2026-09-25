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
import dev.despical.tikfetch.service.CardThumbnailService;
import dev.despical.tikfetch.repository.DownloadedMediaItemRepository;
import dev.despical.tikfetch.repository.DownloadedVideoRepository;
import dev.despical.tikfetch.service.download.RemoteMediaSessionStore;
import dev.despical.tikfetch.storage.LocalFileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
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

    private static final CacheControl PUBLIC_MEDIA_CACHE = CacheControl.maxAge(Duration.ofDays(7))
        .cachePublic()
        .immutable();
    private static final String REMOTE_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36";

    private final DownloadedVideoRepository videoRepository;
    private final DownloadedMediaItemRepository mediaItemRepository;
    private final LocalFileStorageService storageService;
    private final CardThumbnailService cardThumbnailService;
    private final RemoteMediaSessionStore remoteMediaSessionStore;
    private final HttpClient remoteHttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    @GetMapping("/videos/{id}")
    public ResponseEntity<Resource> video(@PathVariable Long id) {
        var video = videoRepository.findById(id)
            .filter(this::isAvailable)
            .orElseThrow(() -> new UserFacingException("Video not found."));

        if (video.getVideoPath() == null) {
            return proxyRemoteMedia(video.getRemoteVideoUrl(), video.getId(), true, null, MediaType.valueOf("video/mp4"));
        }

        Resource resource = storageService.loadAsResource(video.getVideoPath());

        return ResponseEntity.ok()
            .cacheControl(PUBLIC_MEDIA_CACHE)
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
            .cacheControl(PUBLIC_MEDIA_CACHE)
            .contentType(MediaType.parseMediaType(video.getAudioMimeType() == null ? "audio/mpeg" : video.getAudioMimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("tikfetch.despical.dev-audio-%s.mp3".formatted(video.getId()))
                .build()
                .toString())
            .body(resource);
    }

    @GetMapping("/stream/{id}")
    public ResponseEntity<Resource> stream(
        @PathVariable Long id,
        @RequestParam(required = false) Integer position,
        @RequestHeader(value = HttpHeaders.RANGE, required = false) String range
    ) {
        var video = videoRepository.findById(id)
            .filter(this::isAvailable)
            .orElseThrow(() -> new UserFacingException("Video not found."));

        if (position != null) {
            var item = mediaItemRepository.findByVideoAndPositionIndex(video, position)
                .orElseThrow(() -> new UserFacingException("Photo not found."));
            Resource resource = storageService.loadAsResource(item.getMediaPath());

            return ResponseEntity.ok()
                .cacheControl(PUBLIC_MEDIA_CACHE)
                .contentType(MediaType.parseMediaType(item.getMimeType() == null ? "image/jpeg" : item.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                    .filename("tikfetch.despical.dev-photo-%s-%s.%s".formatted(id, position + 1, extensionOf(item.getMediaPath())))
                    .build()
                    .toString())
                .body(resource);
        }

        if (video.getVideoPath() == null) {
            return proxyRemoteMedia(video.getRemoteVideoUrl(), video.getId(), false, range, MediaType.valueOf("video/mp4"));
        }

        Resource resource = storageService.loadAsResource(video.getVideoPath());
        return ResponseEntity.ok()
            .cacheControl(PUBLIC_MEDIA_CACHE)
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
            .filter(this::isAvailable)
            .filter(item -> item.getThumbnailPath() != null || item.getRemoteThumbnailUrl() != null)
            .orElseThrow(() -> new UserFacingException("Thumbnail not found."));

        if (video.getThumbnailPath() == null) {
            return proxyRemoteMedia(video.getRemoteThumbnailUrl(), video.getId(), false, null, MediaType.IMAGE_JPEG);
        }

        Resource resource = storageService.loadAsResource(video.getThumbnailPath());
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.IMAGE_JPEG);
        return ResponseEntity.ok()
            .cacheControl(PUBLIC_MEDIA_CACHE)
            .contentType(mediaType)
            .body(resource);
    }

    @GetMapping("/card-thumbnails/{id}")
    public ResponseEntity<Resource> cardThumbnail(@PathVariable Long id) {
        var video = videoRepository.findById(id)
            .filter(this::isAvailable)
            .filter(item -> item.getThumbnailPath() != null || item.getRemoteThumbnailUrl() != null)
            .orElseThrow(() -> new UserFacingException("Thumbnail not found."));

        if (video.getThumbnailPath() == null) {
            return proxyRemoteMedia(video.getRemoteThumbnailUrl(), id, false, null, MediaType.IMAGE_JPEG);
        }

        Resource original = storageService.loadAsResource(video.getThumbnailPath());
        var card = cardThumbnailService.cardThumbnail(video.getThumbnailPath());
        if (card.isPresent()) {
            return ResponseEntity.ok()
                .cacheControl(PUBLIC_MEDIA_CACHE)
                .contentType(MediaType.IMAGE_JPEG)
                .body(new ByteArrayResource(card.get()));
        }

        return ResponseEntity.ok()
            .cacheControl(PUBLIC_MEDIA_CACHE)
            .contentType(MediaTypeFactory.getMediaType(original).orElse(MediaType.IMAGE_JPEG))
            .body(original);
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
            .cacheControl(PUBLIC_MEDIA_CACHE)
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
            .cacheControl(PUBLIC_MEDIA_CACHE)
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

    private ResponseEntity<Resource> proxyRemoteMedia(
        String remoteUrl,
        Long id,
        boolean attachment,
        String range,
        MediaType fallbackMediaType
    ) {
        URI remoteUri = validatedRemoteUri(remoteUrl);

        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(remoteUri)
                .timeout(Duration.ofSeconds(60))
                .header(HttpHeaders.USER_AGENT, REMOTE_USER_AGENT)
                .header(HttpHeaders.REFERER, "https://www.tiktok.com/")
                .GET();
            remoteMediaSessionStore.cookieHeader(id)
                .filter(cookie -> !cookie.isBlank())
                .ifPresent(cookie -> request.header(HttpHeaders.COOKIE, cookie));

            if (range != null && !range.isBlank()) {
                request.header(HttpHeaders.RANGE, range);
            }

            HttpResponse<InputStream> upstream = remoteHttpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());

            if (upstream.statusCode() >= 400) {
                upstream.body().close();
                throw new UserFacingException("The temporary TikTok media URL is no longer available. Please submit the link again.");
            }

            Resource body = new InputStreamResource(upstream.body());
            MediaType mediaType = upstream.headers().firstValue(HttpHeaders.CONTENT_TYPE)
                .map(MediaType::parseMediaType)
                .orElse(fallbackMediaType);
            ResponseEntity.BodyBuilder response = ResponseEntity.status(upstream.statusCode())
                .cacheControl(CacheControl.noStore())
                .contentType(mediaType);

            if (attachment) {
                response.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                    .filename(downloadFileName(id))
                    .build()
                    .toString());
            }

            upstream.headers().firstValueAsLong(HttpHeaders.CONTENT_LENGTH).ifPresent(response::contentLength);
            upstream.headers().firstValue(HttpHeaders.CONTENT_RANGE).ifPresent(value -> response.header(HttpHeaders.CONTENT_RANGE, value));
            upstream.headers().firstValue(HttpHeaders.ACCEPT_RANGES).ifPresent(value -> response.header(HttpHeaders.ACCEPT_RANGES, value));
            return response.body(body);
        } catch (IOException exception) {
            throw new UserFacingException("Could not stream the temporary TikTok video.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new UserFacingException("The temporary TikTok video stream was interrupted.", exception);
        }
    }

    private URI validatedRemoteUri(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            throw new UserFacingException("The temporary TikTok media URL is unavailable.");
        }

        URI uri;

        try {
            uri = URI.create(remoteUrl);
        } catch (IllegalArgumentException exception) {
            throw new UserFacingException("The temporary TikTok media URL is invalid.", exception);
        }

        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowedHost = host.endsWith(".tiktokcdn.com")
            || host.endsWith(".tiktokcdn-us.com")
            || host.endsWith(".tiktokv.com")
            || host.endsWith(".byteoversea.com")
            || host.endsWith(".tiktok.com")
            || host.equals("tikcdn.io")
            || host.endsWith(".tikcdn.io");

        if (!"https".equalsIgnoreCase(uri.getScheme()) || !allowedHost) {
            throw new UserFacingException("The temporary media host is not trusted.");
        }

        return uri;
    }

    private boolean isAvailable(dev.despical.tikfetch.entity.DownloadedVideo video) {
        return video.getStatus() == DownloadStatus.READY || video.getStatus() == DownloadStatus.SUCCESS;
    }

    private String extensionOf(String path) {
        int dot = path == null ? -1 : path.lastIndexOf('.');
        return dot >= 0 && dot < path.length() - 1 ? path.substring(dot + 1) : "jpg";
    }
}
