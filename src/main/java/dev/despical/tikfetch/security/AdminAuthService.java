package dev.despical.tikfetch.security;

import dev.despical.tikfetch.entity.Admin;
import dev.despical.tikfetch.entity.RefreshToken;
import dev.despical.tikfetch.repository.AdminRepository;
import dev.despical.tikfetch.repository.RefreshTokenRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthTokens login(String username, String password) {
        Admin admin = adminRepository.findByUsernameIgnoreCase(username)
            .filter(Admin::isEnabled)
            .orElseThrow(() -> new BadCredentialsException("Invalid username or password."));

        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password.");
        }

        return createTokens(admin);
    }

    @Transactional
    public AuthTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadCredentialsException("Missing refresh token.");
        }

        RefreshToken token = refreshTokenRepository.findByTokenHash(jwtTokenService.hashToken(rawRefreshToken))
            .filter(RefreshToken::isUsable)
            .orElseThrow(() -> new BadCredentialsException("Invalid refresh token."));
        token.setRevokedAt(Instant.now());

        refreshTokenRepository.save(token);
        return createTokens(token.getAdmin());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHash(jwtTokenService.hashToken(rawRefreshToken))
            .ifPresent(token -> {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            });
    }

    private AuthTokens createTokens(Admin admin) {
        String refreshTokenValue = randomToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setAdmin(admin);
        refreshToken.setTokenHash(jwtTokenService.hashToken(refreshTokenValue));
        refreshToken.setExpiresAt(jwtTokenService.refreshExpiresAt());

        refreshTokenRepository.save(refreshToken);
        return new AuthTokens(jwtTokenService.createAccessToken(admin), refreshTokenValue);
    }

    private String randomToken() {
        byte[] bytes = new byte[48];

        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
