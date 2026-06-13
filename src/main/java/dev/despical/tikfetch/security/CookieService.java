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
