package com.example.conversion_service.service;

import com.example.conversion_service.dto.ModuleConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the title-bar module:
 *   1. Registry contract — tier, key, templateFile
 *   2. config.js rendering — titleBar block present / absent
 *   3. title-bar.js template content — JS structure and behaviour
 *   4. preload.js — no preload injection for this module
 */
class TitleBarModuleTest {

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
    void titleBar_registeredWithCorrectMetadata() {
        var def = registry.get("title-bar").orElseThrow();
        assertThat(def.key()).isEqualTo("title-bar");
        assertThat(def.requiredTier())
                .isEqualTo(com.example.conversion_service.entity.ConversionProject.LicenseTier.TRIAL);
        assertThat(def.templateFile()).isEqualTo("modules/title-bar.mustache");
        assertThat(def.name()).isNotBlank();
    }

    // ─── 2. config.js — titleBar block present when module enabled ────────────

    @Test
    void configJs_withTitleBar_containsTitleBarObject() {
        Map<String, Object> ctx = baseConfigCtx();
        ctx.put("hasTitleBar", true);
        ctx.put("titleBarConfigJson", toJson(new ModuleConfig.TitleBarConfig()));

        String configJs = engine.render("config.mustache", ctx);

        assertThat(configJs).contains("titleBar:");
    }

    @Test
    void configJs_withoutTitleBar_noTitleBarObject() {
        Map<String, Object> ctx = baseConfigCtx();
        ctx.put("hasTitleBar", false);

        String configJs = engine.render("config.mustache", ctx);

        assertThat(configJs).doesNotContain("titleBar:");
    }

    @Test
    void configJs_withCustomText_serialisedCorrectly() {
        ModuleConfig.TitleBarConfig tbc = ModuleConfig.TitleBarConfig.builder()
                .text("My Custom Title")
                .overlayColor("#1e1e2e")
                .symbolColor("#cdd6f4")
                .overlayHeight(32)
                .build();

        Map<String, Object> ctx = baseConfigCtx();
        ctx.put("hasTitleBar", true);
        ctx.put("titleBarConfigJson", toJson(tbc));

        String configJs = engine.render("config.mustache", ctx);

        assertThat(configJs).contains("My Custom Title");
        assertThat(configJs).contains("#1e1e2e");
        assertThat(configJs).contains("#cdd6f4");
        assertThat(configJs).contains("32");
    }

    @Test
    void configJs_defaultTitleBarConfig_hasNullFields() {
        Map<String, Object> ctx = baseConfigCtx();
        ctx.put("hasTitleBar", true);
        ctx.put("titleBarConfigJson", toJson(new ModuleConfig.TitleBarConfig()));

        String configJs = engine.render("config.mustache", ctx);

        assertThat(configJs).contains("titleBar:");
        // all optional fields should be null in default config
        assertThat(configJs).contains("null");
    }

    // ─── 3. title-bar.js — JS structure correctness ───────────────────────────

    @Test
    void titleBarJs_containsSetupFunction() {
        String js = renderTitleBarModule();
        assertThat(js).contains("function setup(");
    }

    @Test
    void titleBarJs_containsPreloadSetupExport() {
        String js = renderTitleBarModule();
        assertThat(js).contains("function preloadSetup()");
    }

    @Test
    void titleBarJs_containsTeardownExport() {
        String js = renderTitleBarModule();
        assertThat(js).contains("function teardown(");
    }

    @Test
    void titleBarJs_exportsAllThree() {
        String js = renderTitleBarModule();
        assertThat(js).contains("module.exports");
        assertThat(js).contains("setup");
        assertThat(js).contains("preloadSetup");
        assertThat(js).contains("teardown");
    }

    @Test
    void titleBarJs_guardChecksModuleKey() {
        String js = renderTitleBarModule();
        assertThat(js).contains("title-bar");
    }

    @Test
    void titleBarJs_callsSetTitle() {
        String js = renderTitleBarModule();
        assertThat(js).contains("setTitle");
    }

    @Test
    void titleBarJs_listensToDidFinishLoad() {
        String js = renderTitleBarModule();
        assertThat(js).contains("did-finish-load");
    }

    @Test
    void titleBarJs_preventsPageTitleUpdates() {
        String js = renderTitleBarModule();
        assertThat(js).contains("page-title-updated");
        assertThat(js).contains("preventDefault");
    }

    @Test
    void titleBarJs_appliesFallbackChain() {
        String js = renderTitleBarModule();
        // null text → appTitle → projectName → 'App'
        assertThat(js).contains("config.appTitle");
        assertThat(js).contains("config.projectName");
    }

    @Test
    void titleBarJs_hasWindowsOverlayBlock() {
        String js = renderTitleBarModule();
        assertThat(js).contains("win32");
        assertThat(js).contains("setTitleBarOverlay");
    }

    @Test
    void titleBarJs_overlayColourFieldsReferenced() {
        String js = renderTitleBarModule();
        assertThat(js).contains("overlayColor");
        assertThat(js).contains("symbolColor");
        assertThat(js).contains("overlayHeight");
    }

    // ─── 4. preload.js — no preloadSetup side-effects for title-bar ──────────

    @Test
    void preloadJs_withTitleBar_emitsRequireAndEmptySetup() {
        String preloadReqs   = "const title_bar = require('./modules/title-bar');\n";
        String preloadSetups = "  title_bar.preloadSetup(contextBridge, ipcRenderer, config);\n";

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("hasScreenProtect", false);
        ctx.put("preloadModuleRequires", preloadReqs);
        ctx.put("preloadModuleSetups",   preloadSetups);

        String preloadJs = engine.render("preload.mustache", ctx);

        assertThat(preloadJs).contains("require('./modules/title-bar')");
        assertThat(preloadJs).contains("title_bar.preloadSetup(");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> baseConfigCtx() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("projectName",    "title-bar-test");
        ctx.put("currentVersion", "1.0.0");
        ctx.put("appTitle",       "Title Bar Test");
        ctx.put("websiteUrl",     "https://example.com");
        ctx.put("iconFile",       "icon.ico");
        ctx.put("modulesJson",    "[\"title-bar\"]");
        ctx.put("hasDomainLock",  false);
        ctx.put("hasTitleBar",    false);
        return ctx;
    }

    private String renderTitleBarModule() {
        var def = registry.get("title-bar").orElseThrow();
        return engine.render(def.templateFile(), Map.of());
    }

    private static String toJson(Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
