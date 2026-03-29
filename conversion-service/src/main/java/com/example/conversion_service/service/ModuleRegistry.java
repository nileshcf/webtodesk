package com.example.conversion_service.service;

import com.example.conversion_service.entity.ConversionProject.LicenseTier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry of all available Electron modules with tier-based availability.
 * Each module has a Mustache template at templates/electron/modules/{key}.mustache.
 */
@Slf4j
@Component
public class ModuleRegistry {

    public record ModuleDefinition(
            String key,
            String name,
            String description,
            LicenseTier requiredTier,
            String templateFile
    ) {}

    private static final Map<String, ModuleDefinition> REGISTRY;

    static {
        Map<String, ModuleDefinition> m = new LinkedHashMap<>();
        m.put("splash-screen", new ModuleDefinition(
                "splash-screen", "Splash Screen",
                "Branded loading screen while the main URL loads",
                LicenseTier.TRIAL, "modules/splash-screen.mustache"));
        m.put("offline", new ModuleDefinition(
                "offline", "Offline Detection",
                "Shows a friendly error page when the network connection is lost",
                LicenseTier.TRIAL, "modules/offline.mustache"));
        m.put("badge", new ModuleDefinition(
                "badge", "Badge Count",
                "Set dock/taskbar badge counter via IPC from the renderer",
                LicenseTier.TRIAL, "modules/badge.mustache"));
        m.put("domain-lock", new ModuleDefinition(
                "domain-lock", "Domain Lock",
                "Restrict navigation to allowed domains and block specified destinations",
                LicenseTier.TRIAL, "modules/domain-lock.mustache"));
        m.put("title-bar", new ModuleDefinition(
                "title-bar", "Title Bar",
                "Set a custom window title that persists across page navigations",
                LicenseTier.TRIAL, "modules/title-bar.mustache"));
        m.put("watermark", new ModuleDefinition(
                "watermark", "Watermark Badge",
                "Persistent badge near window controls showing trial status and days remaining",
                LicenseTier.TRIAL, "modules/watermark.mustache"));
        m.put("expiry", new ModuleDefinition(
                "expiry", "Trial Expiry",
                "Locks the app with a full-screen overlay after a specified expiry timestamp",
                LicenseTier.TRIAL, "modules/expiry.mustache"));
        m.put("screen-protect", new ModuleDefinition(
                "screen-protect", "Screen Protection",
                "OS-level content protection to prevent screenshots and recordings",
                LicenseTier.PRO, "modules/screen-protect.mustache"));
        m.put("deep-link", new ModuleDefinition(
                "deep-link", "Deep Link",
                "Register a custom URL protocol so the app can be launched via myapp:// links",
                LicenseTier.PRO, "modules/deep-link.mustache"));
        REGISTRY = Collections.unmodifiableMap(m);
    }

    /** All defined modules (regardless of tier). */
    public List<ModuleDefinition> getAllModules() {
        return new ArrayList<>(REGISTRY.values());
    }

    /** Modules available for a given license tier. */
    public List<ModuleDefinition> getAvailableModules(LicenseTier tier) {
        return REGISTRY.values().stream()
                .filter(def -> tierRank(tier) >= tierRank(def.requiredTier()))
                .collect(Collectors.toList());
    }

    /** Returns true if the given module key is accessible for the given tier. */
    public boolean isAvailable(String moduleKey, LicenseTier tier) {
        ModuleDefinition def = REGISTRY.get(moduleKey);
        if (def == null) return false;
        return tierRank(tier) >= tierRank(def.requiredTier());
    }

    /**
     * Validates a list of requested module keys against the user's tier.
     * Returns only the keys that are valid and accessible.
     * Logs a warning for each key that is unknown or tier-locked.
     */
    public List<String> resolveEnabledModules(List<String> requested, LicenseTier tier) {
        return resolveEnabledModules(requested, tier, false);
    }

    /**
     * Dev-mode overload — when {@code devMode} is true, tier gating is skipped entirely
     * so all known module keys are resolved regardless of tier.
     */
    public List<String> resolveEnabledModules(List<String> requested, LicenseTier tier, boolean devMode) {
        if (requested == null || requested.isEmpty()) return Collections.emptyList();
        List<String> resolved = new ArrayList<>();
        for (String key : requested) {
            if (!REGISTRY.containsKey(key)) {
                log.warn("Unknown module key '{}' — skipping", key);
                continue;
            }
            if (!devMode && !isAvailable(key, tier)) {
                log.warn("Module '{}' requires tier {} but project is on {} — skipping",
                        key, REGISTRY.get(key).requiredTier(), tier);
                continue;
            }
            resolved.add(key);
        }
        return resolved;
    }

    /** Looks up a module definition by key. */
    public Optional<ModuleDefinition> get(String moduleKey) {
        return Optional.ofNullable(REGISTRY.get(moduleKey));
    }

    private static int tierRank(LicenseTier tier) {
        return switch (tier) {
            case TRIAL -> 0;
            case STARTER -> 1;
            case PRO -> 2;
            case LIFETIME -> 3;
        };
    }
}
