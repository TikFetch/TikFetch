package dev.despical.tikfetch.validation;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Component
public class TikTokUrlValidator {

    private static final int MAX_URL_LENGTH = 2048;
    private static final Pattern TIKTOK_MEDIA_PATH = Pattern.compile("^/@[^/]+/(video|photo)/[0-9]+/?$");

    public ValidatedTikTokUrl validateAndNormalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Paste a TikTok video or photo URL first.");
        }

        String trimmed = rawUrl.trim();
        if (trimmed.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("That URL is too long.");
        }

        URI uri = parse(trimmed);
        String scheme = uri.getScheme();

        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Use a valid TikTok http or https URL.");
        }

        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Use a valid TikTok URL.");
        }

        String asciiHost = IDN.toASCII(host).toLowerCase(Locale.ROOT);
        if (!isAllowedTikTokHost(asciiHost)) {
            throw new IllegalArgumentException("Only TikTok video or photo links are supported.");
        }

        String normalizedPath = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
        ValidatedTikTokUrl.MediaKind mediaKind = mediaKind(asciiHost, normalizedPath);

        URI normalized;
        try {
            normalized = new URI("https", null, asciiHost, -1, normalizedPath, null, null).normalize();
        } catch (URISyntaxException _) {
            throw new IllegalArgumentException("Use a valid TikTok URL.");
        }

        return new ValidatedTikTokUrl(trimmed, normalized.toString(), mediaKind);
    }

    private URI parse(String rawUrl) {
        try {
            return new URI(rawUrl);
        } catch (URISyntaxException _) {
            throw new IllegalArgumentException("Use a valid TikTok URL.");
        }
    }

    private boolean isAllowedTikTokHost(String host) {
        return host.equals("tiktok.com")
            || host.equals("www.tiktok.com")
            || host.equals("m.tiktok.com")
            || host.equals("vm.tiktok.com")
            || host.equals("vt.tiktok.com");
    }

    private ValidatedTikTokUrl.MediaKind mediaKind(String host, String path) {
        if (host.equals("vm.tiktok.com") || host.equals("vt.tiktok.com")) {
            return ValidatedTikTokUrl.MediaKind.SHORT;
        }

        var matcher = TIKTOK_MEDIA_PATH.matcher(path);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Only TikTok video or photo links are supported.");
        }

        return "photo".equals(matcher.group(1))
            ? ValidatedTikTokUrl.MediaKind.PHOTO
            : ValidatedTikTokUrl.MediaKind.VIDEO;
    }
}
