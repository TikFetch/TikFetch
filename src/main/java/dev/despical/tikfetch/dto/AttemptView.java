package dev.despical.tikfetch.dto;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public record AttemptView(
    Long id,
    String submittedUrl,
    String normalizedUrl,
    String status,
    String message,
    String clientIp,
    String createdAt
) {
}
