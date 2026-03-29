package com.example.conversion_service.dto;

import com.example.conversion_service.entity.ConversionProject.LicenseTier;

import java.time.Instant;
import java.util.List;

public record LicenseInfoResponse(
        String licenseId,
        LicenseTier tier,
        String licenseExpiresAt,
        int buildsUsed,
        int buildsAllowed,
        int activeApps,
        int maxActiveApps,
        String issuedAt,
        String lastValidatedAt,
        List<Object> migrationHistory
) {
    public static LicenseInfoResponse of(
            String licenseId,
            LicenseTier tier,
            Instant licenseExpiresAt,
            int buildCount,
            int maxBuilds,
            int activeApps,
            Instant issuedAt
    ) {
        return new LicenseInfoResponse(
                licenseId,
                tier,
                licenseExpiresAt != null ? licenseExpiresAt.toString() : null,
                buildCount,
                maxBuilds,
                activeApps,
                maxActiveAppsFor(tier),
                issuedAt != null ? issuedAt.toString() : null,
                Instant.now().toString(),
                List.of()
        );
    }

    private static int maxActiveAppsFor(LicenseTier tier) {
        return switch (tier) {
            case TRIAL -> 1;
            case STARTER -> 5;
            case PRO -> 25;
            case LIFETIME -> Integer.MAX_VALUE;
        };
    }
}
