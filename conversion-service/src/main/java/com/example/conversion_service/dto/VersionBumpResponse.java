package com.example.conversion_service.dto;

/**
 * Response for POST /conversions/{id}/version/bump.
 */
public record VersionBumpResponse(
        String projectId,
        String previousVersion,
        String newVersion,
        String bumpType,
        ConversionResponse project
) {}
