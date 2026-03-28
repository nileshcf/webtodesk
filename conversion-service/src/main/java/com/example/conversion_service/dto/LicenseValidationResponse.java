package com.example.conversion_service.dto;

import com.example.conversion_service.entity.ConversionProject.LicenseTier;

public record LicenseValidationResponse(
        boolean valid,
        LicenseTier tier,
        LicenseRestrictionsResponse restrictions,
        boolean canBuild,
        String message
) {}
