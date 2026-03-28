package com.example.conversion_service.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteUpgradeRequest(
        @NotBlank String sessionId
) {}
