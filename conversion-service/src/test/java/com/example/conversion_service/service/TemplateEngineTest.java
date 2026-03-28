package com.example.conversion_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TemplateEngine — renders actual Mustache templates from the classpath.
 * No Spring context needed; TemplateEngine has no Spring dependencies.
 */
class TemplateEngineTest {

    private TemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        templateEngine = new TemplateEngine();
        templateEngine.clearCache();
    }

    @Test
    void render_configMustache_containsProjectFields() {
        Map<String, Object> ctx = Map.of(
                "projectName",    "my-cool-app",
                "currentVersion", "1.2.3",
                "appTitle",       "My Cool App",
                "websiteUrl",     "https://example.com",
                "iconFile",       "icon.ico",
                "modulesJson",    "[]"
        );

        String result = templateEngine.render("config.mustache", ctx);

        assertThat(result).contains("my-cool-app");
        assertThat(result).contains("1.2.3");
        assertThat(result).contains("My Cool App");
        assertThat(result).contains("https://example.com");
        assertThat(result).contains("icon.ico");
    }

    @Test
    void render_mainMustache_containsBoilerplate() {
        Map<String, Object> ctx = Map.of(
                "projectName",    "test-app",
                "currentVersion", "1.0.0",
                "appTitle",       "Test App",
                "websiteUrl",     "https://test.com",
                "iconFile",       "icon.ico",
                "modulesJson",    "[]",
                "hasModules",     false,
                "moduleRequires", "",
                "moduleSetups",   ""
        );

        String result = templateEngine.render("main.mustache", ctx);

        assertThat(result).contains("BrowserWindow");
        assertThat(result).contains("createWindow");
    }

    @Test
    void render_packageMustache_containsProjectName() {
        Map<String, Object> ctx = Map.of(
                "projectName",    "pkg-app",
                "currentVersion", "2.0.0",
                "appTitle",       "Pkg App",
                "websiteUrl",     "https://pkg.com",
                "iconFile",       "icon.ico",
                "modulesJson",    "[]",
                "hasModules",     false,
                "moduleRequires", "",
                "moduleSetups",   ""
        );

        String result = templateEngine.render("package.mustache", ctx);

        assertThat(result).contains("pkg-app");
        assertThat(result).contains("2.0.0");
        assertThat(result).contains("electron-builder");
    }

    @Test
    void render_cachesTemplateAfterFirstCall() {
        Map<String, Object> ctx = Map.of(
                "projectName",    "cache-test",
                "currentVersion", "1.0.0",
                "appTitle",       "Cache Test",
                "websiteUrl",     "https://cache.com",
                "iconFile",       "icon.ico",
                "modulesJson",    "[]"
        );

        assertThat(templateEngine.getCacheSize()).isEqualTo(0);
        templateEngine.render("config.mustache", ctx);
        assertThat(templateEngine.getCacheSize()).isEqualTo(1);
        templateEngine.render("config.mustache", ctx);
        assertThat(templateEngine.getCacheSize()).isEqualTo(1); // still 1 — cached
    }

    @Test
    void render_multipleTemplates_cachesAllUnique() {
        Map<String, Object> ctx = Map.of(
                "projectName",    "multi",
                "currentVersion", "1.0.0",
                "appTitle",       "Multi",
                "websiteUrl",     "https://multi.com",
                "iconFile",       "icon.ico",
                "modulesJson",    "[]",
                "hasModules",     false,
                "moduleRequires", "",
                "moduleSetups",   ""
        );

        templateEngine.render("config.mustache",  ctx);
        templateEngine.render("main.mustache",    ctx);
        templateEngine.render("preload.mustache", ctx);
        templateEngine.render("package.mustache", ctx);

        assertThat(templateEngine.getCacheSize()).isEqualTo(4);
    }

    @Test
    void clearCache_resetsSize() {
        Map<String, Object> ctx = Map.of(
                "projectName", "x", "currentVersion", "1.0.0",
                "appTitle", "X", "websiteUrl", "https://x.com",
                "iconFile", "icon.ico", "modulesJson", "[]"
        );
        templateEngine.render("config.mustache", ctx);
        assertThat(templateEngine.getCacheSize()).isGreaterThan(0);

        templateEngine.clearCache();
        assertThat(templateEngine.getCacheSize()).isEqualTo(0);
    }

    @Test
    void render_throwsOnNonExistentTemplate() {
        assertThatThrownBy(() -> templateEngine.render("nonexistent.mustache", Map.of()))
                .isInstanceOf(Exception.class);
    }
}
