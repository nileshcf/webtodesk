package com.example.conversion_service.dto;

import java.util.Map;

/**
 * Contains the generated Electron project files as a map of filename → content.
 */
public record ElectronConfigResponse(
        String projectName,
        String appTitle,
        String websiteUrl,
        Map<String, String> files
) {}
