package dev.despical.tikfetch.security;

import dev.despical.tikfetch.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Service
@RequiredArgsConstructor
public class CookieService {

    public static final String ACCESS_COOKIE = "tf_access";
    public static final String REFRESH_COOKIE = "tf_refresh";

    private final AppProperties properties;

    public void writeAuthCookies(HttpServletResponse response, AuthTokens tokens) {
        writeCookie(response, ACCESS_COOKIE, tokens.accessToken(), Duration.ofMinutes(properties.jwt().accessTokenMinutes()));
        writeCookie(response, REFRESH_COOKIE, tokens.refreshToken(), Duration.ofDays(properties.jwt().refreshTokenDays()));
    }

    public void clearAuthCookies(HttpServletResponse response) {
        writeCookie(response, ACCESS_COOKIE, "", Duration.ZERO);
        writeCookie(response, REFRESH_COOKIE, "", Duration.ZERO);
    }

    public Optional<String> readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }

        return Arrays.stream(request.getCookies())
            .filter(cookie -> cookie.getName().equals(name))
            .map(Cookie::getValue)
            .findFirst();
    }

    private void writeCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
        String header = "%s=%s; Max-Age=%d; Path=/; HttpOnly; SameSite=Lax%s".formatted(
            name,
            value,
            maxAge.toSeconds(),
            properties.cookies().secure() ? "; Secure" : ""
        );

        response.addHeader(HttpHeaders.SET_COOKIE, header);
    }
}
