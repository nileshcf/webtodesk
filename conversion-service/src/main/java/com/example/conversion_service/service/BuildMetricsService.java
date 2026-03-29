package com.example.conversion_service.service;

import com.example.conversion_service.dto.BuildRecordResponse;
import com.example.conversion_service.entity.BuildRecord;
import com.example.conversion_service.repository.BuildRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Records build events and provides build history / aggregate metrics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuildMetricsService {

    private final BuildRecordRepository buildRecordRepository;

    /** Persist a BuildRecord (call this at the end of every triggerBuild() invocation). */
    public BuildRecord save(BuildRecord record) {
        BuildRecord saved = buildRecordRepository.save(record);
        log.debug("Saved BuildRecord id={} project={} result={}", saved.getId(), saved.getProjectId(), saved.getResult());
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
}
