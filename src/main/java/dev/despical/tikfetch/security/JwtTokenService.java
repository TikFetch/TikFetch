package dev.despical.tikfetch.security;

import dev.despical.tikfetch.config.AppProperties;
import dev.despical.tikfetch.entity.Admin;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Service
public class JwtTokenService {

    private final AppProperties properties;
    private final SecretKey key;

    public JwtTokenService(AppProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(sha256(properties.jwt().secret()));
    }

    public String createAccessToken(Admin admin) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.jwt().accessTokenMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
            .subject(admin.getUsername())
            .claim("adminId", admin.getId())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public Instant refreshExpiresAt() {
        return Instant.now().plus(properties.jwt().refreshTokenDays(), ChronoUnit.DAYS);
    }

    public String hashToken(String token) {
        return HexFormat.of().formatHex(sha256(token));
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
