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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.despical.tikfetch.config.AppProperties;
import dev.despical.tikfetch.exception.UserFacingException;
import dev.despical.tikfetch.storage.LocalFileStorageService;
import dev.despical.tikfetch.util.FileUtils;
import dev.despical.tikfetch.validation.ValidatedTikTokUrl;
import dev.despical.tikfetch.validation.ValidatedTikTokUrl.MediaKind;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Service
public class YtDlpTikTokDownloadService implements TikTokDownloadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(YtDlpTikTokDownloadService.class);

    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "webm", "mov", "mkv");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "image");

    private static final Pattern IMAGE_POST_PATTERN = Pattern.compile("\"imagePost\"\\s*:\\s*\\{\"images\"\\s*:\\s*\\[(.*?)]\\s*,\\s*\"cover\"", Pattern.DOTALL);
    private static final Pattern IMAGE_ENTRY_PATTERN = Pattern.compile("\\{\"imageURL\"\\s*:\\s*\\{\"urlList\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern JSON_STRING_PATTERN = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");

    private final AppProperties properties;
    private final LocalFileStorageService storageService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public YtDlpTikTokDownloadService(AppProperties properties, LocalFileStorageService storageService, ObjectMapper objectMapper) {
        this.properties = properties;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(properties.ytDlp().socketTimeoutSeconds()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    @Override
    public DownloadedTikTokVideo download(ValidatedTikTokUrl url) {
        Path temporaryDirectory = storageService.createTempDirectory();
        boolean readyForCaller = false;

        try {
            runDownload(url, temporaryDirectory);
            Metadata metadata = metadataFromInfoJson(temporaryDirectory)
                .orElseGet(() -> fetchMetadataOrDefault(url));

            Optional<Path> videoFile = locateFile(temporaryDirectory, VIDEO_EXTENSIONS);
            Optional<Path> imageFile = locateFile(temporaryDirectory, IMAGE_EXTENSIONS);
            List<Path> fetchedGalleryImages = url.mediaKind() == MediaKind.PHOTO
                ? fetchPhotoGallery(url, temporaryDirectory)
                : List.of();

            List<Path> galleryImages = fetchedGalleryImages.isEmpty() && imageFile.isPresent()
                ? List.of(imageFile.get())
                : fetchedGalleryImages;

            Path mediaFile = videoFile.or(() -> galleryImages.stream().findFirst())
                .or(() -> imageFile)
                .orElseThrow(() -> new UserFacingException("yt-dlp finished, but no supported media file was created."));

            boolean image = videoFile.isEmpty();
            Path thumbnailFile = image ? null : imageFile.orElse(null);

            readyForCaller = true;
            return new DownloadedTikTokVideo(metadata.title(), metadata.author(), metadata.authorUrl(), metadata.id(), metadata.durationSeconds(), metadata.likeCount(), metadata.commentCount(), mediaFile, image, galleryImages, thumbnailFile, temporaryDirectory);
        } finally {
            if (!readyForCaller) {
                storageService.deleteDirectoryQuietly(temporaryDirectory);
            }
        }
    }

    private Metadata fetchMetadataOrDefault(ValidatedTikTokUrl url) {
        try {
            return fetchMetadata(url);
        } catch (UserFacingException exception) {
            LOGGER.warn("Could not fetch TikTok metadata before download, continuing with fallback metadata: {}", exception.getMessage());
            return new Metadata("TikTok video", null, null, null, null, null, null);
        }
    }

    private Metadata fetchMetadata(ValidatedTikTokUrl url) {
        List<String> command = new ArrayList<>();
        command.add(properties.ytDlp().path());

        addCommonOptions(command);

        if (url.mediaKind() != MediaKind.PHOTO) {
            command.add("--no-playlist");
        }

        command.addAll(List.of(
            "--dump-single-json",
            "--skip-download",
            ytDlpUrl(url)
        ));

        ProcessResult result = run(command, null, Duration.ofSeconds(properties.ytDlp().timeoutSeconds()));

        if (result.exitCode() != 0) {
            throw new UserFacingException(cleanYtDlpError(result.stderr()));
        }

        try {
            JsonNode root = objectMapper.readTree(result.stdout());
            return metadataFromJson(root);
        } catch (IOException exception) {
            LOGGER.warn("Could not parse yt-dlp metadata JSON", exception);
            return new Metadata("TikTok video", null, null, null, null, null, null);
        }
    }

    private void runDownload(ValidatedTikTokUrl url, Path temporaryDirectory) {
        String outputTemplate = temporaryDirectory.resolve(url.mediaKind() == MediaKind.PHOTO ? "media.%(playlist_index)s.%(ext)s" : "video.%(ext)s").toString();
        List<String> command = new ArrayList<>();
        command.add(properties.ytDlp().path());

        addCommonOptions(command);

        if (url.mediaKind() != MediaKind.PHOTO) {
            command.add("--no-playlist");
        }

        command.add("--no-part");
        command.add("--write-thumbnail");
        command.add("--write-info-json");

        if (url.mediaKind() == MediaKind.PHOTO) {
            command.add("--skip-download");
        } else {
            command.add("-f");
            command.add(properties.ytDlp().format());
        }

        command.add("-o");
        command.add(outputTemplate);
        command.add(ytDlpUrl(url));

        ProcessResult result = run(command, temporaryDirectory, Duration.ofSeconds(properties.ytDlp().timeoutSeconds()));

        if (result.exitCode() != 0) {
            throw new UserFacingException(cleanYtDlpError(result.stderr()));
        }
    }

    private Optional<Metadata> metadataFromInfoJson(Path temporaryDirectory) {
        try (var files = Files.walk(temporaryDirectory)) {
            return files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".info.json"))
                .findFirst()
                .flatMap(this::readMetadataFile);
        } catch (IOException exception) {
            LOGGER.warn("Could not read yt-dlp info JSON", exception);
            return Optional.empty();
        }
    }

    private Optional<Metadata> readMetadataFile(Path path) {
        try {
            return Optional.of(metadataFromJson(objectMapper.readTree(path.toFile())));
        } catch (IOException exception) {
            LOGGER.warn("Could not parse yt-dlp info JSON {}", path.getFileName(), exception);
            return Optional.empty();
        }
    }

    private Metadata metadataFromJson(JsonNode root) {
        String title = text(root, "title").orElse("TikTok video");
        String author = text(root, "uploader").or(() -> text(root, "creator")).orElse(null);
        String authorUrl = text(root, "uploader_url").or(() -> text(root, "channel_url")).orElse(null);
        String id = text(root, "id").orElse(null);
        Long durationSeconds = root.hasNonNull("duration") ? Math.round(root.get("duration").asDouble()) : null;
        Long likeCount = longValue(root, "like_count");
        Long commentCount = longValue(root, "comment_count");

        return new Metadata(title, author, authorUrl, id, durationSeconds, likeCount, commentCount);
    }

    private String ytDlpUrl(ValidatedTikTokUrl url) {
        if (url.mediaKind() == MediaKind.PHOTO) {
            return url.originalUrl().replaceFirst("/photo/", "/video/");
        }

        return url.originalUrl();
    }

    private List<Path> fetchPhotoGallery(ValidatedTikTokUrl url, Path temporaryDirectory) {
        List<String> imageUrls = extractPhotoImageUrls(url);

        if (imageUrls.isEmpty()) {
            return List.of();
        }

        List<Path> files = new ArrayList<>();

        for (int index = 0; index < imageUrls.size(); index++) {
            String imageUrl = imageUrls.get(index);
            Path target = temporaryDirectory.resolve("gallery-%02d.%s".formatted(index + 1, extensionFromUrl(imageUrl)));

            downloadImage(imageUrl, target);
            files.add(target);
        }

        return files;
    }

    private List<String> extractPhotoImageUrls(ValidatedTikTokUrl url) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(ytDlpUrl(url)))
                .timeout(Duration.ofSeconds(properties.ytDlp().timeoutSeconds()))
                .GET();

            String userAgent = properties.ytDlp().userAgent();
            requestBuilder.header("User-Agent", userAgent == null || userAgent.isBlank()
                ? "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36"
                : userAgent);

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 400) {
                LOGGER.warn("Could not fetch TikTok photo gallery HTML, status {}", response.statusCode());
                return List.of();
            }

            return parseImagePostUrls(response.body());
        } catch (IOException exception) {
            LOGGER.warn("Could not fetch TikTok photo gallery HTML: {}", exception.getMessage());
            return List.of();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not parse TikTok photo gallery HTML: {}", exception.getMessage());
            return List.of();
        }
    }

    private List<String> parseImagePostUrls(String html) {
        var postMatcher = IMAGE_POST_PATTERN.matcher(html);

        if (!postMatcher.find()) {
            return List.of();
        }

        String imagesJson = postMatcher.group(1);
        var imageMatcher = IMAGE_ENTRY_PATTERN.matcher(imagesJson);

        LinkedHashSet<String> urls = new LinkedHashSet<>();

        while (imageMatcher.find()) {
            var stringMatcher = JSON_STRING_PATTERN.matcher(imageMatcher.group(1));

            if (stringMatcher.find()) {
                decodeJsonString(stringMatcher.group(1)).ifPresent(urls::add);
            }
        }

        return List.copyOf(urls);
    }

    private Optional<String> decodeJsonString(String value) {
        try {
            return Optional.of(objectMapper.readValue("\"" + value + "\"", String.class));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private void downloadImage(String imageUrl, Path target) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(properties.ytDlp().timeoutSeconds()))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36")
                .GET()
                .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() >= 400) {
                throw new UserFacingException("Could not download one of the TikTok photos.");
            }

            try (InputStream body = response.body()) {
                Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new UserFacingException("Could not download one of the TikTok photos.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new UserFacingException("The photo download was interrupted.", exception);
        }
    }

    private String extensionFromUrl(String imageUrl) {
        String path = URI.create(imageUrl).getPath().toLowerCase(Locale.ROOT);

        if (path.endsWith(".png")) {
            return "png";
        }

        if (path.endsWith(".webp")) {
            return "webp";
        }

        return "jpg";
    }

    private void addCommonOptions(List<String> command) {
        addOption(command, "--cookies", properties.ytDlp().cookiesPath());
        addOption(command, "--cookies-from-browser", properties.ytDlp().cookiesFromBrowser());
        addOption(command, "--proxy", properties.ytDlp().proxy());
        addOption(command, "--user-agent", properties.ytDlp().userAgent());
        addOption(command, "--extractor-args", properties.ytDlp().extractorArgs());
        addOption(command, "--socket-timeout", String.valueOf(properties.ytDlp().socketTimeoutSeconds()));
        addOption(command, "--retries", String.valueOf(properties.ytDlp().retries()));
        addOption(command, "--extractor-retries", String.valueOf(properties.ytDlp().extractorRetries()));
        addOption(command, "--fragment-retries", String.valueOf(properties.ytDlp().fragmentRetries()));
        addOption(command, "--retry-sleep", properties.ytDlp().retrySleep());
    }

    private void addOption(List<String> command, String option, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        command.add(option);
        command.add(value.trim());
    }

    private ProcessResult run(List<String> command, Path directory, Duration timeout) {
        ProcessBuilder processBuilder = new ProcessBuilder(new ArrayList<>(command));

        if (directory != null) {
            processBuilder.directory(directory.toFile());
        }

        try {
            Process process = processBuilder.start();
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));

            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!completed) {
                process.destroyForcibly();
                throw new UserFacingException("The download timed out. Please try again later.");
            }

            return new ProcessResult(process.exitValue(), stdout.join(), stderr.join());
        } catch (IOException exception) {
            throw new UserFacingException("yt-dlp is not available. Install it or configure YT_DLP_PATH.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new UserFacingException("The download was interrupted.", exception);
        }
    }

    private String readStream(java.io.InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warn("Could not read yt-dlp process output", exception);
            return "";
        }
    }

    private Optional<Path> locateFile(Path directory, Set<String> extensions) {
        try (var files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                .filter(path -> extensions.contains(FileUtils.extensionOf(path)))
                .max(Comparator.comparingLong(this::sizeQuietly));
        } catch (IOException exception) {
            throw new UserFacingException("Could not read downloaded files.", exception);
        }
    }

    private long sizeQuietly(Path path) {
        try {
            return Files.size(path);
        } catch (IOException _) {
            return -1L;
        }
    }

    private Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {
            return Optional.empty();
        }

        String text = value.asText();
        return text == null || text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);

        if (value == null || value.isNull() || !value.canConvertToLong()) {
            return null;
        }

        return value.asLong();
    }

    private String cleanYtDlpError(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return "The video could not be downloaded. It may be private, deleted, or unavailable.";
        }

        String cleaned = stderr.lines()
            .filter(line -> !line.isBlank())
            .reduce((_, second) -> second)
            .orElse(stderr)
            .replace("ERROR:", "")
            .trim();
        return cleaned.length() > 220 ? cleaned.substring(0, 220) + "..." : cleaned;
    }

    private record Metadata(String title, String author, String authorUrl, String id, Long durationSeconds,
                            Long likeCount, Long commentCount) {
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}
