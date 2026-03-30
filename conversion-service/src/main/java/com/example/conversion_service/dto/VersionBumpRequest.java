package com.example.conversion_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for POST /conversions/{id}/version/bump.
 * type must be one of: major | minor | patch
 */
public record VersionBumpRequest(
        @NotNull(message = "type is required")
        @Pattern(regexp = "major|minor|patch", message = "type must be major, minor, or patch")
        String type
) {}
