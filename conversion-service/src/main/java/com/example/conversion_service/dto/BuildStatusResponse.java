package com.example.conversion_service.dto;

import com.example.conversion_service.entity.ConversionProject;
import com.example.conversion_service.entity.ConversionProject.ConversionStatus;

import java.time.Instant;

public record BuildStatusResponse(
        String projectId,
        String projectName,
        ConversionStatus status,
        String buildError,
        boolean downloadAvailable,
        String downloadUrl,
        String buildProgress,
        Instant updatedAt
) {
    public static BuildStatusResponse from(ConversionProject project) {
        boolean isReady = project.getBuildArtifactPath() != null && project.getStatus() == ConversionStatus.READY;
        return new BuildStatusResponse(
                project.getId(),
                project.getProjectName(),
                project.getStatus(),
                project.getBuildError(),
                isReady,
                isReady ? project.getBuildArtifactPath() : null,
                project.getBuildProgress(),
                project.getUpdatedAt()
        );
    }
}
