package dev.despical.tikfetch.repository;

import dev.despical.tikfetch.entity.Admin;
import dev.despical.tikfetch.entity.RefreshToken;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken token set token.revokedAt = :now where token.admin = :admin and token.revokedAt is null")
    int revokeAllForAdmin(Admin admin, Instant now);

    @Modifying
    @Query("delete from RefreshToken token where token.expiresAt < :cutoff or token.revokedAt is not null")
    int deleteExpiredOrRevoked(Instant cutoff);
}
