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

package dev.despical.tikfetch.service.admin;

import dev.despical.tikfetch.dto.MetricCardView;
import dev.despical.tikfetch.dto.MetricsView;
import dev.despical.tikfetch.entity.DownloadStatus;
import dev.despical.tikfetch.mapper.VideoViewMapper;
import dev.despical.tikfetch.repository.DownloadAttemptRepository;
import dev.despical.tikfetch.repository.DownloadedVideoRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.lang.management.*;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Service
@RequiredArgsConstructor
public class AdminMetricsService {

    private final MeterRegistry meterRegistry;
    private final DownloadedVideoRepository videoRepository;
    private final DownloadAttemptRepository attemptRepository;
    private final VideoViewMapper mapper;

    public MetricsView current() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        double virtualThreads = Optional.ofNullable(meterRegistry.find("jvm.threads.virtual.started").gauge()).map(Gauge::value).orElse(0D);
        long successfulVideos = safeCountSuccessfulVideos();
        long attempts = safeCountAttempts();

        List<MetricCardView> runtimeCards = List.of(
            new MetricCardView("Health", healthStatus(), "Local DB and storage checks"),
            new MetricCardView("Uptime", formatDuration(runtime.getUptime()), runtime.getVmName()),
            new MetricCardView("Heap memory", memoryPair(heap.getUsed(), heap.getMax()), "Used / max"),
            new MetricCardView("Non-heap memory", mapper.humanReadableByteCount(nonHeap.getUsed()), "Metaspace, code cache, JVM internals"),
            new MetricCardView("Garbage collection", timerMaxMillis("jvm.gc.pause"), "Max pause observed")
        );

        List<MetricCardView> systemCards = List.of(
            new MetricCardView("System CPU", percent(systemCpu()), "Current machine load"),
            new MetricCardView("Process CPU", percent(processCpu()), "TikFetch JVM process"),
            new MetricCardView("Processors", String.valueOf(os.getAvailableProcessors()), os.getArch()),
            new MetricCardView("Threads", String.valueOf(threads.getThreadCount()), "Peak " + threads.getPeakThreadCount() + ", virtual " + virtualThreads),
            new MetricCardView("Disk space available", bytesGauge("disk.free"), "Free storage visible to the app")
        );

        List<MetricCardView> trafficCards = List.of(
            new MetricCardView("HTTP requests", formatWhole(httpRequests()), "Observed by Micrometer"),
            new MetricCardView("Avg response time", timerMeanMillis("http.server.requests"), "Mean across observed requests"),
            new MetricCardView("HTTP 5xx errors", formatWhole(http5xxErrors()), "Server error responses"),
            new MetricCardView("Successful downloads", String.valueOf(successfulVideos), "Rows with SUCCESS status"),
            new MetricCardView("Download attempts", String.valueOf(attempts), "Success and failure records"),
            new MetricCardView("Loaded meters", String.valueOf(meterRegistry.getMeters().size()), "Actuator/Micrometer meter count")
        );

        List<MetricCardView> databaseCards = List.of(
            new MetricCardView("Active DB connections", formatWhole(gaugeValue("hikaricp.connections.active")), "Currently borrowed from pool"),
            new MetricCardView("Max DB pool size", formatWhole(gaugeValue("hikaricp.connections.max")), "Configured Hikari limit")
        );

        return new MetricsView(healthStatus(), runtimeCards, systemCards, trafficCards, databaseCards);
    }

    private String healthStatus() {
        try {
            videoRepository.countByStatus(DownloadStatus.SUCCESS);
            attemptRepository.count();
            return "UP";
        } catch (RuntimeException _) {
            return "DOWN";
        }
    }

    private long safeCountSuccessfulVideos() {
        try {
            return videoRepository.countByStatus(DownloadStatus.SUCCESS);
        } catch (RuntimeException _) {
            return 0;
        }
    }

    private long safeCountAttempts() {
        try {
            return attemptRepository.count();
        } catch (RuntimeException _) {
            return 0;
        }
    }

    private double httpRequests() {
        return meterRegistry.find("http.server.requests")
            .timers()
            .stream()
            .mapToDouble(Timer::count)
            .sum();
    }

    private double http5xxErrors() {
        return meterRegistry.find("http.server.requests")
            .timers()
            .stream()
            .filter(timer -> timer.getId().getTag("status") != null && timer.getId().getTag("status").startsWith("5"))
            .mapToDouble(Timer::count)
            .sum();
    }

    private double systemCpu() {
        return gaugeValue("system.cpu.usage");
    }

    private double processCpu() {
        return gaugeValue("process.cpu.usage");
    }

    private double gaugeValue(String name) {
        Gauge gauge = meterRegistry.find(name).gauge();

        if (gauge == null) {
            return -1;
        }

        double value = gauge.value();
        return Double.isNaN(value) ? -1 : value;
    }

    private String bytesGauge(String name) {
        double value = gaugeValue(name);

        if (value < 0) {
            return "Unknown";
        }

        return mapper.humanReadableByteCount((long) value);
    }

    private String timerMeanMillis(String name) {
        double total = 0;
        double count = 0;

        for (Timer timer : meterRegistry.find(name).timers()) {
            total += timer.totalTime(TimeUnit.MILLISECONDS);
            count += timer.count();
        }

        return count <= 0 ? "Unknown" : "%.1f ms".formatted(total / count);
    }

    private String timerMaxMillis(String name) {
        double max = meterRegistry.find(name)
            .timers()
            .stream()
            .mapToDouble(timer -> timer.max(TimeUnit.MILLISECONDS))
            .max()
            .orElse(-1);

        return max < 0 ? "Unknown" : "%.1f ms".formatted(max);
    }

    private String memoryPair(long used, long max) {
        if (max <= 0) {
            return mapper.humanReadableByteCount(used);
        }

        return mapper.humanReadableByteCount(used) + " / " + mapper.humanReadableByteCount(max);
    }

    private String percent(double value) {
        if (value < 0 || Double.isNaN(value)) {
            return "Unknown";
        }

        return "%.1f%%".formatted(value * 100);
    }

    private String formatWhole(double value) {
        if (value < 0 || Double.isNaN(value)) {
            return "Unknown";
        }

        return "%.0f".formatted(value);
    }

    private String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(millis);
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();

        if (days > 0) {
            return "%dd %dh %dm".formatted(days, hours, minutes);
        }

        return "%dh %dm".formatted(hours, minutes);
    }
}
