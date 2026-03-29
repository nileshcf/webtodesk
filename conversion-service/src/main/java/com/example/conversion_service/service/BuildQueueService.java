package com.example.conversion_service.service;

import com.example.conversion_service.entity.ConversionProject.LicenseTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks active builds and exposes queue-status metrics.
 * Actual async dispatch still uses BuildService's @Async("buildExecutor").
 * Week 3 will add a separate priority executor pool.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuildQueueService {

    private final AtomicInteger normalQueueDepth = new AtomicInteger(0);
    private final AtomicInteger priorityQueueDepth = new AtomicInteger(0);

    // projectId → startEpochMs
    private final Map<String, Long> activeBuilds = new ConcurrentHashMap<>();

    // ─── Queue tracking ─────────────────────────────────────────────────

    public void recordBuildStarted(String projectId, LicenseTier tier) {
        activeBuilds.put(projectId, System.currentTimeMillis());
        if (isPriorityTier(tier)) {
            priorityQueueDepth.incrementAndGet();
        } else {
            normalQueueDepth.incrementAndGet();
        }
        log.debug("Build started: project={} tier={} active={}", projectId, tier, activeBuilds.size());
    }

    public void recordBuildFinished(String projectId, LicenseTier tier) {
        activeBuilds.remove(projectId);
        if (isPriorityTier(tier)) {
            priorityQueueDepth.decrementAndGet();
        } else {
            normalQueueDepth.decrementAndGet();
        }
        log.debug("Build finished: project={} active={}", projectId, activeBuilds.size());
    }

    public boolean isAlreadyBuilding(String projectId) {
        return activeBuilds.containsKey(projectId);
    }

    // ─── Queue status ────────────────────────────────────────────────────

    public QueueStatus getQueueStatus() {
        int normalActive = Math.max(0, normalQueueDepth.get());
        int priorityActive = Math.max(0, priorityQueueDepth.get());

        // Rough estimate: 10 min per build in normal queue, 5 min in priority
        double avgWaitNormal = normalActive * 10.0;
        double avgWaitPriority = priorityActive * 5.0;

        return new QueueStatus(normalActive, priorityActive, avgWaitNormal, avgWaitPriority);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private boolean isPriorityTier(LicenseTier tier) {
        return tier == LicenseTier.PRO || tier == LicenseTier.LIFETIME;
    }

    public record QueueStatus(
            int normalQueueLength,
            int priorityQueueLength,
            double averageWaitTimeNormal,
            double averageWaitTimePriority
    ) {}
}
