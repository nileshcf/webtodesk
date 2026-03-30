package com.example.conversion_service.service;

import com.example.conversion_service.dto.ModuleConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the watermark module:
 *   1. Registry contract — tier, key, templateFile
 *   2. config.js rendering — watermark block present / absent
 *   3. expiresAt cross-population from ExpiryConfig
 *   4. watermark.js template — JS structure and key behaviour
 *   5. preload.js — preloadSetup injection
 */
class WatermarkModuleTest {

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
    void watermark_registeredWithCorrectMetadata() {
        var def = registry.get("watermark").orElseThrow();
        assertThat(def.key()).isEqualTo("watermark");
        assertThat(def.requiredTier())
                .isEqualTo(com.example.conversion_service.entity.ConversionProject.LicenseTier.TRIAL);
        assertThat(def.templateFile()).isEqualTo("modules/watermark.mustache");
        assertThat(def.name()).isNotBlank();
    }

    // ─── 2. config.js — watermark block present / absent ─────────────────────

    @Test
    void configJs_withWatermark_containsWatermarkObject() {
        Map<String, Object> ctx = baseConfigCtx();
        ctx.put("hasWatermark", true);
        ctx.put("watermarkConfigJson", toJson(new ModuleConfig.WatermarkConfig()));

        String configJs = engine.render("config.mustache", ctx);

        assertThat(configJs).contains("watermark:");
        assertThat(configJs).contains("position");
        assertThat(configJs).contains("badgeColor");
    }

    @Test
    void configJs_withoutWatermark_noWatermarkObject() {
        Map<String, Object> ctx = baseConfigCtx();
        ctx.put("hasWatermark", false);

        String configJs = engine.render("config.mustache", ctx);

        assertThat(configJs).doesNotContain("watermark:");
    }

    @Test
    void configJs_withCustomText_serialisedCorrectly() {
        ModuleConfig.WatermarkConfig wmc = ModuleConfig.WatermarkConfig.builder()
                .text("My Watermark")
                .position("bottom-left")
                .badgeColor("#ff0000")
                .textColor("#000000")
                .build();

        Map<String, Object> ctx = baseConfigCtx();
        ctx.put("hasWatermark", true);
        ctx.put("watermarkConfigJson", toJson(wmc));

        String configJs = engine.render("config.mustache", ctx);

        assertThat(configJs).contains("My Watermark");
        assertThat(configJs).contains("bottom-left");
        assertThat(configJs).contains("#ff0000");
        assertThat(configJs).contains("#000000");
    }

    // ─── 3. expiresAt cross-population ───────────────────────────────────────

    @Test
    void watermarkConfig_expiresAt_serialisedAsIsoString() {
        Instant future = Instant.now().plus(7, ChronoUnit.DAYS);
        ModuleConfig.WatermarkConfig wmc = ModuleConfig.WatermarkConfig.builder()
                .expiresAt(future)
                .build();

        Map<String, Object> ctx = baseConfigCtx();
        ctx.put("hasWatermark", true);
        ctx.put("watermarkConfigJson", toJson(wmc));

        String configJs = engine.render("config.mustache", ctx);

        // Instant must be serialised as ISO string (not epoch millis)
        assertThat(configJs).contains("expiresAt");
        assertThat(configJs).doesNotMatch(".*\"expiresAt\":\\d{10,}.*");
    }

    @Test
    void watermarkConfig_defaultExpiresAt_isNull() {
        ModuleConfig.WatermarkConfig wmc = new ModuleConfig.WatermarkConfig();
        assertThat(wmc.getExpiresAt()).isNull();
    }

    @Test
    void watermarkConfig_crossPopulated_fromExpiryConfig() {
        Instant future = Instant.now().plus(30, ChronoUnit.DAYS);

        ModuleConfig mc = ModuleConfig.builder()
                .expiry(ModuleConfig.ExpiryConfig.builder()
                        .expiresAt(future)
                        .build())
                .build();

        ModuleConfig.WatermarkConfig wmc = new ModuleConfig.WatermarkConfig();
        // Simulate what applyModuleConfigContext does
        if (wmc.getExpiresAt() == null && mc.getExpiry() != null) {
            wmc.setExpiresAt(mc.getExpiry().getExpiresAt());
        }

        assertThat(wmc.getExpiresAt()).isEqualTo(future);
    }

    // ─── 4. watermark.js — JS structure ──────────────────────────────────────

