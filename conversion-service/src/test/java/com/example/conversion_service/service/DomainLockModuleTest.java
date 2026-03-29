package com.example.conversion_service.service;

import com.example.conversion_service.dto.ModuleConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the domain-lock module:
 *   1. Registry contract — tier, key, templateFile
 *   2. config.js rendering — domainLock block present / absent
 *   3. domain-lock.js template content — JS structure correctness
 *   4. preload.js injection — preloadSetup call is emitted
 */
class DomainLockModuleTest {

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
    void domainLock_registeredWithCorrectMetadata() {
        var def = registry.get("domain-lock").orElseThrow();
        assertThat(def.key()).isEqualTo("domain-lock");
        assertThat(def.requiredTier()).isEqualTo(com.example.conversion_service.entity.ConversionProject.LicenseTier.TRIAL);
        assertThat(def.templateFile()).isEqualTo("modules/domain-lock.mustache");
        assertThat(def.name()).isNotBlank();
    }

    // ─── 2. config.js — domain-lock block present when module enabled ─────────

    @Test
    void configJs_withDomainLock_containsDomainLockObject() {
        Map<String, Object> ctx = baseConfigCtx();
        ctx.put("hasDomainLock", true);
        ctx.put("domainLockConfigJson", defaultDomainLockJson());

        String configJs = engine.render("config.mustache", ctx);

        assertThat(configJs).contains("domainLock:");
        assertThat(configJs).contains("allowedDomains");
        assertThat(configJs).contains("blockedDomains");
        assertThat(configJs).contains("blockMessage");
        assertThat(configJs).contains("allowExternalInBrowser");
    }

    @Test
    void configJs_withoutDomainLock_noDomainLockObject() {
        Map<String, Object> ctx = baseConfigCtx();
        ctx.put("hasDomainLock", false);

        String configJs = engine.render("config.mustache", ctx);

        assertThat(configJs).doesNotContain("domainLock:");
    }

    @Test
    void configJs_withCustomAllowedDomains_serialisedCorrectly() throws Exception {
        ModuleConfig.DomainLockConfig dlc = ModuleConfig.DomainLockConfig.builder()
                .allowedDomains(List.of("example.com", "api.example.com"))
                .blockedDomains(List.of("ads.example.com"))
                .blockMessage("Access denied")
                .allowExternalInBrowser(true)
                .build();

        Map<String, Object> ctx = baseConfigCtx();
        ctx.put("hasDomainLock", true);
        ctx.put("domainLockConfigJson", toJson(dlc));

        String configJs = engine.render("config.mustache", ctx);

        assertThat(configJs).contains("example.com");
        assertThat(configJs).contains("api.example.com");
        assertThat(configJs).contains("ads.example.com");
        assertThat(configJs).contains("Access denied");
        assertThat(configJs).contains("true");
    }

    @Test
    void configJs_withEmptyAllowedDomains_defaultsPresent() {
        Map<String, Object> ctx = baseConfigCtx();
        ctx.put("hasDomainLock", true);
        ctx.put("domainLockConfigJson", defaultDomainLockJson());

        String configJs = engine.render("config.mustache", ctx);

        assertThat(configJs).contains("\"allowedDomains\":[]");
        assertThat(configJs).contains("\"blockedDomains\":[]");
    }

    // ─── 3. domain-lock.js module file — JS structure ─────────────────────────

    @Test
    void domainLockJs_containsSetupFunction() {
        String js = renderDomainLockModule();
        assertThat(js).contains("function setup(");
    }

    @Test
    void domainLockJs_containsPreloadSetupExport() {
        String js = renderDomainLockModule();
        assertThat(js).contains("function preloadSetup()");
        assertThat(js).contains("preloadSetup");
    }

    @Test
    void domainLockJs_containsModuleExports() {
        String js = renderDomainLockModule();
        assertThat(js).contains("module.exports");
        assertThat(js).contains("setup");
        assertThat(js).contains("teardown");
    }

    @Test
    void domainLockJs_containsWillNavigateListener() {
        String js = renderDomainLockModule();
        assertThat(js).contains("will-navigate");
    }

    @Test
    void domainLockJs_containsWindowOpenHandler() {
        String js = renderDomainLockModule();
        assertThat(js).contains("setWindowOpenHandler");
    }

    @Test
    void domainLockJs_containsIsBlockedFunction() {
        String js = renderDomainLockModule();
        assertThat(js).contains("isBlocked");
    }

    @Test
    void domainLockJs_guardChecksModuleKey() {
        String js = renderDomainLockModule();
        assertThat(js).contains("domain-lock");
    }

    @Test
    void domainLockJs_readsConfigDomainLock() {
        String js = renderDomainLockModule();
        assertThat(js).contains("config.domainLock");
    }

    @Test
    void domainLockJs_handlesAllowExternalInBrowser() {
        String js = renderDomainLockModule();
        assertThat(js).contains("allowExternalInBrowser");
        assertThat(js).contains("shell.openExternal");
    }

    // ─── 4. preload.js — preloadSetup injected for domain-lock ───────────────

    @Test
    void preloadJs_withDomainLock_callsPreloadSetup() {
        String preloadReqs   = "const domain_lock = require('./modules/domain-lock');\n";
        String preloadSetups = "  domain_lock.preloadSetup(contextBridge, ipcRenderer, config);\n";

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("hasScreenProtect", false);
        ctx.put("preloadModuleRequires", preloadReqs);
        ctx.put("preloadModuleSetups",   preloadSetups);

        String preloadJs = engine.render("preload.mustache", ctx);

        assertThat(preloadJs).contains("require('./modules/domain-lock')");
        assertThat(preloadJs).contains("domain_lock.preloadSetup(");
    }

    @Test
    void preloadJs_withNoDomainLock_noPreloadSetupCall() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("hasScreenProtect", false);
        ctx.put("preloadModuleRequires", "");
        ctx.put("preloadModuleSetups",   "");

        String preloadJs = engine.render("preload.mustache", ctx);

        assertThat(preloadJs).doesNotContain("preloadSetup");
    }

    @Test
    void preloadJs_alwaysImportsConfig() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("hasScreenProtect", false);
        ctx.put("preloadModuleRequires", "");
        ctx.put("preloadModuleSetups",   "");

        String preloadJs = engine.render("preload.mustache", ctx);

        assertThat(preloadJs).contains("require('./config')");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> baseConfigCtx() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("projectName",    "test-app");
        ctx.put("currentVersion", "1.0.0");
        ctx.put("appTitle",       "Test App");
        ctx.put("websiteUrl",     "https://example.com");
        ctx.put("iconFile",       "icon.ico");
        ctx.put("modulesJson",    "[\"domain-lock\"]");
        ctx.put("hasDomainLock",  false);
        return ctx;
    }

    private String renderDomainLockModule() {
        var def = registry.get("domain-lock").orElseThrow();
        return engine.render(def.templateFile(), Map.of());
    }

    private String defaultDomainLockJson() {
        return toJson(new ModuleConfig.DomainLockConfig());
    }

    private static String toJson(Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
