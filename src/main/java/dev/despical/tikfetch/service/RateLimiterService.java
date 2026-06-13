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

package dev.despical.tikfetch.service;

import dev.despical.tikfetch.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final AppProperties properties;
    private final Map<String, Bucket> downloadBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    public boolean tryConsumeDownload(String key) {
        return downloadBuckets.computeIfAbsent(key, _ -> createBucket(
            properties.rateLimit().downloadCapacity(),
            properties.rateLimit().downloadRefillMinutes()
        )).tryConsume(1);
    }

    public boolean tryConsumeLogin(String key) {
        return loginBuckets.computeIfAbsent(key, _ -> createBucket(
            properties.rateLimit().loginCapacity(),
            properties.rateLimit().loginRefillMinutes()
        )).tryConsume(1);
    }

    private Bucket createBucket(long capacity, long refillMinutes) {
        Bandwidth limit = Bandwidth.builder()
            .capacity(capacity)
            .refillIntervally(capacity, Duration.ofMinutes(refillMinutes))
            .build();
        return Bucket.builder().addLimit(limit).build();
    }

    public String getClientIP(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
