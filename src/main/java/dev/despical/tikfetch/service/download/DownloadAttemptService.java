package dev.despical.tikfetch.service.download;

import dev.despical.tikfetch.entity.DownloadAttempt;
import dev.despical.tikfetch.entity.DownloadStatus;
import dev.despical.tikfetch.repository.DownloadAttemptRepository;
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
public class DownloadAttemptService {

    private final DownloadAttemptRepository attemptRepository;

    @Transactional
    public void record(String submittedUrl, String normalizedUrl, DownloadStatus status, String message, String clientIp) {
        DownloadAttempt attempt = new DownloadAttempt();
        attempt.setSubmittedUrl(submittedUrl == null ? "" : submittedUrl);
        attempt.setNormalizedUrl(normalizedUrl);
        attempt.setStatus(status);
        attempt.setMessage(message);
        attempt.setClientIp(clientIp);

        attemptRepository.save(attempt);
    }
}
