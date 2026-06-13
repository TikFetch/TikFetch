package dev.despical.tikfetch.validation;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public record ValidatedTikTokUrl(String originalUrl, String normalizedUrl, MediaKind mediaKind) {

    public enum MediaKind {
        VIDEO,
        PHOTO,
        SHORT
    }
}
