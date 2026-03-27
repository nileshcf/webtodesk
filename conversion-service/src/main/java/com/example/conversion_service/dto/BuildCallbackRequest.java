package com.example.conversion_service.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload sent by GitHub Actions workflow on build completion.
 */
public record BuildCallbackRequest(
        @NotBlank String projectId,
        Long runId,
        boolean success,
        String artifactUrl,  // optional: direct URL (if workflow provides it)
        String errorMessage  // null if success=true
) {}
