package com.example.conversion_service.service;

import com.example.conversion_service.dto.ModuleConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the expiry module:
 *   1. Registry contract — tier, key, templateFile
 *   2. config.js rendering — expiry block present / absent, ISO expiresAt
 *   3. expiry.js template — JS structure, lock screen, periodic check
 *   4. preload.js — preloadSetup injection
 *   5. ExpiryConfig defaults
 */
class ExpiryModuleTest {

    private TemplateEngine engine;
    private ModuleRegistry registry;

    @BeforeEach
    void setUp() {
        engine   = new TemplateEngine();
        registry = new ModuleRegistry();
        engine.clearCache();
    }

    // ─── 1. Registry contract ─────────────────────────────────────────────────

    @Test
    void expiry_registeredWithCorrectMetadata() {
        var def = registry.get("expiry").orElseThrow();
        assertThat(def.key()).isEqualTo("expiry");
        assertThat(def.requiredTier())
                .isEqualTo(com.example.conversion_service.entity.ConversionProject.LicenseTier.TRIAL);
        assertThat(def.templateFile()).isEqualTo("modules/expiry.mustache");
        assertThat(def.name()).isNotBlank();
    }

    // ─── 2. config.js — expiry block ─────────────────────────────────────────

    @Test
    void configJs_withExpiry_containsExpiryObject() {
        Map<String, Object> ctx = baseConfigCtx();
        ctx.put("hasExpiry", true);
        ctx.put("expiryConfigJson", toJson(new ModuleConfig.ExpiryConfig()));

        String configJs = engine.render("config.mustache", ctx);

        assertThat(configJs).contains("expiry:");
        assertThat(configJs).contains("lockMessage");
        assertThat(configJs).contains("upgradeUrl");
    }

    @Test
    void configJs_withoutExpiry_noExpiryObject() {
        Map<String, Object> ctx = baseConfigCtx();
        ctx.put("hasExpiry", false);

        String configJs = engine.render("config.mustache", ctx);

        assertThat(configJs).doesNotContain("expiry:");
    }

    @Test
    void configJs_expiresAt_serialisedAsIsoString() {
        Instant future = Instant.now().plus(30, ChronoUnit.DAYS);
        ModuleConfig.ExpiryConfig ec = ModuleConfig.ExpiryConfig.builder()
                .expiresAt(future)
                .build();

        Map<String, Object> ctx = baseConfigCtx();
        ctx.put("hasExpiry", true);
        ctx.put("expiryConfigJson", toJson(ec));

        String configJs = engine.render("config.mustache", ctx);

        assertThat(configJs).contains("expiresAt");
        // Must be ISO string, not epoch millis
        assertThat(configJs).doesNotMatch(".*\"expiresAt\":\\s*\\d{10,}.*");
        assertThat(configJs).containsPattern("expiresAt.*Z");
    }

    @Test
    void configJs_customLockMessageAndUpgradeUrl() {
        ModuleConfig.ExpiryConfig ec = ModuleConfig.ExpiryConfig.builder()
                .lockMessage("License expired. Contact support.")
                .upgradeUrl("https://example.com/upgrade")
                .build();

        Map<String, Object> ctx = baseConfigCtx();
        ctx.put("hasExpiry", true);
        ctx.put("expiryConfigJson", toJson(ec));

        String configJs = engine.render("config.mustache", ctx);

        assertThat(configJs).contains("License expired. Contact support.");
        assertThat(configJs).contains("https://example.com/upgrade");
    }

    // ─── 3. expiry.js — JS structure and behaviour ────────────────────────────

    @Test
    void expiryJs_containsSetupFunction() {
        String js = renderExpiryModule();
        assertThat(js).contains("function setup(");  // now has (mainWindow, config) params
    }

    @Test
    void expiryJs_containsPreloadSetupFunction() {
        String js = renderExpiryModule();
        assertThat(js).contains("function preloadSetup(");
    }

    @Test
    void expiryJs_containsTeardownFunction() {
        String js = renderExpiryModule();
        assertThat(js).contains("function teardown()");
    }

    @Test
    void expiryJs_exportsAll() {
        String js = renderExpiryModule();
        assertThat(js).contains("module.exports");
        assertThat(js).contains("setup");
        assertThat(js).contains("preloadSetup");
        assertThat(js).contains("teardown");
    }

