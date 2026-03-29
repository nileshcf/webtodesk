package com.example.conversion_service.dto;

import jakarta.validation.constraints.NotBlank;

public record InitiateUpgradeRequest(
        @NotBlank String tier,
        @NotBlank String billingCycle
) {}
