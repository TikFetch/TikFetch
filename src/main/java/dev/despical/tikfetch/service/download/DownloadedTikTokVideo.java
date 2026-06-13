package dev.despical.tikfetch.service.download;

import java.nio.file.Path;
import java.util.List;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public record DownloadedTikTokVideo(
    String title,
    String author,
    String authorUrl,
    String sourceVideoId,
    Long durationSeconds,
    Long likeCount,
    Long commentCount,
    Path videoFile,
    boolean image,
    List<Path> galleryImageFiles,
    Path thumbnailFile,
    Path temporaryDirectory
) {
}
