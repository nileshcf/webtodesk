package com.example.conversion_service.service;

import com.example.conversion_service.dto.BuildRecordResponse;
import com.example.conversion_service.entity.BuildRecord;
import com.example.conversion_service.entity.ConversionProject.LicenseTier;
import com.example.conversion_service.repository.BuildRecordRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Records build events, provides build history / aggregate metrics,
 * and publishes Micrometer / Prometheus counters, timers, and gauges.
 *
 * Metrics exposed:
 *   builds.started{tier, os}         — counter, incremented when a build begins
 *   builds.completed{tier, os, status} — counter, incremented when a build finishes
 *   builds.duration{tier, os}         — timer (histogram), measures wall-clock build time
 *   build.queue.depth{queue=normal}   — gauge, current normal-priority build count
 *   build.queue.depth{queue=priority} — gauge, current priority-tier build count
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuildMetricsService {

    private final BuildRecordRepository buildRecordRepository;
    private final BuildQueueService     buildQueueService;
    private final MeterRegistry         meterRegistry;

    @PostConstruct
    void registerQueueGauges() {
        Gauge.builder("build.queue.depth",
                      buildQueueService,
                      svc -> svc.getQueueStatus().normalQueueLength())
             .tag("queue", "normal")
             .description("Number of currently active normal-priority builds")
             .register(meterRegistry);

        Gauge.builder("build.queue.depth",
                      buildQueueService,
                      svc -> svc.getQueueStatus().priorityQueueLength())
             .tag("queue", "priority")
             .description("Number of currently active priority-tier builds")
             .register(meterRegistry);
    }

    /**
     * Call once per build attempt, immediately before the build work begins.
     * Increments the {@code builds.started} counter.
     */
    public void recordBuildStarted(LicenseTier tier, String os) {
        Counter.builder("builds.started")
               .tag("tier", tierTag(tier))
               .tag("os",   osTag(os))
               .description("Total number of build attempts started")
               .register(meterRegistry)
               .increment();
    }

    /** Persist a BuildRecord and emit Micrometer counter + timer. */
    public BuildRecord save(BuildRecord record) {
        BuildRecord saved = buildRecordRepository.save(record);
        log.debug("Saved BuildRecord id={} project={} result={}", saved.getId(), saved.getProjectId(), saved.getResult());

        String tier   = tierTag(saved.getTier());
        String os     = osTag(saved.getBuildTarget());
        String status = saved.getResult() != null ? saved.getResult() : "UNKNOWN";

        Counter.builder("builds.completed")
               .tag("tier",   tier)
               .tag("os",     os)
               .tag("status", status)
               .description("Total number of completed build attempts")
               .register(meterRegistry)
               .increment();

        if (saved.getDurationMs() > 0) {
            Timer.builder("builds.duration")
                 .tag("tier", tier)
                 .tag("os",   os)
                 .description("Wall-clock duration of build attempts")
                 .register(meterRegistry)
                 .record(saved.getDurationMs(), TimeUnit.MILLISECONDS);
        }

        return saved;
    }

    /** Returns the last {@code limit} build records for a project, newest first. */
    public List<BuildRecordResponse> getBuildHistory(String projectId, int limit) {
        return buildRecordRepository.findByProjectIdOrderByStartedAtDesc(projectId)
                .stream()
                .limit(limit)
                .map(BuildRecordResponse::from)
                .collect(Collectors.toList());
    }

    /** Returns the last {@code limit} build records across all projects for a user. */
    public List<BuildRecordResponse> getUserBuildHistory(String userEmail, int limit) {
        return buildRecordRepository.findByUserEmailOrderByStartedAtDesc(userEmail)
                .stream()
                .limit(limit)
                .map(BuildRecordResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Aggregate metrics for a project:
     * - total, successful, failed builds
     * - average duration for successful builds (ms)
     */
    public Map<String, Object> getProjectMetrics(String projectId) {
        List<BuildRecord> records = buildRecordRepository.findByProjectIdOrderByStartedAtDesc(projectId);

        long total = records.size();
        long success = records.stream().filter(r -> "READY".equals(r.getResult())).count();
        long failed = records.stream().filter(r -> "FAILED".equals(r.getResult())).count();
        double avgDuration = records.stream()
                .filter(r -> "READY".equals(r.getResult()) && r.getDurationMs() > 0)
                .mapToLong(BuildRecord::getDurationMs)
                .average()
                .orElse(0.0);

        return Map.of(
                "totalBuilds", total,
                "successfulBuilds", success,
                "failedBuilds", failed,
                "avgDurationMs", (long) avgDuration,
                "successRate", total > 0 ? Math.round((success * 100.0) / total) : 0
        );
    }

    // ── Tag helpers ───────────────────────────────────────────────────────────

    private static String tierTag(LicenseTier tier) {
        return tier != null ? tier.name() : "UNKNOWN";
    }

    private static String osTag(String buildTarget) {
        if (buildTarget == null) return "unknown";
        return switch (buildTarget.toLowerCase()) {
            case "win"   -> "win";
            case "linux" -> "linux";
            case "mac"   -> "mac";
            default      -> buildTarget.toLowerCase();
        };
    }
}
