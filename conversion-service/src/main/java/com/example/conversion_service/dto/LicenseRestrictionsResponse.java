package com.example.conversion_service.dto;

import com.example.conversion_service.entity.ConversionProject.LicenseTier;

import java.util.List;

public record LicenseRestrictionsResponse(
        int maxBuilds,
        int maxActiveApps,
        List<String> allowedFeatures,
        List<String> blockedFeatures,
        boolean priorityQueue,
        boolean crossPlatformBuilds
) {
    public static LicenseRestrictionsResponse forTier(LicenseTier tier) {
        return switch (tier) {
            case TRIAL -> new LicenseRestrictionsResponse(
                    4, 1,
                    List.of("basic-build", "offline"),
                    List.of("screen-protect", "biometric", "deep-link", "badge", "priority-queue", "cross-platform"),
                    false, false
            );
            case STARTER -> new LicenseRestrictionsResponse(
                    120, 5,
                    List.of("basic-build", "offline", "badge"),
                    List.of("screen-protect", "biometric", "deep-link", "priority-queue", "cross-platform"),
                    false, false
            );
            case PRO -> new LicenseRestrictionsResponse(
                    3000, 25,
                    List.of("basic-build", "offline", "badge", "screen-protect", "biometric", "deep-link"),
                    List.of(),
                    true, true
            );
            case LIFETIME -> new LicenseRestrictionsResponse(
                    Integer.MAX_VALUE, Integer.MAX_VALUE,
                    List.of("basic-build", "offline", "badge", "screen-protect", "biometric", "deep-link"),
                    List.of(),
                    true, true
            );
        };
    }
}
