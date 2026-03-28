package com.example.conversion_service.dto;

public record LicenseUsageStatsResponse(
        int buildsThisMonth,
        double avgBuildTime,
        double successRate,
        double queueWaitTime,
        int activeProjects
) {}
