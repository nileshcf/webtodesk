package com.example.conversion_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Typed per-module configuration — stored as an embedded document in ConversionProject.
 * Each inner class maps 1:1 to a module key. Fields are null-safe: missing = use defaults.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleConfig {

    private DomainLockConfig domainLock;
    private TitleBarConfig   titleBar;
    private WatermarkConfig  watermark;
    private ExpiryConfig     expiry;

    // ── Domain Lock ───────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DomainLockConfig {
        /** Hostnames that are permitted. Empty = allow all except blockedDomains. */
        @Builder.Default
        private List<String> allowedDomains = new ArrayList<>();

        /** Hostnames that are always blocked regardless of allowedDomains. */
        @Builder.Default
        private List<String> blockedDomains = new ArrayList<>();

        /** Message shown (via alert) when navigation is blocked. */
        @Builder.Default
        private String blockMessage = "Navigation to this destination is not allowed.";

        /**
         * When true, blocked URLs are opened in the system browser instead of
         * silently dropped. Useful for external links.
         */
        @Builder.Default
        private boolean allowExternalInBrowser = false;
    }

    // ── Title Bar ─────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TitleBarConfig {
        /** Custom window title. Null = falls back to appTitle. */
        private String text;

        /**
         * Windows DWM title-bar background colour (hex, e.g. "#1e1e2e").
         * Requires the BrowserWindow to be created with titleBarOverlay:true.
         * Null = OS default.
         */
        private String overlayColor;

        /**
         * Windows DWM title-bar button / symbol colour (hex, e.g. "#cdd6f4").
         * Null = OS default.
         */
        private String symbolColor;

        /**
         * Windows DWM title-bar height in pixels (e.g. 32).
         * Null = OS default (≈32 px).
         */
        private Integer overlayHeight;
    }

    // ── Watermark ─────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WatermarkConfig {
        /**
         * Badge label text. Null = "Powered by WebToDesk" (TRIAL brand).
         * PRO users set this to their own text.
         */
        private String text;

        /**
         * Badge position: top-left | top-right | bottom-left | bottom-right.
         * Default top-right places the badge near native window controls (Win/Linux).
         */
        @Builder.Default
        private String position = "top-right";

        /**
         * When true, appends " · N days left" to the label using expiresAt.
         * Ignored when expiresAt is null.
         */
        @Builder.Default
        private boolean showDaysRemaining = true;

        /**
         * Expiry timestamp used to compute days-remaining. Populated by the service
         * from ExpiryConfig.expiresAt when both modules are enabled — callers need
         * not set this manually.
         */
        private Instant expiresAt;

        /** Badge background colour as a CSS colour string. */
        @Builder.Default
        private String badgeColor = "rgba(234,88,12,0.92)";

        /** Badge text colour. */
        @Builder.Default
        private String textColor = "#ffffff";

        /** Overall opacity of the badge element (0.0–1.0). */
        @Builder.Default
        private double opacity = 1.0;
    }

    // ── Expiry ────────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpiryConfig {
        /** Timestamp at which the built app locks itself. Null = never. */
        private Instant expiresAt;

        /** Message shown on the lock screen when the app has expired. */
        @Builder.Default
        private String lockMessage = "Your trial has expired. Please upgrade to continue.";

        /** URL of the upgrade/pricing page shown on the lock screen. */
        @Builder.Default
        private String upgradeUrl = "https://webtodesk.com/pricing";
    }
}
