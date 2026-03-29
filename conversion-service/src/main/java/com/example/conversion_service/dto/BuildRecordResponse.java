package com.example.conversion_service.dto;

import com.example.conversion_service.entity.BuildRecord;
import com.example.conversion_service.entity.ConversionProject.LicenseTier;

import java.time.Instant;
import java.util.List;

public record BuildRecordResponse(
        String id,
        String projectId,
        String projectName,
        String userEmail,
        LicenseTier tier,
        String result,
        String buildError,
        String artifactUrl,
        String buildTarget,
        List<String> enabledModules,
        Instant startedAt,
        Instant completedAt,
        long durationMs
) {
    public static BuildRecordResponse from(BuildRecord r) {
        return new BuildRecordResponse(
                r.getId(),
                r.getProjectId(),
                r.getProjectName(),
                r.getUserEmail(),
                r.getTier(),
                r.getResult(),
                r.getBuildError(),
                r.getArtifactUrl(),
                r.getBuildTarget(),
                r.getEnabledModules(),
                r.getStartedAt(),
                r.getCompletedAt(),
                r.getDurationMs()
        );
    }
}
