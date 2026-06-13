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
