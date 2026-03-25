package com.example.conversion_service.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record CreateConversionRequest(
        @NotBlank(message = "Project name is required")
        String projectName,

        @NotBlank(message = "Website URL is required")
        @URL(message = "Must be a valid URL")
        String websiteUrl,

        @NotBlank(message = "App title is required")
        String appTitle,

        String iconFile
) {}
