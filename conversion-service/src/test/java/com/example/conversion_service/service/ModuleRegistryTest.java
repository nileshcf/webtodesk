package com.example.conversion_service.service;

import com.example.conversion_service.entity.ConversionProject.LicenseTier;
import com.example.conversion_service.service.ModuleRegistry.ModuleDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ModuleRegistry — no Spring context needed.
 */
class ModuleRegistryTest {

    private ModuleRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ModuleRegistry();
    }

    // ─── getAllModules ────────────────────────────────────

    @Test
    void getAllModules_returnsSeventeen() {
        List<ModuleDefinition> all = registry.getAllModules();
        assertThat(all).hasSize(17);
    }

    @Test
    void getAllModules_containsExpectedKeys() {
        List<String> keys = registry.getAllModules().stream()
                .map(ModuleDefinition::key).toList();
        assertThat(keys).containsExactlyInAnyOrder(
                "splash-screen", "offline", "badge", "domain-lock", "title-bar", "watermark", "expiry",
                "notifications", "system-tray", "dark-mode", "right-click", "auto-update",
                "key-bindings", "window-polish", "clipboard",
                "screen-protect", "deep-link");
    }

    // ─── getAvailableModules by tier ─────────────────────

    @Test
    void getAvailableModules_trial_returnsSevenModules() {
        List<ModuleDefinition> modules = registry.getAvailableModules(LicenseTier.TRIAL);
        assertThat(modules).hasSize(7);
        List<String> keys = modules.stream().map(ModuleDefinition::key).toList();
        assertThat(keys).containsExactlyInAnyOrder("splash-screen", "offline", "badge", "domain-lock", "title-bar", "watermark", "expiry");
    }

    @Test
    void getAvailableModules_starter_returnsFifteenModules() {
        List<ModuleDefinition> modules = registry.getAvailableModules(LicenseTier.STARTER);
        assertThat(modules).hasSize(15);
    }

    @Test
    void getAvailableModules_pro_returnsAllSeventeen() {
        List<ModuleDefinition> modules = registry.getAvailableModules(LicenseTier.PRO);
        assertThat(modules).hasSize(17);
    }

    @Test
    void getAvailableModules_lifetime_returnsAllSeventeen() {
        List<ModuleDefinition> modules = registry.getAvailableModules(LicenseTier.LIFETIME);
        assertThat(modules).hasSize(17);
    }

    // ─── isAvailable ─────────────────────────────────────

    @Test
    void isAvailable_trialModuleOnTrial_returnsTrue() {
        assertThat(registry.isAvailable("offline", LicenseTier.TRIAL)).isTrue();
        assertThat(registry.isAvailable("badge", LicenseTier.TRIAL)).isTrue();
        assertThat(registry.isAvailable("splash-screen", LicenseTier.TRIAL)).isTrue();
        assertThat(registry.isAvailable("domain-lock", LicenseTier.TRIAL)).isTrue();
        assertThat(registry.isAvailable("title-bar", LicenseTier.TRIAL)).isTrue();
        assertThat(registry.isAvailable("watermark", LicenseTier.TRIAL)).isTrue();
        assertThat(registry.isAvailable("expiry", LicenseTier.TRIAL)).isTrue();
    }

    @Test
    void isAvailable_proModuleOnTrial_returnsFalse() {
        assertThat(registry.isAvailable("screen-protect", LicenseTier.TRIAL)).isFalse();
        assertThat(registry.isAvailable("deep-link", LicenseTier.TRIAL)).isFalse();
    }

    @Test
    void isAvailable_starterModulesOnTrial_returnsFalse() {
        assertThat(registry.isAvailable("notifications", LicenseTier.TRIAL)).isFalse();
        assertThat(registry.isAvailable("system-tray",   LicenseTier.TRIAL)).isFalse();
        assertThat(registry.isAvailable("dark-mode",     LicenseTier.TRIAL)).isFalse();
        assertThat(registry.isAvailable("right-click",   LicenseTier.TRIAL)).isFalse();
        assertThat(registry.isAvailable("auto-update",   LicenseTier.TRIAL)).isFalse();
        assertThat(registry.isAvailable("key-bindings",  LicenseTier.TRIAL)).isFalse();
        assertThat(registry.isAvailable("window-polish", LicenseTier.TRIAL)).isFalse();
        assertThat(registry.isAvailable("clipboard",     LicenseTier.TRIAL)).isFalse();
    }

    @Test
    void isAvailable_starterModulesOnStarter_returnsTrue() {
        assertThat(registry.isAvailable("notifications", LicenseTier.STARTER)).isTrue();
        assertThat(registry.isAvailable("system-tray",   LicenseTier.STARTER)).isTrue();
        assertThat(registry.isAvailable("dark-mode",     LicenseTier.STARTER)).isTrue();
        assertThat(registry.isAvailable("right-click",   LicenseTier.STARTER)).isTrue();
        assertThat(registry.isAvailable("auto-update",   LicenseTier.STARTER)).isTrue();
        assertThat(registry.isAvailable("key-bindings",  LicenseTier.STARTER)).isTrue();
        assertThat(registry.isAvailable("window-polish", LicenseTier.STARTER)).isTrue();
        assertThat(registry.isAvailable("clipboard",     LicenseTier.STARTER)).isTrue();
    }

    @Test
    void isAvailable_proModulesOnStarter_returnsFalse() {
        assertThat(registry.isAvailable("screen-protect", LicenseTier.STARTER)).isFalse();
        assertThat(registry.isAvailable("deep-link",      LicenseTier.STARTER)).isFalse();
    }

    @Test
    void isAvailable_proModuleOnPro_returnsTrue() {
        assertThat(registry.isAvailable("screen-protect", LicenseTier.PRO)).isTrue();
        assertThat(registry.isAvailable("deep-link", LicenseTier.PRO)).isTrue();
    }

    @Test
    void isAvailable_unknownKey_returnsFalse() {
        assertThat(registry.isAvailable("nonexistent-module", LicenseTier.LIFETIME)).isFalse();
    }

    // ─── resolveEnabledModules ───────────────────────────

    @Test
    void resolveEnabledModules_nullInput_returnsEmpty() {
        assertThat(registry.resolveEnabledModules(null, LicenseTier.TRIAL)).isEmpty();
    }

    @Test
    void resolveEnabledModules_emptyInput_returnsEmpty() {
        assertThat(registry.resolveEnabledModules(List.of(), LicenseTier.TRIAL)).isEmpty();
    }

    @Test
    void resolveEnabledModules_validTierAccessibleKeys_returnsAll() {
        List<String> resolved = registry.resolveEnabledModules(
                List.of("offline", "badge"), LicenseTier.TRIAL);
        assertThat(resolved).containsExactly("offline", "badge");
    }

    @Test
    void resolveEnabledModules_unknownKey_isSkipped() {
        List<String> resolved = registry.resolveEnabledModules(
                List.of("offline", "unknown-key"), LicenseTier.TRIAL);
        assertThat(resolved).containsExactly("offline");
        assertThat(resolved).doesNotContain("unknown-key");
    }

    @Test
    void resolveEnabledModules_tierLockedKey_isSkipped() {
        List<String> resolved = registry.resolveEnabledModules(
                List.of("offline", "deep-link"), LicenseTier.TRIAL);
        assertThat(resolved).containsExactly("offline");
        assertThat(resolved).doesNotContain("deep-link");
    }

    @Test
    void resolveEnabledModules_proTier_allowsAllModules() {
        List<String> resolved = registry.resolveEnabledModules(
                List.of("offline", "badge", "domain-lock", "title-bar", "watermark", "expiry",
                        "notifications", "system-tray", "dark-mode", "right-click",
                        "auto-update", "key-bindings", "window-polish", "clipboard",
                        "screen-protect", "deep-link"), LicenseTier.PRO);
        assertThat(resolved).hasSize(16);
    }

    // ─── expiry specific ────────────────────────────────

    @Test
    void expiry_isRegistered() {
        assertThat(registry.get("expiry")).isPresent();
    }

    @Test
    void expiry_hasTrialTier() {
        ModuleDefinition def = registry.get("expiry").orElseThrow();
        assertThat(def.requiredTier()).isEqualTo(LicenseTier.TRIAL);
    }

    @Test
    void expiry_templateFileIsCorrect() {
        ModuleDefinition def = registry.get("expiry").orElseThrow();
        assertThat(def.templateFile()).isEqualTo("modules/expiry.mustache");
    }

    @Test
    void expiry_resolvedForTrialUser() {
        List<String> resolved = registry.resolveEnabledModules(
                List.of("expiry"), LicenseTier.TRIAL);
        assertThat(resolved).containsExactly("expiry");
    }

    // ─── watermark specific ─────────────────────────────

    @Test
    void watermark_isRegistered() {
        assertThat(registry.get("watermark")).isPresent();
    }

    @Test
    void watermark_hasTrialTier() {
        ModuleDefinition def = registry.get("watermark").orElseThrow();
        assertThat(def.requiredTier()).isEqualTo(LicenseTier.TRIAL);
    }

    @Test
    void watermark_templateFileIsCorrect() {
        ModuleDefinition def = registry.get("watermark").orElseThrow();
        assertThat(def.templateFile()).isEqualTo("modules/watermark.mustache");
    }

    @Test
    void watermark_resolvedForTrialUser() {
        List<String> resolved = registry.resolveEnabledModules(
                List.of("watermark"), LicenseTier.TRIAL);
        assertThat(resolved).containsExactly("watermark");
    }

    // ─── title-bar specific ──────────────────────────────

    @Test
    void titleBar_isRegistered() {
        assertThat(registry.get("title-bar")).isPresent();
    }

    @Test
    void titleBar_hasTrialTier() {
        ModuleDefinition def = registry.get("title-bar").orElseThrow();
        assertThat(def.requiredTier()).isEqualTo(LicenseTier.TRIAL);
    }

    @Test
    void titleBar_templateFileIsCorrect() {
        ModuleDefinition def = registry.get("title-bar").orElseThrow();
        assertThat(def.templateFile()).isEqualTo("modules/title-bar.mustache");
    }

    @Test
    void titleBar_resolvedForTrialUser() {
        List<String> resolved = registry.resolveEnabledModules(
                List.of("title-bar"), LicenseTier.TRIAL);
        assertThat(resolved).containsExactly("title-bar");
    }

    // ─── domain-lock specific ────────────────────────────

    @Test
    void domainLock_isRegistered() {
        assertThat(registry.get("domain-lock")).isPresent();
    }

    @Test
    void domainLock_hasTrial_tier() {
        ModuleDefinition def = registry.get("domain-lock").orElseThrow();
        assertThat(def.requiredTier()).isEqualTo(LicenseTier.TRIAL);
    }

    @Test
    void domainLock_isAvailableOnTrial() {
        assertThat(registry.isAvailable("domain-lock", LicenseTier.TRIAL)).isTrue();
    }

    @Test
    void domainLock_isAvailableOnPro() {
        assertThat(registry.isAvailable("domain-lock", LicenseTier.PRO)).isTrue();
    }

    @Test
    void domainLock_resolvedForTrialUser() {
        List<String> resolved = registry.resolveEnabledModules(
                List.of("domain-lock"), LicenseTier.TRIAL);
        assertThat(resolved).containsExactly("domain-lock");
    }

    @Test
    void domainLock_templateFileIsCorrect() {
        ModuleDefinition def = registry.get("domain-lock").orElseThrow();
        assertThat(def.templateFile()).isEqualTo("modules/domain-lock.mustache");
    }

    // ─── get ─────────────────────────────────────────────

    @Test
    void get_existingKey_returnsDefinition() {
        assertThat(registry.get("offline")).isPresent();
        assertThat(registry.get("offline").get().name()).isEqualTo("Offline Detection");
    }

    @Test
    void get_unknownKey_returnsEmpty() {
        assertThat(registry.get("does-not-exist")).isEmpty();
    }

    @Test
    void moduleDefinition_hasCorrectTemplateFile() {
        ModuleDefinition def = registry.get("offline").orElseThrow();
        assertThat(def.templateFile()).isEqualTo("modules/offline.mustache");
    }
}
