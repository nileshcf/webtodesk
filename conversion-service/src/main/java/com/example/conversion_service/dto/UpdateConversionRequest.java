package com.example.conversion_service.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.List;

public record UpdateConversionRequest(
        @Size(max = 64, message = "Project name must be at most 64 characters")
        @Pattern(regexp = "^[a-zA-Z0-9 _-]+$", message = "Project name can only contain letters, numbers, spaces, hyphens, and underscores")
        String projectName,

        @URL(message = "Must be a valid URL")
        @Pattern(regexp = "^https://.*", message = "Website URL must start with https://")
        String websiteUrl,

        @Size(max = 128, message = "App title must be at most 128 characters")
        String appTitle,

        String iconFile,

        @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "Version must be in semver format (e.g. 1.0.0)")
        String currentVersion,

        List<String> enabledModules,

        String targetPlatform
) {}
