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
