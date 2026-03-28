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
    void getAllModules_returnsAllFive() {
        List<ModuleDefinition> all = registry.getAllModules();
        assertThat(all).hasSize(5);
    }

    @Test
    void getAllModules_containsExpectedKeys() {
        List<String> keys = registry.getAllModules().stream()
                .map(ModuleDefinition::key).toList();
        assertThat(keys).containsExactlyInAnyOrder(
                "splash-screen", "offline", "badge", "screen-protect", "deep-link");
    }

    // ─── getAvailableModules by tier ─────────────────────

    @Test
    void getAvailableModules_trial_returnsThreeModules() {
        List<ModuleDefinition> modules = registry.getAvailableModules(LicenseTier.TRIAL);
        assertThat(modules).hasSize(3);
        List<String> keys = modules.stream().map(ModuleDefinition::key).toList();
        assertThat(keys).containsExactlyInAnyOrder("splash-screen", "offline", "badge");
    }

    @Test
    void getAvailableModules_starter_returnsThreeModules() {
        List<ModuleDefinition> modules = registry.getAvailableModules(LicenseTier.STARTER);
        assertThat(modules).hasSize(3);
    }

    @Test
    void getAvailableModules_pro_returnsAllFive() {
        List<ModuleDefinition> modules = registry.getAvailableModules(LicenseTier.PRO);
        assertThat(modules).hasSize(5);
    }

    @Test
    void getAvailableModules_lifetime_returnsAllFive() {
        List<ModuleDefinition> modules = registry.getAvailableModules(LicenseTier.LIFETIME);
        assertThat(modules).hasSize(5);
    }

    // ─── isAvailable ─────────────────────────────────────

    @Test
    void isAvailable_trialModuleOnTrial_returnsTrue() {
        assertThat(registry.isAvailable("offline", LicenseTier.TRIAL)).isTrue();
        assertThat(registry.isAvailable("badge", LicenseTier.TRIAL)).isTrue();
        assertThat(registry.isAvailable("splash-screen", LicenseTier.TRIAL)).isTrue();
    }

    @Test
    void isAvailable_proModuleOnTrial_returnsFalse() {
        assertThat(registry.isAvailable("screen-protect", LicenseTier.TRIAL)).isFalse();
        assertThat(registry.isAvailable("deep-link", LicenseTier.TRIAL)).isFalse();
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
                List.of("offline", "badge", "screen-protect", "deep-link"), LicenseTier.PRO);
        assertThat(resolved).hasSize(4);
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
