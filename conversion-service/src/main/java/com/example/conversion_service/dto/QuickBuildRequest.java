package com.example.conversion_service.dto;

import java.util.List;

public record QuickBuildRequest(
        String projectName,
        String websiteUrl,
        String appTitle,
        String iconFile,
        List<String> modules,
        String platform,
        String userEmail
) {
    public String resolvedProjectName() {
        return projectName != null && !projectName.isBlank() ? projectName : "test-build";
    }

    public String resolvedAppTitle() {
        return appTitle != null && !appTitle.isBlank() ? appTitle : resolvedProjectName();
    }

    public String resolvedUserEmail() {
        return userEmail != null && !userEmail.isBlank() ? userEmail : "dev@webtodesk.local";
    }
}
