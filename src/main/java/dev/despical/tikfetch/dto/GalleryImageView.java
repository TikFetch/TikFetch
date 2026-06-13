package dev.despical.tikfetch.dto;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public record GalleryImageView(
    int index,
    String url,
    String downloadUrl,
    String fileSize
) {
}
