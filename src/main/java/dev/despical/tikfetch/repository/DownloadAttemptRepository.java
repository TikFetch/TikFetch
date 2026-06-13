package dev.despical.tikfetch.repository;

import dev.despical.tikfetch.entity.DownloadAttempt;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public interface DownloadAttemptRepository extends JpaRepository<DownloadAttempt, Long> {

    List<DownloadAttempt> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long deleteByCreatedAtBefore(Instant cutoff);
}
