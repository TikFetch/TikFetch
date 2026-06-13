package dev.despical.tikfetch.dto;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public record LatestVideoView(
    Long id,
    String title,
    String thumbnailUrl,
    String originalUrl,
    String streamUrl,
    boolean image,
    String authorUrl,
    String author,
    String duration,
    String fileSize,
    String likeCount,
    String commentCount
) {
}
