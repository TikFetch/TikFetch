package dev.despical.tikfetch.dto;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public record AdminVideoView(
    Long id,
    String title,
    String originalUrl,
    String normalizedUrl,
    String author,
    String authorUrl,
    String status,
    String downloadedAt,
    String duration,
    String fileSize,
    String videoPath,
    String thumbnailPath,
    String errorMessage
) {
}
