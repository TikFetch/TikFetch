package dev.despical.tikfetch.dto;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public record SystemInfoView(
    String storageDirectory,
    String totalSpace,
    String usableSpace,
    String healthStatus,
    long successfulVideos,
    long attempts
) {
}
