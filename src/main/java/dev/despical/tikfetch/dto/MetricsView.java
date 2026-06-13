package dev.despical.tikfetch.dto;

import java.util.List;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public record MetricsView(
    String healthStatus,
    List<MetricCardView> runtime,
    List<MetricCardView> system,
    List<MetricCardView> traffic,
    List<MetricCardView> database
) {
}
