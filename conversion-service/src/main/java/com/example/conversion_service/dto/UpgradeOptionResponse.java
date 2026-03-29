package com.example.conversion_service.dto;

import com.example.conversion_service.entity.ConversionProject.LicenseTier;

import java.util.List;

public record UpgradeOptionResponse(
        LicenseTier tier,
        double price,
        String currency,
        String billingCycle,
        List<String> features,
        boolean popular,
        boolean current
) {}
