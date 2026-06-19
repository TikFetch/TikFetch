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

package dev.despical.tikfetch.service.download;

import dev.despical.tikfetch.config.AppProperties;
import dev.despical.tikfetch.validation.TikTokUrlValidator;
import dev.despical.tikfetch.validation.ValidatedTikTokUrl;
import dev.despical.tikfetch.validation.ValidatedTikTokUrl.MediaKind;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Despical
 * <p>
 * Created at 19.06.2026
 */
@Component
public class TikTokUrlResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(TikTokUrlResolver.class);

    private final AppProperties properties;
    private final TikTokUrlValidator urlValidator;
    private final HttpClient httpClient;

    public TikTokUrlResolver(AppProperties properties, TikTokUrlValidator urlValidator) {
        this.properties = properties;
        this.urlValidator = urlValidator;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(properties.ytDlp().socketTimeoutSeconds()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public ValidatedTikTokUrl resolveForDownload(ValidatedTikTokUrl url) {
        if (url.mediaKind() != MediaKind.SHORT) {
            return url;
        }

        return resolveShortUrl(url)
            .map(resolved -> new ValidatedTikTokUrl(url.originalUrl(), resolved.normalizedUrl(), resolved.mediaKind()))
            .orElse(url);
    }

    private Optional<ValidatedTikTokUrl> resolveShortUrl(ValidatedTikTokUrl url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.normalizedUrl()))
                .timeout(Duration.ofSeconds(properties.ytDlp().socketTimeoutSeconds()))
                .GET()
                .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            URI resolvedUri = response.uri();

            if (resolvedUri == null || resolvedUri.toString().equals(url.normalizedUrl())) {
                return Optional.empty();
            }

            return Optional.of(urlValidator.validateAndNormalize(resolvedUri.toString()));
        } catch (IOException exception) {
            LOGGER.warn("Could not resolve TikTok short URL '{}': {}", url.normalizedUrl(), exception.getMessage());
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not parse resolved TikTok short URL '{}': {}", url.normalizedUrl(), exception.getMessage());
            return Optional.empty();
        }
    }
}
