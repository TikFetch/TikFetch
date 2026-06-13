package dev.despical.tikfetch.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.despical.tikfetch.config.AppProperties;
import dev.despical.tikfetch.entity.Admin;
import org.junit.Test;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public class JwtTokenServiceTest {

    @Test
    public void createsParseableAccessTokenAndHashesRefreshTokens() {
        JwtTokenService tokenService = new JwtTokenService(properties());
        Admin admin = new Admin();
        admin.setId(42L);
        admin.setUsername("admin");

        String token = tokenService.createAccessToken(admin);

        assertThat(tokenService.parse(token).getSubject()).isEqualTo("admin");
        assertThat(tokenService.hashToken("refresh-token")).hasSize(64);
    }

    private AppProperties properties() {
        return new AppProperties(
            "TikFetch",
            "https://www.tikfetch.despical.dev",
            10,
            new AppProperties.Storage("C:/tikfetch/storage", 10),
            new AppProperties.Cleanup(true, 3600000, 7),
            new AppProperties.Jwt("change-this-to-a-long-random-secret-at-least-256-bits", 15, 30),
            new AppProperties.Admin("admin", "admin@example.com", "change-me"),
            new AppProperties.YtDlp("yt-dlp", 120, "", "", "", "", "", "best", 60, 10, 5, 10, "exp=1:20"),
            new AppProperties.Cookies(false),
            new AppProperties.RateLimit(10, 1, 5, 5)
        );
    }
}
