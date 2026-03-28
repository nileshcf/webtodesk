package com.example.conversion_service.dto;

import com.example.conversion_service.entity.ConversionProject.LicenseTier;
import com.example.conversion_service.service.ModuleRegistry.ModuleDefinition;

public record ModuleInfoResponse(
        String key,
        String name,
        String description,
        LicenseTier requiredTier,
        boolean available
) {
    public static ModuleInfoResponse from(ModuleDefinition def, boolean available) {
        return new ModuleInfoResponse(def.key(), def.name(), def.description(), def.requiredTier(), available);
    }
}
