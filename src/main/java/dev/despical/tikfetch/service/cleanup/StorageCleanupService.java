package dev.despical.tikfetch.service.cleanup;

import dev.despical.tikfetch.config.AppProperties;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Service
@RequiredArgsConstructor
public class StorageCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageCleanupService.class);

    private final AppProperties properties;
    private final CleanupTransactionService cleanupTransactionService;

    @Scheduled(fixedDelayString = "${app.cleanup.interval-ms:3600000}")
    public void scheduledCleanup() {
        if (!properties.cleanup().enabled()) {
            return;
        }

        LOGGER.debug("Running scheduled TikFetch cleanup");

        cleanupTransactionService.cleanup();
    }
}
