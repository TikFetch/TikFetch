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

package dev.despical.tikfetch.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @NotBlank String name,
    @NotBlank String baseUrl,
    @Min(1) int latestVideosLimit,
    Storage storage,
    Cleanup cleanup,
    Jwt jwt,
    Admin admin,
    YtDlp ytDlp,
    Cookies cookies,
    RateLimit rateLimit
) {

    public record Storage(
        @NotBlank String directory,
        @Min(1) int retainedSuccessfulVideos
    ) {
    }

    public record Cleanup(
        boolean enabled,
        @Min(1000) long intervalMs,
        @Min(1) int failedAttemptRetentionDays
    ) {
    }

    public record Jwt(
        @NotBlank String secret,
        @Min(1) int accessTokenMinutes,
        @Min(1) int refreshTokenDays
    ) {
    }

    public record Admin(
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotBlank String password
    ) {
    }

    public record YtDlp(
        @NotBlank String path,
        @Min(1) int timeoutSeconds,
        String cookiesPath,
        String cookiesFromBrowser,
        String proxy,
        String userAgent,
        String extractorArgs,
        String ffmpegLocation,
        @NotBlank String format,
        @Min(1) int socketTimeoutSeconds,
        @Min(0) int retries,
        @Min(0) int extractorRetries,
        @Min(0) int fragmentRetries,
        String retrySleep
    ) {
    }

    public record Cookies(boolean secure) {
    }

    public record RateLimit(
        @Min(1) long downloadCapacity,
        @Min(1) long downloadRefillMinutes,
        @Min(1) long loginCapacity,
        @Min(1) long loginRefillMinutes
    ) {
    }
}
