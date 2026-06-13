package dev.despical.tikfetch.service.cleanup;

import dev.despical.tikfetch.config.AppProperties;
import dev.despical.tikfetch.repository.DownloadAttemptRepository;
import dev.despical.tikfetch.service.download.DownloadedVideoRetentionService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Service
@RequiredArgsConstructor
public class CleanupTransactionService {

    private final AppProperties properties;
    private final DownloadedVideoRetentionService retentionService;
    private final DownloadAttemptRepository attemptRepository;

    @Transactional
    public void cleanup() {
        retentionService.enforceSuccessfulRetention();

        Instant cutoff = Instant.now().minus(properties.cleanup().failedAttemptRetentionDays(), ChronoUnit.DAYS);
        attemptRepository.deleteByCreatedAtBefore(cutoff);
    }
}
