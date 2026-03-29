package com.example.conversion_service.dto;

import java.util.List;

public record LicenseDashboardResponse(
        LicenseInfoResponse currentLicense,
        List<UpgradeOptionResponse> upgradeOptions,
        LicenseUsageStatsResponse usageStats,
        ExpiryWarning expiryWarning
) {
    public record ExpiryWarning(int daysRemaining, String warningLevel) {}
}