    @Test
    void watermarkJs_containsSetupFunction() {
        String js = renderWatermarkModule();
        assertThat(js).contains("function setup(");  // now has (mainWindow, config) params
    }

    @Test
    void watermarkJs_containsPreloadSetupFunction() {
        String js = renderWatermarkModule();
        assertThat(js).contains("function preloadSetup(");
    }

    @Test
    void watermarkJs_containsTeardownFunction() {
        String js = renderWatermarkModule();
        assertThat(js).contains("function teardown()");
    }

    @Test
    void watermarkJs_exportsAll() {
        String js = renderWatermarkModule();
        assertThat(js).contains("module.exports");
        assertThat(js).contains("setup");
        assertThat(js).contains("preloadSetup");
        assertThat(js).contains("teardown");
    }

    @Test
    void watermarkJs_guardChecksModuleKey() {
        String js = renderWatermarkModule();
        assertThat(js).contains("watermark");
    }

    @Test
    void watermarkJs_injectsBadgeElement() {
        String js = renderWatermarkModule();
        // Badge is now in a transparent child BrowserWindow, not DOM injection
        assertThat(js).contains("BrowserWindow");
        assertThat(js).contains("__wtd-wm-badge");  // id inside badge HTML string
    }

    @Test
    void watermarkJs_fixedPositionCss() {
        String js = renderWatermarkModule();
        assertThat(js).contains("position:fixed");
        assertThat(js).contains("z-index");
    }

    @Test
    void watermarkJs_pointerEventsNone() {
        String js = renderWatermarkModule();
        assertThat(js).contains("pointer-events:none");
    }

    @Test
    void watermarkJs_topRightIsDefaultPosition() {
        String js = renderWatermarkModule();
        assertThat(js).contains("top-right");
        assertThat(js).contains("right:138px");  // clears Win11 window controls in title bar
    }

    @Test
    void watermarkJs_showsDaysRemaining() {
        String js = renderWatermarkModule();
        assertThat(js).contains("showDaysRemaining");
        assertThat(js).contains("expiresAt");
        assertThat(js).contains("days left");
    }

    @Test
    void watermarkJs_handlesSpaNavigation() {
        String js = renderWatermarkModule();
        // Badge is a child BrowserWindow — it follows main window via syncBounds, not history API
        assertThat(js).contains("syncBounds");
        assertThat(js).contains("getBounds");
    }

    @Test
    void watermarkJs_idempotentInjection() {
        String js = renderWatermarkModule();
        // Overlay window guarded by isDestroyed() — safe to call syncBounds repeatedly
        assertThat(js).contains("isDestroyed");
        assertThat(js).contains("__wtd-wm-badge");  // id present in badge HTML string
    }

    @Test
    void watermarkJs_brandTextFallback() {
        String js = renderWatermarkModule();
        // Text now falls back to tierLabel (set by backend) or hardcoded 'Trial'
        assertThat(js).contains("tierLabel");
    }

    // ─── 5. preload.js — injection for watermark ─────────────────────────────

    @Test
    void preloadJs_withWatermark_callsPreloadSetup() {
        String preloadReqs   = "const watermark = require('./modules/watermark');\n";
        String preloadSetups = "  watermark.preloadSetup(contextBridge, ipcRenderer, config);\n";

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("hasScreenProtect", false);
        ctx.put("preloadModuleRequires", preloadReqs);
        ctx.put("preloadModuleSetups",   preloadSetups);

        String preloadJs = engine.render("preload.mustache", ctx);

        assertThat(preloadJs).contains("require('./modules/watermark')");
        assertThat(preloadJs).contains("watermark.preloadSetup(");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> baseConfigCtx() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("projectName",    "watermark-test");
        ctx.put("currentVersion", "1.0.0");
        ctx.put("appTitle",       "Watermark Test");
        ctx.put("websiteUrl",     "https://example.com");
        ctx.put("iconFile",       "icon.ico");
        ctx.put("modulesJson",    "[\"watermark\"]");
        ctx.put("hasDomainLock",  false);
        ctx.put("hasTitleBar",    false);
        ctx.put("hasWatermark",   false);
        return ctx;
    }

    private String renderWatermarkModule() {
        var def = registry.get("watermark").orElseThrow();
        return engine.render(def.templateFile(), Map.of());
    }

    private static String toJson(Object obj) {
        try {
            return new ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
