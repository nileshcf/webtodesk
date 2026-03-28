package com.example.conversion_service.dto;

import com.example.conversion_service.entity.ConversionProject;

import java.time.Instant;
import java.util.List;

public record ConversionResponse(
        String id,
        String projectName,
        String websiteUrl,
        String appTitle,
        String iconFile,
        String currentVersion,
        ConversionProject.ConversionStatus status,
        String createdBy,
        String buildError,
        boolean downloadAvailable,
        String downloadUrl,
        String buildProgress,
        Instant createdAt,
        Instant updatedAt,
        List<String> enabledModules,
        String targetPlatform
) {
    public static ConversionResponse from(ConversionProject project) {
        boolean isReady = project.getBuildArtifactPath() != null
                && project.getStatus() == ConversionProject.ConversionStatus.READY;
        return new ConversionResponse(
                project.getId(),
                project.getProjectName(),
                project.getWebsiteUrl(),
                project.getAppTitle(),
                project.getIconFile(),
                project.getCurrentVersion(),
                project.getStatus(),
                project.getCreatedBy(),
                project.getBuildError(),
                isReady,
                isReady ? project.getBuildArtifactPath() : null,
                project.getBuildProgress(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getEnabledModules(),
                project.getTargetPlatform()
        );
    }
}
