package com.example.conversion_service.dto;

import com.example.conversion_service.entity.ConversionProject.LicenseTier;

/**
 * Aggregate stats for a user's projects — returned by GET /conversions/stats.
 */
public record ConversionStatsResponse(
        String userEmail,
        int totalProjects,
        int draftProjects,
        int readyProjects,
        int buildingProjects,
        int failedProjects,
        int totalBuilds,
        int buildsAllowed,
        int buildsRemaining,
        LicenseTier tier,
        String licenseExpiresAt
) {}
