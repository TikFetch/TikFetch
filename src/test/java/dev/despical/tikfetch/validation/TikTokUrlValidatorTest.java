package dev.despical.tikfetch.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public class TikTokUrlValidatorTest {

    private final TikTokUrlValidator validator = new TikTokUrlValidator();

    @Test
    public void acceptsTikTokVideoUrlsAndDropsQueryParameters() {
        ValidatedTikTokUrl result = validator.validateAndNormalize("https://www.tiktok.com/@creator/video/123?lang=en");

        assertThat(result.originalUrl()).contains("?lang=en");
        assertThat(result.normalizedUrl()).isEqualTo("https://www.tiktok.com/@creator/video/123");
        assertThat(result.mediaKind()).isEqualTo(ValidatedTikTokUrl.MediaKind.VIDEO);
    }

    @Test
    public void acceptsTikTokPhotoUrls() {
        ValidatedTikTokUrl result = validator.validateAndNormalize("https://www.tiktok.com/@creator/photo/7644218109468970261?lang=en");

        assertThat(result.normalizedUrl()).isEqualTo("https://www.tiktok.com/@creator/photo/7644218109468970261");
        assertThat(result.mediaKind()).isEqualTo(ValidatedTikTokUrl.MediaKind.PHOTO);
    }

    @Test
    public void acceptsShortTikTokHosts() {
        assertThat(validator.validateAndNormalize("https://vm.tiktok.com/abc123").normalizedUrl())
            .isEqualTo("https://vm.tiktok.com/abc123");

        assertThat(validator.validateAndNormalize("https://vt.tiktok.com/abc123").normalizedUrl())
            .isEqualTo("https://vt.tiktok.com/abc123");

        assertThat(validator.validateAndNormalize("https://vm.tiktok.com/abc123").mediaKind())
            .isEqualTo(ValidatedTikTokUrl.MediaKind.SHORT);
    }

    @Test
    public void rejectsNonTikTokHosts() {
        assertThatThrownBy(() -> validator.validateAndNormalize("https://example.com/video/123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Only TikTok");
    }

    @Test
    public void rejectsMalformedInput() {
        assertThatThrownBy(() -> validator.validateAndNormalize("not a url"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