    @Test
    void expiryJs_guardChecksModuleKey() {
        String js = renderExpiryModule();
        assertThat(js).contains("'expiry'");
    }

    @Test
    void expiryJs_earlyReturnWhenNoExpiresAt() {
        String js = renderExpiryModule();
        assertThat(js).contains("if (!expiresAt) return");
    }

    @Test
    void expiryJs_showsLockScreen() {
        String js = renderExpiryModule();
        assertThat(js).contains("showLockScreen");
        assertThat(js).contains("__wtd-expiry-lock");
    }

    @Test
    void expiryJs_lockScreenIsFullCover() {
        String js = renderExpiryModule();
        assertThat(js).contains("position:fixed");
        assertThat(js).contains("width:100%");
        assertThat(js).contains("height:100%");
        assertThat(js).contains("z-index:2147483647");
    }

    @Test
    void expiryJs_upgradeButtonOpensUrl() {
        String js = renderExpiryModule();
        assertThat(js).contains("open-external");
        assertThat(js).contains("upgradeUrl");
    }

    @Test
    void expiryJs_hasPeriodicCheck() {
        String js = renderExpiryModule();
        assertThat(js).contains("setInterval");
        assertThat(js).contains("CHECK_INTERVAL");
    }

    @Test
    void expiryJs_domContentLoadedFallback() {
        String js = renderExpiryModule();
        assertThat(js).contains("DOMContentLoaded");
    }

    @Test
    void expiryJs_lockScreenIsNonDismissible() {
        String js = renderExpiryModule();
        // Overlay uses pointer-events:all (blocks underlying page)
        assertThat(js).contains("pointer-events:all");
    }

    @Test
    void expiryJs_idempotentInjection() {
        String js = renderExpiryModule();
        assertThat(js).contains("getElementById");
        assertThat(js).contains("__wtd-expiry-lock");
    }

    // ─── 4. preload.js injection ──────────────────────────────────────────────

    @Test
    void preloadJs_withExpiry_callsPreloadSetup() {
        String preloadReqs   = "const expiry = require('./modules/expiry');\n";
        String preloadSetups = "  expiry.preloadSetup(contextBridge, ipcRenderer, config);\n";

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("hasScreenProtect", false);
        ctx.put("preloadModuleRequires", preloadReqs);
        ctx.put("preloadModuleSetups",   preloadSetups);

        String preloadJs = engine.render("preload.mustache", ctx);

        assertThat(preloadJs).contains("require('./modules/expiry')");
        assertThat(preloadJs).contains("expiry.preloadSetup(");
    }

    // ─── 5. ExpiryConfig defaults ─────────────────────────────────────────────

    @Test
    void expiryConfig_defaultExpiresAt_isNull() {
        assertThat(new ModuleConfig.ExpiryConfig().getExpiresAt()).isNull();
    }

    @Test
    void expiryConfig_defaultLockMessage_isSet() {
        String msg = new ModuleConfig.ExpiryConfig().getLockMessage();
        assertThat(msg).isNotBlank();
        assertThat(msg).containsIgnoringCase("trial");
    }

    @Test
    void expiryConfig_defaultUpgradeUrl_isWebtodesk() {
        String url = new ModuleConfig.ExpiryConfig().getUpgradeUrl();
        assertThat(url).isNotBlank();
        assertThat(url).contains("webtodesk.com");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> baseConfigCtx() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("projectName",    "expiry-test");
        ctx.put("currentVersion", "1.0.0");
        ctx.put("appTitle",       "Expiry Test");
        ctx.put("websiteUrl",     "https://example.com");
        ctx.put("iconFile",       "icon.ico");
        ctx.put("modulesJson",    "[\"expiry\"]");
        ctx.put("hasDomainLock",  false);
        ctx.put("hasTitleBar",    false);
        ctx.put("hasWatermark",   false);
        ctx.put("hasExpiry",      false);
        return ctx;
    }

    private String renderExpiryModule() {
        var def = registry.get("expiry").orElseThrow();
        return engine.render(def.templateFile(), Map.of());
    }

    private static String toJson(Object obj) {
        try {
            return new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
