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
import java.net.URLEncoder;
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
    private static final int MAX_MEDIA_DOWNLOAD_ATTEMPTS = 2;
    private static final URI SSSTIK_PAGE_URI = URI.create("https://ssstik.io/tr");
    private static final URI SSSTIK_RESOLVE_URI = URI.create("https://ssstik.io/abc?url=dl");
    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";

    private static final Pattern IMAGE_POST_PATTERN = Pattern.compile("\"imagePost\"\\s*:\\s*\\{\"images\"\\s*:\\s*\\[(.*?)]\\s*,\\s*\"cover\"", Pattern.DOTALL);
    private static final Pattern IMAGE_ENTRY_PATTERN = Pattern.compile("\\{\"imageURL\"\\s*:\\s*\\{\"urlList\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern JSON_STRING_PATTERN = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern SSSTIK_TOKEN_PATTERN = Pattern.compile("s_tt\\s*=\\s*'([^']+)'");
    private static final Pattern SSSTIK_VIDEO_URL_PATTERN = Pattern.compile("<a\\s+href=\"([^\"]+)\"[^>]*\\bwithout_watermark\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SSSTIK_THUMBNAIL_URL_PATTERN = Pattern.compile("background-image:\\s*url\\((https://[^)]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SSSTIK_AUTHOR_PATTERN = Pattern.compile("<h2>\\s*([^<]+?)\\s*</h2>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SSSTIK_LIKE_COUNT_PATTERN = Pattern.compile("feather-thumbs-up.*?</svg>\\s*<div>\\s*([^<]+?)\\s*</div>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SSSTIK_COMMENT_COUNT_PATTERN = Pattern.compile("feather-message-square.*?</svg>\\s*<div>\\s*([^<]+?)\\s*</div>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern COMPACT_COUNT_PATTERN = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*([KM])?", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIKTOK_VIDEO_ID_PATTERN = Pattern.compile("/video/(\\d+)");

    private final AppProperties properties;
    private final LocalFileStorageService storageService;
    private final ObjectMapper objectMapper;
    private final TikTokUrlResolver urlResolver;
    private final HttpClient httpClient;

    public YtDlpTikTokDownloadService(AppProperties properties, LocalFileStorageService storageService, ObjectMapper objectMapper, TikTokUrlResolver urlResolver) {
        this.properties = properties;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
        this.urlResolver = urlResolver;
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
            DownloadTarget target = resolveDownloadTarget(url);

            runDownload(target, temporaryDirectory);
            Metadata metadata = metadataFromInfoJson(temporaryDirectory)
                .orElseGet(() -> fetchMetadataOrDefault(target));

            Optional<Path> videoFile = locateFile(temporaryDirectory, VIDEO_EXTENSIONS);
            Optional<Path> imageFile = locateFile(temporaryDirectory, IMAGE_EXTENSIONS);
            List<Path> fetchedGalleryImages = target.mediaKind() == MediaKind.PHOTO
                ? fetchPhotoGallery(target, temporaryDirectory)
                : List.of();

            List<Path> galleryImages = fetchedGalleryImages.isEmpty() && imageFile.isPresent()
                ? List.of(imageFile.get())
                : fetchedGalleryImages;

            Path mediaFile = videoFile.or(() -> galleryImages.stream().findFirst())
                .or(() -> imageFile)
                .orElseThrow(() -> new UserFacingException("yt-dlp finished, but no supported media file was created."));

            boolean image = videoFile.isEmpty();
            Path audioFile = image ? null : extractAudio(videoFile.orElseThrow(), temporaryDirectory).orElse(null);
            Path thumbnailFile = image ? null : imageFile.orElse(null);

            readyForCaller = true;
            return new DownloadedTikTokVideo(metadata.title(), metadata.author(), metadata.authorUrl(), metadata.id(), metadata.durationSeconds(), metadata.likeCount(), metadata.commentCount(), mediaFile, audioFile, image, galleryImages, thumbnailFile, temporaryDirectory);
        } finally {
            if (!readyForCaller) {
                storageService.deleteDirectoryQuietly(temporaryDirectory);
            }
        }
    }

    @Override
    public Optional<ResolvedTikTokVideo> resolveForFastStart(ValidatedTikTokUrl url) {
        DownloadTarget target = resolveDownloadTarget(url);

        if (target.mediaKind() != MediaKind.VIDEO) {
            return Optional.empty();
        }

        Optional<ResolvedTikTokVideo> sssTikResult = resolveWithSssTik(target);

        if (sssTikResult.isPresent()) {
            return sssTikResult;
        }

        Path cookieFile = createTemporaryCookieFile();

        try {
            for (int attempt = 1; attempt <= MAX_MEDIA_DOWNLOAD_ATTEMPTS; attempt++) {
                List<String> command = new ArrayList<>();
                command.add(properties.ytDlp().path());
                addCommonOptions(command, cookieFile.toString());
                command.add("--no-playlist");
                command.add("--dump-single-json");
                command.add("--skip-download");
                command.add("-f");
                command.add(properties.ytDlp().format());
                command.add(target.ytDlpUrl());

                ProcessResult result = run(command, null, Duration.ofSeconds(properties.ytDlp().timeoutSeconds()));

                if (result.exitCode() == 0) {
                    JsonNode root = parseJson(result.stdout());
                    String videoUrl = selectedVideoUrl(root);
                    return Optional.of(resolvedVideoFromJson(root, videoUrl, cookieHeader(cookieFile, URI.create(videoUrl).getHost())));
                }

                if (attempt < MAX_MEDIA_DOWNLOAD_ATTEMPTS && isRetryableTikTokError(result.stderr())) {
                    LOGGER.warn("TikTok returned a temporary extractor error while resolving media; retrying");
                    continue;
                }

                throw new UserFacingException(cleanYtDlpError(result.stderr()));
            }
        } finally {
            try {
                Files.deleteIfExists(cookieFile);
            } catch (IOException exception) {
                LOGGER.debug("Could not delete the temporary yt-dlp cookie file", exception);
            }
        }

        return Optional.empty();
    }

    private Optional<ResolvedTikTokVideo> resolveWithSssTik(DownloadTarget target) {
        try {
            HttpResponse<String> page = httpClient.send(HttpRequest.newBuilder(SSSTIK_PAGE_URI)
                .timeout(Duration.ofSeconds(properties.ytDlp().socketTimeoutSeconds()))
                .header("User-Agent", BROWSER_USER_AGENT)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (page.statusCode() != 200) {
                return Optional.empty();
            }

            var tokenMatcher = SSSTIK_TOKEN_PATTERN.matcher(page.body());
            if (!tokenMatcher.find()) {
                return Optional.empty();
            }

            String form = "id=" + URLEncoder.encode(target.ytDlpUrl(), StandardCharsets.UTF_8)
                + "&locale=tr&tt=" + URLEncoder.encode(tokenMatcher.group(1), StandardCharsets.UTF_8)
                + "&debug=ab%3D0%26loc%3DTR";

            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(SSSTIK_RESOLVE_URI)
                .timeout(Duration.ofSeconds(properties.ytDlp().socketTimeoutSeconds()))
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "text/html, */*; q=0.01")
                .header("Origin", "https://ssstik.io")
                .header("Referer", "https://ssstik.io/tr")
                .header("HX-Request", "true")
                .header("HX-Target", "target")
                .header("HX-Trigger", "main_page_text")
                .header("HX-Current-URL", "https://ssstik.io/tr")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                return Optional.empty();
            }

            return sssTikVideoUrl(response.body()).map(videoUrl -> new ResolvedTikTokVideo(
                "TikTok video", sssTikText(SSSTIK_AUTHOR_PATTERN, response.body()).orElse(null), null,
                sourceVideoId(target.ytDlpUrl()), null, sssTikCount(SSSTIK_LIKE_COUNT_PATTERN, response.body()),
                sssTikCount(SSSTIK_COMMENT_COUNT_PATTERN, response.body()), videoUrl,
                sssTikThumbnailUrl(response.body()).orElse(null), null, null
            ));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | RuntimeException exception) {
            LOGGER.debug("SSSTik fallback could not resolve the TikTok video: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    static Optional<String> sssTikVideoUrl(String responseBody) {
        return sssTikUrl(responseBody, SSSTIK_VIDEO_URL_PATTERN);
    }

    static Optional<String> sssTikThumbnailUrl(String responseBody) {
        return sssTikUrl(responseBody, SSSTIK_THUMBNAIL_URL_PATTERN);
    }

    private static Optional<String> sssTikUrl(String responseBody, Pattern urlPattern) {
        if (responseBody == null || responseBody.isBlank()) {
            return Optional.empty();
        }

        var matcher = urlPattern.matcher(responseBody);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String url = matcher.group(1).replace("&amp;", "&");

        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || !(uri.getHost().equals("tikcdn.io") || uri.getHost().endsWith(".tikcdn.io"))) {
                return Optional.empty();
            }

            return Optional.of(uri.toString());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Optional<String> sssTikText(Pattern pattern, String responseBody) {
        var matcher = pattern.matcher(responseBody);
        return matcher.find() ? Optional.of(matcher.group(1).trim()) : Optional.empty();
    }

    private static Long sssTikCount(Pattern pattern, String responseBody) {
        Optional<String> value = sssTikText(pattern, responseBody);
        if (value.isEmpty()) {
            return null;
        }

        var matcher = COMPACT_COUNT_PATTERN.matcher(value.get());
        if (!matcher.find()) {
            return null;
        }

        try {
            double amount = Double.parseDouble(matcher.group(1).replace(',', '.'));
            String suffix = matcher.group(2);
            if (suffix != null) {
                amount *= "M".equalsIgnoreCase(suffix) ? 1_000_000 : 1_000;
            }
            return Math.round(amount);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String sourceVideoId(String url) {
        var matcher = TIKTOK_VIDEO_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    @Override
    public DownloadedTikTokVideo download(ResolvedTikTokVideo resolved) {
        Path temporaryDirectory = storageService.createTempDirectory();
        boolean readyForCaller = false;

        try {
            Path videoFile = temporaryDirectory.resolve("video.mp4");
            downloadRemoteFile(resolved.videoUrl(), videoFile, "TikTok video", resolved.cookieHeader());

            Path thumbnailFile = null;
            if (resolved.thumbnailUrl() != null && !resolved.thumbnailUrl().isBlank()) {
                try {
                    thumbnailFile = temporaryDirectory.resolve("thumbnail.jpg");
                    downloadRemoteFile(resolved.thumbnailUrl(), thumbnailFile, "TikTok thumbnail", resolved.cookieHeader());
                } catch (UserFacingException exception) {
                    thumbnailFile = null;
                    LOGGER.debug("Could not cache the resolved TikTok thumbnail", exception);
                }
            }

            Path audioFile = extractAudio(videoFile, temporaryDirectory).orElse(null);
            readyForCaller = true;
            return new DownloadedTikTokVideo(
                resolved.title(), resolved.author(), resolved.authorUrl(), resolved.sourceVideoId(),
                resolved.durationSeconds(), resolved.likeCount(), resolved.commentCount(), videoFile,
                audioFile, false, List.of(), thumbnailFile, temporaryDirectory
            );
        } finally {
            if (!readyForCaller) {
                storageService.deleteDirectoryQuietly(temporaryDirectory);
            }
        }
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException exception) {
            throw new UserFacingException("Could not parse TikTok media information.", exception);
        }
    }

    private ResolvedTikTokVideo resolvedVideoFromJson(JsonNode root, String videoUrl, String cookieHeader) {
        Metadata metadata = metadataFromJson(root);
        JsonNode selected = root.path("requested_downloads").isArray() && !root.path("requested_downloads").isEmpty()
            ? root.path("requested_downloads").get(0)
            : root;
        String thumbnailUrl = text(root, "thumbnail").orElse(null);
        Long fileSize = longValue(selected, "filesize");

        if (fileSize == null) {
            fileSize = longValue(selected, "filesize_approx");
        }

        return new ResolvedTikTokVideo(
            metadata.title(), metadata.author(), metadata.authorUrl(), metadata.id(), metadata.durationSeconds(),
            metadata.likeCount(), metadata.commentCount(), videoUrl, thumbnailUrl, fileSize, cookieHeader
        );
    }

    private String selectedVideoUrl(JsonNode root) {
        JsonNode selected = root.path("requested_downloads").isArray() && !root.path("requested_downloads").isEmpty()
            ? root.path("requested_downloads").get(0)
            : root;
        return text(selected, "url")
            .or(() -> text(root, "url"))
            .orElseThrow(() -> new UserFacingException("TikTok did not provide a downloadable video URL."));
    }

    private Path createTemporaryCookieFile() {
        try {
            Path cookieFile = Files.createTempFile("tikfetch-yt-dlp-", ".cookies.txt");
            String configured = properties.ytDlp().cookiesPath();

            if (configured != null && !configured.isBlank() && Files.isRegularFile(Path.of(configured.trim()))) {
                Files.copy(Path.of(configured.trim()), cookieFile, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.writeString(cookieFile, "# Netscape HTTP Cookie File\n", StandardCharsets.UTF_8);
            }

            return cookieFile;
        } catch (IOException exception) {
            throw new UserFacingException("Could not prepare the temporary TikTok session.", exception);
        }
    }

    private String cookieHeader(Path cookieFile, String requestHost) {
        if (requestHost == null || requestHost.isBlank()) {
            return null;
        }

        try {
            return Files.readAllLines(cookieFile, StandardCharsets.UTF_8).stream()
                .map(line -> line.startsWith("#HttpOnly_") ? line.substring("#HttpOnly_".length()) : line)
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> line.split("\\t", 7))
                .filter(parts -> parts.length == 7 && domainMatches(requestHost, parts[0]))
                .map(parts -> parts[5] + "=" + parts[6])
                .reduce((first, second) -> first + "; " + second)
                .orElse(null);
        } catch (IOException exception) {
            LOGGER.debug("Could not read the temporary TikTok cookie file", exception);
            return null;
        }
    }

    private boolean domainMatches(String requestHost, String cookieDomain) {
        String host = requestHost.toLowerCase(Locale.ROOT);
        String domain = cookieDomain.toLowerCase(Locale.ROOT);
        return host.equals(domain) || host.endsWith(domain.startsWith(".") ? domain : "." + domain);
    }

    private void downloadRemoteFile(String sourceUrl, Path target, String label, String cookieHeader) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(sourceUrl))
                .timeout(Duration.ofSeconds(properties.ytDlp().timeoutSeconds()))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36")
                .header("Referer", "https://www.tiktok.com/")
                .GET();

            if (cookieHeader != null && !cookieHeader.isBlank()) {
                request.header("Cookie", cookieHeader);
            }

            HttpResponse<InputStream> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() >= 400) {
                response.body().close();
                throw new UserFacingException("Could not download the resolved %s.".formatted(label));
            }

            try (InputStream body = response.body()) {
                Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
            }

            if (!Files.isRegularFile(target) || Files.size(target) == 0) {
                throw new UserFacingException("The resolved %s was empty.".formatted(label));
            }
        } catch (IOException exception) {
            throw new UserFacingException("Could not download the resolved %s.".formatted(label), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new UserFacingException("The resolved %s download was interrupted.".formatted(label), exception);
        }
    }

    private Metadata fetchMetadataOrDefault(DownloadTarget target) {
        try {
            return fetchMetadata(target);
        } catch (UserFacingException exception) {
            LOGGER.warn("Could not fetch TikTok metadata before download, continuing with fallback metadata: {}", exception.getMessage());
            return new Metadata("TikTok video", null, null, null, null, null, null);
        }
    }

    private Metadata fetchMetadata(DownloadTarget target) {
        List<String> command = new ArrayList<>();
        command.add(properties.ytDlp().path());

        addCommonOptions(command);

        if (target.mediaKind() != MediaKind.PHOTO) {
            command.add("--no-playlist");
        }

        command.addAll(List.of(
            "--dump-single-json",
            "--skip-download",
            target.ytDlpUrl()
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

    private void runDownload(DownloadTarget target, Path temporaryDirectory) {
        for (int attempt = 1; attempt <= MAX_MEDIA_DOWNLOAD_ATTEMPTS; attempt++) {
            Path attemptDirectory = temporaryDirectory.resolve("download-" + attempt);

            try {
                Files.createDirectories(attemptDirectory);
            } catch (IOException exception) {
                throw new UserFacingException("Could not prepare the download directory.", exception);
            }

            ProcessResult result = runDownloadAttempt(target, attemptDirectory);

            if (result.exitCode() == 0) {
                return;
            }

            storageService.deleteDirectoryQuietly(attemptDirectory);
            String error = cleanYtDlpError(result.stderr());

            if (attempt < MAX_MEDIA_DOWNLOAD_ATTEMPTS && isRetryableTikTokError(result.stderr())) {
                LOGGER.warn("TikTok returned a temporary extractor or media delivery error; retrying with a fresh request");
                continue;
            }

            throw new UserFacingException(error);
        }
    }

    private ProcessResult runDownloadAttempt(DownloadTarget target, Path attemptDirectory) {
        String outputTemplate = attemptDirectory.resolve(target.mediaKind() == MediaKind.PHOTO ? "media.%(playlist_index)s.%(ext)s" : "video.%(ext)s").toString();
        List<String> command = new ArrayList<>();
        command.add(properties.ytDlp().path());

        addCommonOptions(command);

        if (target.mediaKind() != MediaKind.PHOTO) {
            command.add("--no-playlist");
        }

        command.add("--no-part");
        command.add("--write-thumbnail");
        command.add("--write-info-json");

        if (target.mediaKind() == MediaKind.PHOTO) {
            command.add("--write-pages");
            command.add("--skip-download");
        } else {
            command.add("-f");
            command.add(properties.ytDlp().format());
        }

        command.add("-o");
        command.add(outputTemplate);
        command.add(target.ytDlpUrl());

        return run(command, attemptDirectory, Duration.ofSeconds(properties.ytDlp().timeoutSeconds()));
    }

    static boolean isRetryableTikTokError(String stderr) {
        if (stderr == null) {
            return false;
        }

        String error = stderr.toLowerCase(Locale.ROOT);
        return error.contains("http error 404")
            || error.contains("did not get any data blocks")
            || error.contains("unable to download video data")
            || error.contains("unexpected response from webpage request")
            || error.contains("unable to extract universal data for rehydration")
            || error.contains("unable to extract webpage video data");
    }

    private Optional<Path> extractAudio(Path videoFile, Path temporaryDirectory) {
        Path outputFile = temporaryDirectory.resolve("audio.mp3");
        List<String> command = new ArrayList<>();
        command.add(ffmpegExecutable());
        command.addAll(List.of(
            "-y",
            "-nostdin",
            "-hide_banner",
            "-loglevel",
            "error",
            "-i",
            videoFile.toAbsolutePath().toString(),
            "-vn",
            "-codec:a",
            "libmp3lame",
            "-q:a",
            "0",
            outputFile.toAbsolutePath().toString()
        ));

        ProcessResult result;

        try {
            result = run(command, temporaryDirectory, Duration.ofSeconds(properties.ytDlp().timeoutSeconds()));
        } catch (UserFacingException exception) {
            LOGGER.warn("Could not extract the downloaded TikTok audio as MP3: {}", exception.getMessage());
            return Optional.empty();
        }

        if (result.exitCode() != 0) {
            LOGGER.warn("Could not extract the downloaded TikTok audio as MP3: {}", cleanYtDlpError(result.stderr()));
            return Optional.empty();
        }

        return Files.isRegularFile(outputFile) ? Optional.of(outputFile) : Optional.empty();
    }

    private String ffmpegExecutable() {
        String configured = properties.ytDlp().ffmpegLocation();

        if (configured == null || configured.isBlank()) {
            return "ffmpeg";
        }

        Path configuredPath = Path.of(configured.trim());

        if (!Files.isDirectory(configuredPath)) {
            return configured.trim();
        }

        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")
            ? "ffmpeg.exe"
            : "ffmpeg";
        return configuredPath.resolve(executable).toString();
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
        String normalizedUrl = url.normalizedUrl();

        if (url.mediaKind() == MediaKind.PHOTO) {
            return normalizedUrl.replaceFirst("/photo/", "/video/");
        }

        return normalizedUrl;
    }

    private DownloadTarget resolveDownloadTarget(ValidatedTikTokUrl url) {
        ValidatedTikTokUrl resolvedUrl = urlResolver.resolveForDownload(url);

        return new DownloadTarget(ytDlpUrl(resolvedUrl), resolvedUrl.mediaKind());
    }

    private List<Path> fetchPhotoGallery(DownloadTarget target, Path temporaryDirectory) {
        List<String> imageUrls = extractPhotoImageUrls(temporaryDirectory);

        if (imageUrls.isEmpty()) {
            imageUrls = extractPhotoImageUrls(target);
        }

        if (imageUrls.isEmpty()) {
            return List.of();
        }

        List<Path> files = new ArrayList<>();

        for (int index = 0; index < imageUrls.size(); index++) {
            String imageUrl = imageUrls.get(index);
            Path imageTarget = temporaryDirectory.resolve("gallery-%02d.%s".formatted(index + 1, extensionFromUrl(imageUrl)));

            downloadImage(imageUrl, imageTarget);
            files.add(imageTarget);
        }

        return files;
    }

    private List<String> extractPhotoImageUrls(Path temporaryDirectory) {
        try (var files = Files.walk(temporaryDirectory)) {
            return files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".dump"))
                .map(this::readStringQuietly)
                .map(this::parseImagePostUrls)
                .filter(urls -> !urls.isEmpty())
                .findFirst()
                .orElse(List.of());
        } catch (IOException exception) {
            LOGGER.warn("Could not read yt-dlp page dump for TikTok photo gallery", exception);
            return List.of();
        }
    }

    private String readStringQuietly(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warn("Could not read yt-dlp page dump {}", path.getFileName(), exception);
            return "";
        }
    }

    private List<String> extractPhotoImageUrls(DownloadTarget target) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(target.ytDlpUrl()))
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
        addCommonOptions(command, properties.ytDlp().cookiesPath());
    }

    private void addCommonOptions(List<String> command, String cookiesPath) {
        addOption(command, "--cookies", cookiesPath);
        addOption(command, "--cookies-from-browser", properties.ytDlp().cookiesFromBrowser());
        addOption(command, "--proxy", properties.ytDlp().proxy());
        addOption(command, "--user-agent", properties.ytDlp().userAgent());
        addOption(command, "--extractor-args", properties.ytDlp().extractorArgs());
        addOption(command, "--ffmpeg-location", properties.ytDlp().ffmpegLocation());
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

    private record DownloadTarget(String ytDlpUrl, MediaKind mediaKind) {
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}
