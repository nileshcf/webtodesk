package com.example.conversion_service.service;

import com.example.conversion_service.dto.*;
import com.example.conversion_service.dto.InitiateUpgradeResponse;
import com.example.conversion_service.entity.ConversionProject;
import com.example.conversion_service.entity.ConversionProject.LicenseTier;
import com.example.conversion_service.exception.LicenseViolationException;
import com.example.conversion_service.repository.ConversionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseService {

    private final ConversionRepository repository;

    // In-memory license cache: userEmail → LicenseInfoResponse (TTL 60 s)
    private final ConcurrentMap<String, CachedLicense> licenseCache = new ConcurrentHashMap<>();

    private static final long CACHE_TTL_SECONDS = 60;

    // ─── Public API ─────────────────────────────────────────────────────

    /**
     * Returns the aggregate license info for a user (derived from their projects).
     * Uses the "best" tier found across all user projects.
     */
    public LicenseInfoResponse getCurrentLicense(String userEmail) {
        CachedLicense cached = licenseCache.get(userEmail);
        if (cached != null && !cached.isExpired()) {
            return cached.info;
        }

        LicenseInfoResponse info = buildLicenseInfo(userEmail);
        licenseCache.put(userEmail, new CachedLicense(info));
        return info;
    }

    /** Full dashboard: license info + upgrade options + usage stats + expiry warning. */
    public LicenseDashboardResponse getDashboard(String userEmail) {
        LicenseInfoResponse info = getCurrentLicense(userEmail);
        List<UpgradeOptionResponse> upgradeOptions = getUpgradeOptions(info.tier());
        LicenseUsageStatsResponse stats = getUsageStats(userEmail);
        LicenseDashboardResponse.ExpiryWarning warning = buildExpiryWarning(info);
        return new LicenseDashboardResponse(info, upgradeOptions, stats, warning);
    }

    /** Validate whether a user can perform the given operation. */
    public LicenseValidationResponse validateLicense(String userEmail, String operation) {
        LicenseInfoResponse info = getCurrentLicense(userEmail);
        LicenseRestrictionsResponse restrictions = LicenseRestrictionsResponse.forTier(info.tier());

        boolean valid = true;
        boolean canBuild = true;
        String message = null;

        // Check expiry
        if (info.licenseExpiresAt() != null) {
            Instant expiry = Instant.parse(info.licenseExpiresAt());
            if (Instant.now().isAfter(expiry)) {
                valid = false;
                canBuild = false;
                message = "License expired on " + info.licenseExpiresAt();
            }
        }

        // Check build quota for build operations
        if (valid && "build".equals(operation)) {
            if (info.tier() != LicenseTier.LIFETIME && info.buildsUsed() >= info.buildsAllowed()) {
                canBuild = false;
                message = "Build quota reached: " + info.buildsAllowed() + " builds for " + info.tier() + " tier";
            }
        }

        return new LicenseValidationResponse(valid, info.tier(), restrictions, canBuild, message);
    }

    /** Enforce license rules before a build starts — throws LicenseViolationException on failure. */
    public void validateBuildRequest(ConversionProject project) {
        LicenseTier tier = project.getTier() != null ? project.getTier() : LicenseTier.TRIAL;
        Instant now = Instant.now();

        // Check expiry
        if (project.getLicenseExpiresAt() != null && now.isAfter(project.getLicenseExpiresAt())) {
            throw new LicenseViolationException("License expired on " + project.getLicenseExpiresAt());
        }

        // Check build quotas (skip for LIFETIME)
        if (tier != LicenseTier.LIFETIME) {
            int maxBuilds = project.getMaxBuilds() != null ? project.getMaxBuilds() : maxBuildsFor(tier);
            int buildCount = project.getBuildCount() != null ? project.getBuildCount() : 0;
            if (buildCount >= maxBuilds) {
                throw new LicenseViolationException(
                        tier + " limit reached: " + maxBuilds + " builds maximum. Upgrade to continue building.");
            }
        }

        log.debug("License validated for project {} (tier={}, builds={}/{})",
                project.getId(), tier, project.getBuildCount(), project.getMaxBuilds());
    }

    /** Returns upgrade options relative to current tier. */
    public List<UpgradeOptionResponse> getUpgradeOptions(LicenseTier currentTier) {
        return List.of(
                new UpgradeOptionResponse(LicenseTier.TRIAL, 0, "USD", "lifetime",
                        List.of("4 builds", "1 active app", "Basic build"), false, currentTier == LicenseTier.TRIAL),
                new UpgradeOptionResponse(LicenseTier.STARTER, 9.99, "USD", "monthly",
                        List.of("120 builds/mo", "5 active apps", "Basic modules", "Email support"),
                        false, currentTier == LicenseTier.STARTER),
                new UpgradeOptionResponse(LicenseTier.PRO, 29.99, "USD", "monthly",
                        List.of("3000 builds/mo", "25 active apps", "All modules", "Priority queue",
                                "Cross-platform builds", "Priority support"),
                        true, currentTier == LicenseTier.PRO),
                new UpgradeOptionResponse(LicenseTier.LIFETIME, 299, "USD", "lifetime",
                        List.of("Unlimited builds", "Unlimited apps", "All modules", "Priority queue",
                                "Cross-platform builds", "Dedicated support", "All future features"),
                        false, currentTier == LicenseTier.LIFETIME)
        );
    }

    /** Stub upgrade initiation — returns a placeholder checkout URL. */
    public InitiateUpgradeResponse initiateUpgrade(String userEmail, String tier, String billingCycle) {
        String sessionId = UUID.randomUUID().toString();
        String upgradeUrl = "https://buy.stripe.com/webtodesk/" + tier.toLowerCase() + "?session=" + sessionId;
        log.info("Upgrade initiated for {} → tier={} cycle={} session={}", userEmail, tier, billingCycle, sessionId);
        return new InitiateUpgradeResponse(upgradeUrl, sessionId);
    }

    /** Stub upgrade completion — in production this would verify payment and update the DB. */
    public LicenseInfoResponse completeUpgrade(String userEmail, String sessionId) {
        log.info("Upgrade complete for {} session={}", userEmail, sessionId);
        // Re-fetch current license (production: update tier in DB first)
        evictCache(userEmail);
        return getCurrentLicense(userEmail);
    }

    /** Usage stats for a user's projects. */
    public LicenseUsageStatsResponse getUsageStats(String userEmail) {
        List<ConversionProject> projects = repository.findByCreatedByOrderByCreatedAtDesc(userEmail);
        int active = (int) projects.stream()
                .filter(p -> p.getStatus() == ConversionProject.ConversionStatus.READY
                        || p.getStatus() == ConversionProject.ConversionStatus.BUILDING).count();
        int totalBuilds = projects.stream()
                .mapToInt(p -> p.getBuildCount() != null ? p.getBuildCount() : 0).sum();
        return new LicenseUsageStatsResponse(totalBuilds, 0, 0, 0, active);
    }

    /** Check if a feature string is in the allowed list for the user's tier. */
    public boolean isFeatureAvailable(String userEmail, String featureId) {
        LicenseInfoResponse info = getCurrentLicense(userEmail);
        LicenseRestrictionsResponse restrictions = LicenseRestrictionsResponse.forTier(info.tier());
        return restrictions.allowedFeatures().contains(featureId);
    }

    /** Returns the best tier found across all user projects (TRIAL if none exist). */
    public LicenseTier getUserBestTier(String userEmail) {
        return getCurrentLicense(userEmail).tier();
    }

    /** Force-refresh the cache for this user. */
    public LicenseInfoResponse refreshLicense(String userEmail) {
        evictCache(userEmail);
        return getCurrentLicense(userEmail);
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private LicenseInfoResponse buildLicenseInfo(String userEmail) {
        List<ConversionProject> projects = repository.findByCreatedByOrderByCreatedAtDesc(userEmail);

        // Derive "best" tier from user's projects
        LicenseTier bestTier = projects.stream()
                .map(p -> p.getTier() != null ? p.getTier() : LicenseTier.TRIAL)
                .max(LicenseService::compareTiers)
                .orElse(LicenseTier.TRIAL);

        int totalBuilds = projects.stream()
                .mapToInt(p -> p.getBuildCount() != null ? p.getBuildCount() : 0).sum();
        int maxBuilds = maxBuildsFor(bestTier);
        int activeApps = (int) projects.stream()
                .filter(p -> p.getStatus() == ConversionProject.ConversionStatus.READY).count();

        Instant licenseExpiresAt = projects.stream()
                .filter(p -> p.getLicenseExpiresAt() != null)
                .map(ConversionProject::getLicenseExpiresAt)
                .max(Instant::compareTo)
                .orElse(defaultExpiry(bestTier));

        String licenseId = projects.stream()
                .filter(p -> p.getLicenseId() != null)
                .map(ConversionProject::getLicenseId)
                .findFirst()
                .orElse(UUID.randomUUID().toString());

        return LicenseInfoResponse.of(licenseId, bestTier, licenseExpiresAt,
                totalBuilds, maxBuilds, activeApps, Instant.now());
    }

    private LicenseDashboardResponse.ExpiryWarning buildExpiryWarning(LicenseInfoResponse info) {
        if (info.licenseExpiresAt() == null || info.tier() == LicenseTier.LIFETIME) return null;
        long days = ChronoUnit.DAYS.between(Instant.now(), Instant.parse(info.licenseExpiresAt()));
        if (days > 14) return null;
        String level = days <= 3 ? "critical" : days <= 7 ? "warning" : "info";
        return new LicenseDashboardResponse.ExpiryWarning((int) days, level);
    }

    private static int compareTiers(LicenseTier a, LicenseTier b) {
        return tierRank(a) - tierRank(b);
    }

    private static int tierRank(LicenseTier tier) {
        return switch (tier) {
            case TRIAL -> 0;
            case STARTER -> 1;
            case PRO -> 2;
            case LIFETIME -> 3;
        };
    }

    public static int maxBuildsFor(LicenseTier tier) {
        return switch (tier) {
            case TRIAL -> 4;
            case STARTER -> 120;
            case PRO -> 3000;
            case LIFETIME -> Integer.MAX_VALUE;
        };
    }

    private Instant defaultExpiry(LicenseTier tier) {
        return switch (tier) {
            case LIFETIME -> null;
            case TRIAL -> Instant.now().plus(30, ChronoUnit.DAYS);
            case STARTER, PRO -> Instant.now().plus(30, ChronoUnit.DAYS);
        };
    }

    private void evictCache(String userEmail) {
        licenseCache.remove(userEmail);
    }

    // ─── Cache record ────────────────────────────────────────────────────

    private record CachedLicense(LicenseInfoResponse info, Instant cachedAt) {
        CachedLicense(LicenseInfoResponse info) {
            this(info, Instant.now());
        }

        boolean isExpired() {
            return Instant.now().isAfter(cachedAt.plusSeconds(CACHE_TTL_SECONDS));
        }
    }
}
