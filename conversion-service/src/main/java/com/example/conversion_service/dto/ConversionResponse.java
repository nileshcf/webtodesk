package com.example.conversion_service.dto;

import com.example.conversion_service.entity.ConversionProject;

import java.time.Instant;

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
        Instant updatedAt
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
                project.getUpdatedAt()
        );
    }
}
