package com.example.conversion_service.dto;

public record UpdateConversionRequest(
        String projectName,
        String websiteUrl,
        String appTitle,
        String iconFile,
        String currentVersion
) {}
