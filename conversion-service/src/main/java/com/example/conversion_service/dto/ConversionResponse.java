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
        Instant createdAt,
        Instant updatedAt
) {
    public static ConversionResponse from(ConversionProject project) {
        return new ConversionResponse(
                project.getId(),
                project.getProjectName(),
                project.getWebsiteUrl(),
                project.getAppTitle(),
                project.getIconFile(),
                project.getCurrentVersion(),
                project.getStatus(),
                project.getCreatedBy(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
