# WebToDesk — Feature Module Injection Plan

## Project Architecture Overview

WebToDesk is a **Spring Boot microservices** platform that converts websites into desktop Electron apps. The core build pipeline lives in `conversion-service` and works as follows:

1. **ConversionService.java** — Holds the project entity and *generates* the raw Electron source files (`main.js`, `preload.js`, `config.js`, `package.json`) as in-memory strings.
2. **BuildService.java** — Takes those files, writes them to a temp workspace, runs `npm install` + `electron-builder`, finds the installer artifact, then uploads it to Cloudflare R2.
3. **ConversionProject entity** — Stores project metadata (URL, title, icon, status, artifact path).

**The injection point is clear:** all feature modules are code strings appended/merged into `generateMainJs()`, `generatePreloadJs()`, `generateConfigJs()`, and `generatePackageJson()` inside `ConversionService.java`, gated by tier logic before `writeElectronFiles()` is called in `BuildService.java`.

---

## Implementation Strategy

### Core Pattern: Module Injection via `FeatureConfig`

Introduce a `FeatureConfig` record that travels with every build request. `ConversionService` reads this config and conditionally includes module code blocks. This keeps every feature as an isolated, testable snippet rather than entangled spaghetti.

```
ConversionProject
  └── featureConfig: FeatureConfig (JSON column)
        └── tier: FREE | STARTER | FIVE_YEAR | LIFETIME
        └── splashScreen: SplashScreenConfig { logoUrl, showOurLogo }
        └── titleBar: TitleBarConfig { enabled, text, style }
        └── domainLock: DomainLockConfig { allowedDomains[], blockedDomains[] }
        └── fileDownload: boolean
        └── screenCaptureProtection: boolean
        └── watermark: WatermarkConfig { text, position, opacity, useOurs }
        └── keyBindings: KeyBindingConfig[]
        └── offlineCache: OfflineCacheConfig { strategy, maxAge }
        └── autoUpdate: AutoUpdateConfig { feedUrl }
        └── notifications: boolean
        └── systemTray: TrayConfig { tooltip, contextMenu[] }
        └── darkLightSync: boolean
        └── clipboardIntegration: boolean
        └── windowPolish: WindowPolishConfig { blur, alwaysOnTop, frame }
        └── rightClickDisable: RightClickConfig { disable, customMenuItems[] }
        └── fileSystemAccess: FileSystemConfig { allowedPaths[] }
        └── globalHotkeys: GlobalHotkeyConfig[]
        └── expiry: ExpiryConfig { expiresAt, lockMessage }
        └── buildLimit: BuildLimitConfig { buildsAllowed, buildsUsed }
```

---

## Tier Capability Matrix

| Feature | Free (Trial) | Starter/1-Yr | Five-Year | Lifetime |
|---|---|---|---|---|
| Splash screen (our logo visible) | ✅ | ✅ | ✅ | ✅ |
| File download | ✅ | ✅ | ✅ | ✅ |
| 1 active app | ✅ | — | — | — |
| Domain lock (base URL only) | ✅ | — | — | — |
| Title bar basic | ✅ | ✅ | ✅ | ✅ |
| Queued builds | ✅ | — | — | — |
| Watermark (our brand) | ✅ | — | — | — |
| 30-day expiry → upgrade screen | ✅ | — | — | — |
| Priority queue | — | ✅ | ✅ | ✅ |
| Full domain whitelist/blacklist | — | ✅ | ✅ | ✅ |
| Screen capture protection | — | ✅ | ✅ | ✅ |
| Custom watermark (live, no ours) | — | ✅ | ✅ | ✅ |
| Custom key bindings | — | ✅ | ✅ | ✅ |
| Offline caching | — | ✅ | ✅ | ✅ |
| Auto-updates | — | ✅ | ✅ | ✅ |
| Native notifications | — | ✅ | ✅ | ✅ |
| System tray + context menu | — | ✅ | ✅ | ✅ |
| Dark/light mode sync | — | ✅ | ✅ | ✅ |
| Clipboard integration | — | ✅ | ✅ | ✅ |
| Window polish (blur, always-on-top) | — | ✅ | ✅ | ✅ |
| Right-click disable / custom menu | — | ✅ | ✅ | ✅ |
| File system access | — | ✅ | ✅ | ✅ |
| Global hotkeys | — | ✅ | ✅ | ✅ |
| Build quota | 1 active | 10/mo | 50/mo | Unlimited (fair use) |
| App license duration | 30 days | 1 year | 5 years | Lifetime |

---

## Phase 1 — Foundation & Free Tier Modules

**Goal:** Restructure `ConversionService.java` to accept `FeatureConfig`, add DB column, and implement all Free Tier features.

### 1.1 — DB & Entity Changes

- Add `feature_config` JSON column to `ConversionProject` entity using `@Column(columnDefinition = "TEXT")` + Jackson serialization.
- Add `tier` enum field: `FREE | STARTER | FIVE_YEAR | LIFETIME`.
- Add `expiresAt` (Instant), `buildCount` (int), `maxBuilds` (int) fields.
- Update `CreateConversionRequest` DTO to accept `featureConfig`.

### 1.2 — Module: Splash Screen (Free + All Tiers)

**Where:** `generateMainJs()` — inject before `mainWindow.loadURL()`.

**Logic:**
- Create a borderless `BrowserWindow` (400×300) showing the logo image.
- Free tier: always shows our WebToDesk logo watermark alongside user logo.
- Pro tiers: only user logo visible (`showOurLogo: false`).
- Logo URL fetched from `SplashScreenConfig.logoUrl` (uploaded to R2 during project creation).
- Splash auto-closes after 2.5 seconds or when the main window's `did-finish-load` fires.

```javascript
// INJECT: splashScreen module
function createSplashWindow() {
  const splash = new BrowserWindow({
    width: 400, height: 300,
    frame: false, transparent: true, alwaysOnTop: true,
    webPreferences: { nodeIntegration: false }
  });
  splash.loadURL(`data:text/html,...`); // inline HTML with logo
  return splash;
}
```

**Config fields:** `logoUrl`, `showOurLogo` (boolean).

### 1.3 — Module: File Download (Free + All Tiers)

**Where:** `generateMainJs()` — add `will-download` handler on `webContents.session`.

**Logic:**
- Intercept `session.defaultSession.on('will-download')`.
- Show native Save dialog using `dialog.showSaveDialogSync`.
- Set download path, attach progress listener, emit IPC events for UI progress display.
- Free tier: downloads work but installer is watermarked.

### 1.4 — Module: Domain Lock (Free Tier Only)

**Where:** `generateMainJs()` — add `will-navigate` and `new-window` event handlers.

**Logic:**
- `allowedDomains` array (from `DomainLockConfig`) contains only the base URL hostname.
- On every navigation attempt, parse URL hostname and block if not in allowlist.
- Show in-app toast: "Navigation to external sites is disabled in this version."
- Pro tier: replaces with full `DomainWhitelistBlacklist` module (Phase 2).

### 1.5 — Module: Title Bar Customization (Free + All Tiers)

**Where:** `generateMainJs()` — `BrowserWindow` constructor options + `setTitle` logic.

**Logic:**
- `enabled: false` → `frame: false`, custom frameless window with draggable CSS region injected via `preload.js`.
- `enabled: true` → standard frame, `title` set from `TitleBarConfig.text`.
- Style options (`style: 'hidden' | 'hiddenInset' | 'default'`) mapped to Electron's `titleBarStyle` option on macOS.
- `page-title-updated` event always prevented to keep the custom title.

### 1.6 — Module: Build Queue Priority

**Where:** `BuildService.java` — `@Async("buildExecutor")` thread pool configuration.

**Logic:**
- Free tier: uses a `buildExecutorFree` thread pool with `corePoolSize=1`, `queueCapacity=50`.
- Pro tiers: uses `buildExecutorPro` with `corePoolSize=5`, instant dispatch.
- `BuildQueueService` bean selects executor based on project tier before triggering.

### 1.7 — Module: Watermark (Free Tier — Our Brand)

**Where:** `generatePreloadJs()` — inject CSS overlay into the page DOM.

**Logic:**
- Inject a fixed-position `div` with "Powered by WebToDesk" text + logo image.
- `opacity: 0.15`, `pointer-events: none`, bottom-right corner.
- Cannot be removed by the website (injected after every `did-finish-load`).
- Free tier: always our watermark. Pro: replaced by custom watermark module.

### 1.8 — Module: 30-Day Expiry + Upgrade Lock

**Where:** `generateMainJs()` + `generatePreloadJs()` + backend enforcement.

**Logic (two-layer):**

**Layer 1 — Backend:** `BuildService` checks `project.getExpiresAt()` before allowing a new build. Expired free-tier projects get `status: EXPIRED`, no new builds allowed.

**Layer 2 — Runtime (in the built app):** `config.js` includes `expiresAt` timestamp. On app start, `main.js` checks `Date.now() > expiresAt`. If expired:
- Load a local `expired.html` page instead of the website URL.
- `expired.html` is bundled with the build, shows upgrade CTA with link to WebToDesk pricing page.
- App is otherwise fully locked — no way past the screen.

```javascript
// INJECT: expiry check
const { expiresAt, upgradeUrl } = require('./config');
if (Date.now() > expiresAt) {
  mainWindow.loadFile(path.join(__dirname, 'expired.html'));
  return; // don't load websiteUrl
}
```

**New file injected:** `expired.html` (bundled in build assets).

---

## Phase 2 — Pro Tier Modules

**Goal:** Implement all 16 Pro features as injectable modules. Each module is a self-contained code block in `ConversionService`.

### 2.1 — Module: Full Domain Whitelist / Blacklist

**Replaces** Free Tier domain lock. Config: `allowedDomains[]`, `blockedDomains[]` (regex-capable).

**Where:** `generateMainJs()`.

```javascript
// INJECT: domainWhitelistBlacklist
const { allowedDomains, blockedDomains } = require('./config');
mainWindow.webContents.on('will-navigate', (event, url) => {
  const hostname = new URL(url).hostname;
  const blocked = blockedDomains.some(b => hostname.match(b));
  const allowed = allowedDomains.length === 0 || allowedDomains.some(a => hostname.match(a));
  if (blocked || !allowed) { event.preventDefault(); }
});
mainWindow.webContents.setWindowOpenHandler(({ url }) => {
  // same check — return { action: 'deny' } or { action: 'allow' }
});
```

### 2.2 — Module: Screen Capture Protection

**Note:** Already partially present in current codebase (`setContentProtection`, screenshot shortcut blocking). This module **enhances and makes it configurable.**

**Where:** `generateMainJs()` + `generatePreloadJs()`.

**Additions:**
- `mainWindow.setContentProtection(true)` — always on if feature enabled (currently unconditional, gate it).
- Block all known screenshot/recording shortcuts (already done — ensure exhaustive cross-platform list).
- Preload overlay (already implemented) — make it configurable (custom message text, countdown seconds).
- Add `visibilitychange` listener in preload to trigger blackout when app goes to background (OBS detection heuristic).

### 2.3 — Module: Custom Watermark

**Replaces** the "Powered by WebToDesk" free watermark.

**Where:** `generatePreloadJs()`.

**Config:** `text`, `imageUrl`, `position` (`top-left | top-right | bottom-left | bottom-right | center`), `opacity` (0.0–1.0), `fontSize`, `color`.

- On `did-finish-load`, inject CSS watermark div with user-specified content.
- `imageUrl` fetched from R2 (uploaded during project config).

### 2.4 — Module: Custom Key Bindings

**Where:** `generateMainJs()`.

**Config:** `KeyBindingConfig[]` — each entry: `{ accelerator: string, action: 'reload' | 'goBack' | 'goForward' | 'toggleDevTools' | 'custom', ipcChannel?: string }`.

```javascript
// INJECT: customKeyBindings
const { keyBindings } = require('./config');
keyBindings.forEach(({ accelerator, action }) => {
  globalShortcut.register(accelerator, () => {
    if (action === 'reload') mainWindow.reload();
    else if (action === 'goBack') mainWindow.webContents.goBack();
    // etc.
  });
});
```

### 2.5 — Module: Offline Caching

**Where:** `generateMainJs()` (session configuration) + new `service-worker.js` file injected into build.

**Config:** `strategy` (`cache-first | network-first | stale-while-revalidate`), `maxAge` (seconds), `maxSize` (MB).

**Implementation:**
- Use Electron's `session.defaultSession.webRequest` + `protocol.interceptHttpProtocol` to serve cached responses.
- Alternative approach: inject a Service Worker registration script via `preload.js` that caches app shell resources.
- Bundle a `sw.js` file with the configurable caching strategy.
- `did-fail-load` handler shows offline page if cache miss and network unavailable.

### 2.6 — Module: Auto-Updates

**Where:** `generateMainJs()` + `package.json` devDependencies.

**Config:** `feedUrl` (e.g. a GitHub Releases URL or custom update server endpoint).

**Dependencies added to `package.json`:** `electron-updater`.

```javascript
// INJECT: autoUpdate
const { autoUpdater } = require('electron-updater');
autoUpdater.setFeedURL({ url: '${feedUrl}' });
app.whenReady().then(() => autoUpdater.checkForUpdatesAndNotify());
autoUpdater.on('update-available', () => { /* notify user */ });
autoUpdater.on('update-downloaded', () => autoUpdater.quitAndInstall());
```

**electron-builder config addition:**
```json
"publish": { "provider": "generic", "url": "${feedUrl}" }
```

### 2.7 — Module: Native Notifications

**Where:** `generatePreloadJs()` — expose IPC bridge; `generateMainJs()` — IPC handler using `Notification` from Electron.

**Config:** `enabled` boolean. No deep config needed — apps use the standard Web Notifications API which Electron forwards.

**Implementation:** Grant `notifications` permission in `session.setPermissionRequestHandler`, and ensure `Notification.requestPermission()` resolves to `'granted'`.

### 2.8 — Module: System Tray + Context Menu

**Where:** `generateMainJs()`.

**Config:** `TrayConfig { tooltip: string, contextMenu: MenuItem[] }` where `MenuItem` = `{ label, action: 'show' | 'quit' | 'reload' | 'separator' }`.

```javascript
// INJECT: systemTray
const { Tray, Menu } = require('electron');
const tray = new Tray(getIconPath());
tray.setToolTip('${tooltip}');
const menu = Menu.buildFromTemplate([
  { label: 'Show', click: () => mainWindow.show() },
  { type: 'separator' },
  { label: 'Quit', click: () => app.quit() }
]);
tray.setContextMenu(menu);
tray.on('double-click', () => mainWindow.show());
```

### 2.9 — Module: Dark/Light Mode Sync

**Where:** `generateMainJs()` + `generatePreloadJs()`.

**Logic:**
- Listen to `nativeTheme.on('updated')` in `main.js`.
- Send IPC message `theme-changed` with `{ isDark: nativeTheme.shouldUseDarkColors }`.
- Preload bridge exposes `onThemeChange(callback)` via `contextBridge`.
- Injects CSS class `wtd-dark` / `wtd-light` on `document.documentElement` for websites to optionally respond to.

### 2.10 — Module: Clipboard Integration

**Where:** `generatePreloadJs()` (expose API) + `generateMainJs()` (IPC handler).

**Config:** `ClipboardConfig { allowRead: boolean, allowWrite: boolean }`.

```javascript
// preload.js INJECT: clipboard
contextBridge.exposeInMainWorld('wtdClipboard', {
  read: () => ipcRenderer.invoke('clipboard-read'),
  write: (text) => ipcRenderer.invoke('clipboard-write', text)
});
```

Main process handler uses Electron's `clipboard` module with permission check.

### 2.11 — Module: Window Polish

**Where:** `generateMainJs()` — `BrowserWindow` constructor options.

**Config:** `WindowPolishConfig { blur: boolean, alwaysOnTop: boolean, frame: boolean, opacity: number, vibrancy: string }`.

- `blur: true` → `vibrancy: 'under-window'` (macOS) or `backgroundMaterial: 'acrylic'` (Windows 11).
- `alwaysOnTop: true` → `mainWindow.setAlwaysOnTop(true, 'floating')`.
- `frame: false` → frameless window with injected drag region in preload.
- `opacity` → `mainWindow.setOpacity(value)`.

### 2.12 — Module: Right-Click Disable / Custom Menu

**Where:** `generatePreloadJs()`.

**Config:** `RightClickConfig { disable: boolean, customMenuItems: MenuItem[] }`.

```javascript
// INJECT: rightClickMenu
document.addEventListener('contextmenu', (e) => {
  e.preventDefault();
  if (!disable) {
    // Send IPC to show custom Electron context menu
    ipcRenderer.send('show-context-menu', { x: e.clientX, y: e.clientY });
  }
});
```

Main process builds `Menu.buildFromTemplate(customMenuItems)` and calls `menu.popup()` on IPC trigger.

### 2.13 — Module: File System Access

**Where:** `generateMainJs()` (IPC handlers) + `generatePreloadJs()` (bridge).

**Config:** `FileSystemConfig { allowedPaths: string[], mode: 'read' | 'read-write' }`.

```javascript
// main.js INJECT: fileSystem
const { dialog, shell } = require('electron');
ipcMain.handle('fs-open-dialog', async () => {
  return dialog.showOpenDialogSync({ properties: ['openFile'] });
});
ipcMain.handle('fs-read-file', async (event, filePath) => {
  // Validate filePath is within allowedPaths before reading
  return fs.readFileSync(filePath, 'utf8');
});
```

Preload exposes `wtdFS.openDialog()`, `wtdFS.readFile(path)`, `wtdFS.writeFile(path, data)`.

### 2.14 — Module: Global Hotkeys

**Where:** `generateMainJs()`.

**Config:** `GlobalHotkeyConfig[]` — each: `{ accelerator, action, ipcChannel }`. Unlike custom key bindings, these work **even when the app window is not focused.**

```javascript
// INJECT: globalHotkeys (registered on app ready, never unregistered on blur)
app.whenReady().then(() => {
  globalHotkeys.forEach(({ accelerator, action }) => {
    globalShortcut.register(accelerator, () => { /* action */ });
  });
});
```

**Note:** Screen capture shortcuts are excluded from user-configurable global hotkeys if `screenCaptureProtection` is also enabled.

---

## Phase 3 — License & Build Enforcement (Backend)

### 3.1 — License Validation Service

New `LicenseService` bean in `conversion-service`:

- On every build request, validate: `tier`, `expiresAt`, `buildsUsed < maxBuilds`, `activeAppsCount < maxActiveApps`.
- Free: 1 active app, 1 build ever, expires 30 days from creation.
- Starter: 10 builds/month rolling window, apps expire 1 year from purchase date.
- Five-Year: 50 builds/month, apps expire 5 years from purchase.
- Lifetime: unlimited builds (soft cap at 500/month for abuse prevention), no expiry.
- Return typed `LicenseViolation` exceptions that surface to the user as clear error messages.

### 3.2 — Build Counter & Quota Tracking

- `ConversionProject` gets `buildCount` (int) + `lastBuildResetAt` (Instant).
- Monthly rolling reset: on each build, if `now - lastBuildResetAt > 30 days`, reset `buildCount = 0`.
- Quota exceeded → return `402 Payment Required` with upgrade URL in response body.

### 3.3 — Watermarked Installer (Free Tier)

Two approaches — use approach (a) as primary:

**(a) NSIS script injection (Windows):** In `generatePackageJson()` for Free tier, add custom NSIS script that appends a "Trial Version" text to installer screens and desktop shortcut name.

**(b) Binary patching:** Post-build, use a script to patch the installer header. More fragile — avoid.

### 3.4 — Upgrade Screen Asset

Bundle `expired.html` as a static resource in the conversion-service (under `src/main/resources/templates/electron/`). It is copied into the build workspace alongside `main.js`. Content: branded WebToDesk upgrade page with a CTA button pointing to the pricing page URL (injected from `config.js`).

---

## Phase 4 — Frontend UI Changes

### 4.1 — Feature Config Builder (UI)

In the `frontend` service, add a multi-step project creation wizard:

**Step 1:** Basic info (URL, title, icon upload).  
**Step 2:** Feature toggles — grouped by tier. Free features always enabled; Pro features shown but locked with upgrade CTA for Free users.  
**Step 3:** Per-feature configuration panels (e.g. splash screen logo upload, watermark text/position, tray menu editor, domain lists).  
**Step 4:** Review → Build.

### 4.2 — Tier Badge & Upgrade Prompts

- Show current tier badge in dashboard header.
- Locked Pro features show a `🔒 Pro` badge with a modal on click.
- Build quota indicator (e.g. "8 of 10 builds used this month").
- Expiry countdown for Free tier ("Trial expires in 12 days").

---

## Phase 5 — Code Architecture Refactor in ConversionService

The current `generateMainJs()` is a single large string template. Refactor to module composition:

```java
private String generateMainJs(FeatureConfig config) {
    StringBuilder sb = new StringBuilder();
    sb.append(MAIN_JS_HEADER);               // imports, app setup
    sb.append(generateWindowOptions(config)); // BrowserWindow constructor
    sb.append(MAIN_JS_LOAD_URL);             // mainWindow.loadURL(...)

    if (config.hasSplashScreen())            sb.append(MODULE_SPLASH_SCREEN.render(config));
    if (config.hasExpiryLock())              sb.append(MODULE_EXPIRY.render(config));
    if (config.hasDomainLock())              sb.append(MODULE_DOMAIN_LOCK.render(config));
    if (config.hasSystemTray())              sb.append(MODULE_TRAY.render(config));
    if (config.hasAutoUpdate())              sb.append(MODULE_AUTO_UPDATE.render(config));
    if (config.hasGlobalHotkeys())           sb.append(MODULE_GLOBAL_HOTKEYS.render(config));
    if (config.hasFileSystemAccess())        sb.append(MODULE_FILESYSTEM.render(config));
    if (config.hasDarkLightSync())           sb.append(MODULE_THEME_SYNC.render(config));
    // ... etc.

    sb.append(MAIN_JS_APP_LIFECYCLE);        // app.whenReady, window-all-closed, etc.
    return sb.toString();
}
```

Each `MODULE_*` is a static `ElectronModule` — an interface with `String render(FeatureConfig config)` — living in a `modules/` package. This makes individual modules unit-testable and independently deployable.

Same pattern for `generatePreloadJs(FeatureConfig config)`.

---

## File Structure Changes Summary

```
conversion-service/src/main/java/com/example/conversion_service/
├── entity/
│   └── ConversionProject.java          ← add featureConfig, tier, expiresAt, buildCount
├── service/
│   ├── ConversionService.java          ← refactor to module composition
│   ├── BuildService.java               ← add LicenseService check, queue routing
│   ├── LicenseService.java             ← NEW: tier validation & quota enforcement
│   └── modules/                        ← NEW: one class per feature module
│       ├── ElectronModule.java         (interface)
│       ├── SplashScreenModule.java
│       ├── DomainLockModule.java
│       ├── ExpiryLockModule.java
│       ├── WatermarkModule.java
│       ├── ScreenCaptureModule.java
│       ├── AutoUpdateModule.java
│       ├── SystemTrayModule.java
│       ├── OfflineCacheModule.java
│       ├── DarkLightSyncModule.java
│       ├── ClipboardModule.java
│       ├── FileSystemModule.java
│       ├── WindowPolishModule.java
│       ├── RightClickModule.java
│       ├── GlobalHotkeysModule.java
│       ├── CustomKeyBindingsModule.java
│       └── NativeNotificationsModule.java
├── dto/
│   ├── FeatureConfig.java              ← NEW: full feature config record
│   ├── CreateConversionRequest.java    ← update to include featureConfig
│   └── ...
└── resources/templates/electron/
    └── expired.html                    ← NEW: upgrade lock screen asset
```

---

## Implementation Order (Recommended)

1. **Entity + DTO refactor** — add `FeatureConfig`, migrate DB schema.
2. **`ElectronModule` interface + `ConversionService` refactor** — establish the composition pattern.
3. **Free Tier modules** — Splash, Domain Lock, Title Bar, Watermark, Expiry Lock, File Download.
4. **`LicenseService`** — quota enforcement and tier validation.
5. **Pro Tier — Phase A (low risk):** Dark/Light Sync, Clipboard, Window Polish, Right-Click, Custom Key Bindings, Global Hotkeys, Notifications.
6. **Pro Tier — Phase B (higher complexity):** Screen Capture Protection (enhance existing), System Tray, File System Access.
7. **Pro Tier — Phase C (infra-dependent):** Auto-Updates (needs update server), Offline Caching (needs SW strategy decision), Full Domain Lists.
8. **Build queue routing** — priority executor pools.
9. **Watermarked installer** — NSIS script injection.
10. **Frontend UI** — Feature config wizard, tier badges, upgrade prompts.

---

## Notes for Electron Specialist

- **Content Protection caveat:** `setContentProtection(true)` on Windows only hides the window from screen capture APIs that use `BitBlt` — it does NOT stop hardware-level capture. The blackout overlay in `preload.js` is the real UX deterrent. This should be clearly communicated to users.
- **`vibrancy` / `backgroundMaterial`** for window blur requires specific OS versions (macOS 10.14+, Windows 11 Build 22000+). The Window Polish module must include OS version gating.
- **Auto-updater** requires code signing for macOS (notarization) and Windows (Authenticode). The feature should be gated behind a note to the user that signing is required, or provide a self-signed fallback with a user warning.
- **Service Worker offline caching** conflicts with Electron's custom protocol handling if `loadURL` uses `https://`. Test carefully with `session.webRequest` intercepts to avoid CORS issues.
- **Global hotkeys** registration must be wrapped in try/catch — some accelerators are OS-reserved and `globalShortcut.register` returns `false` silently. Log and surface failures to the user in the build output.
- **Expiry timestamp** in `config.js` is client-readable. It is not a security mechanism — it's a UX gate. Do not market it as tamper-proof. The server-side quota enforcement is the authoritative control.

---

## Phase 6 — Complete Java Code Blueprints

### 6.1 — `ElectronModule.java` Interface

```java
package com.example.conversion_service.service.modules;

import com.example.conversion_service.dto.FeatureConfig;

/**
 * Contract for every injectable Electron feature module.
 * Each implementation returns a self-contained JavaScript code block
 * that is appended into main.js or preload.js during file generation.
 */
public interface ElectronModule {

    /**
     * Which file this module targets.
     */
    enum Target { MAIN_JS, PRELOAD_JS, PACKAGE_JSON_EXTRA, CONFIG_JS_EXTRA }

    Target target();

    /**
     * Returns the JavaScript (or JSON fragment) for this module,
     * fully rendered with values from the given FeatureConfig.
     * Returns empty string if the module is not applicable/enabled.
     */
    String render(FeatureConfig config);

    /**
     * Whether this module should be included given the config.
     * ConversionService calls this before render() to short-circuit.
     */
    boolean isEnabled(FeatureConfig config);
}
```

---

### 6.2 — `FeatureConfig.java` DTO (complete)

```java
package com.example.conversion_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;

/**
 * Serialized as JSON into the feature_config column of ConversionProject.
 * Every nested config class is nullable — absence means "disabled".
 */
@Data
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeatureConfig {

    public enum Tier { FREE, STARTER, FIVE_YEAR, LIFETIME }

    private Tier tier;

    // ── Free Tier ─────────────────────────────────────────
    private SplashScreenConfig splashScreen;
    private TitleBarConfig titleBar;
    private DomainLockConfig domainLock;       // Free: base URL only; Pro: full lists
    private boolean fileDownloadEnabled;
    private ExpiryConfig expiry;
    private WatermarkConfig watermark;

    // ── Pro Tier ──────────────────────────────────────────
    private boolean screenCaptureProtectionEnabled;
    private List<KeyBindingConfig> keyBindings;
    private OfflineCacheConfig offlineCache;
    private AutoUpdateConfig autoUpdate;
    private boolean nativeNotificationsEnabled;
    private TrayConfig systemTray;
    private boolean darkLightSyncEnabled;
    private ClipboardConfig clipboard;
    private WindowPolishConfig windowPolish;
    private RightClickConfig rightClick;
    private FileSystemConfig fileSystem;
    private List<GlobalHotkeyConfig> globalHotkeys;

    // ── Convenience helpers ───────────────────────────────
    public boolean isFree()     { return tier == Tier.FREE; }
    public boolean isPro()      { return tier != null && tier != Tier.FREE; }
    public boolean hasSplash()  { return splashScreen != null; }
    public boolean hasTray()    { return systemTray != null; }
    public boolean hasExpiry()  { return expiry != null && expiry.getExpiresAt() != null; }
    public boolean hasDomainLock() { return domainLock != null; }
    public boolean hasAutoUpdate() { return autoUpdate != null && autoUpdate.getFeedUrl() != null; }
    public boolean hasOfflineCache() { return offlineCache != null; }
    public boolean hasWindowPolish() { return windowPolish != null; }
    public boolean hasRightClick()   { return rightClick != null; }
    public boolean hasFileSystem()   { return fileSystem != null; }
    public boolean hasClipboard()    { return clipboard != null; }
    public boolean hasGlobalHotkeys(){ return globalHotkeys != null && !globalHotkeys.isEmpty(); }
    public boolean hasKeyBindings()  { return keyBindings != null && !keyBindings.isEmpty(); }

    // ── Nested config classes ─────────────────────────────

    @Data @Builder @Jacksonized
    public static class SplashScreenConfig {
        private String logoUrl;       // R2 public URL of user logo image
        private boolean showOurLogo;  // true = show WebToDesk branding alongside
        private int durationMs;       // default 2500
    }

    @Data @Builder @Jacksonized
    public static class TitleBarConfig {
        private boolean enabled;      // false = frameless
        private String text;          // custom title text
        private String style;         // "default" | "hidden" | "hiddenInset" (macOS)
    }

    @Data @Builder @Jacksonized
    public static class DomainLockConfig {
        private List<String> allowedDomains;  // regex-capable for Pro
        private List<String> blockedDomains;  // Pro only
        private String blockMessage;          // shown to user on blocked nav
    }

    @Data @Builder @Jacksonized
    public static class ExpiryConfig {
        private Instant expiresAt;
        private String lockMessage;
        private String upgradeUrl;
    }

    @Data @Builder @Jacksonized
    public static class WatermarkConfig {
        private String text;
        private String imageUrl;      // null = text-only
        private String position;      // "bottom-right" | "bottom-left" | "top-right" | "top-left" | "center"
        private double opacity;       // 0.0–1.0
        private String color;
        private int fontSize;
        private boolean useOurBranding; // true = override all above with WebToDesk defaults
    }

    @Data @Builder @Jacksonized
    public static class KeyBindingConfig {
        private String accelerator;
        private String action;        // "reload" | "goBack" | "goForward" | "zoomIn" | "zoomOut" | "ipc"
        private String ipcChannel;    // used when action = "ipc"
    }

    @Data @Builder @Jacksonized
    public static class OfflineCacheConfig {
        private String strategy;      // "cache-first" | "network-first" | "stale-while-revalidate"
        private int maxAgeSeconds;
        private int maxSizeMb;
    }

    @Data @Builder @Jacksonized
    public static class AutoUpdateConfig {
        private String feedUrl;
        private boolean silent;       // false = show update dialog
        private boolean autoInstall;  // true = install on quit automatically
    }

    @Data @Builder @Jacksonized
    public static class TrayConfig {
        private String tooltip;
        private List<TrayMenuItem> contextMenu;
    }

    @Data @Builder @Jacksonized
    public static class TrayMenuItem {
        private String label;
        private String action;        // "show" | "hide" | "reload" | "quit" | "separator"
        private String ipcChannel;
    }

    @Data @Builder @Jacksonized
    public static class ClipboardConfig {
        private boolean allowRead;
        private boolean allowWrite;
    }

    @Data @Builder @Jacksonized
    public static class WindowPolishConfig {
        private boolean blur;
        private boolean alwaysOnTop;
        private boolean frame;
        private double opacity;       // 0.0–1.0, 1.0 = fully opaque
        private String vibrancy;      // macOS: "under-window" | "sidebar" | "titlebar"
    }

    @Data @Builder @Jacksonized
    public static class RightClickConfig {
        private boolean disable;
        private List<RightClickMenuItem> customMenuItems;
    }

    @Data @Builder @Jacksonized
    public static class RightClickMenuItem {
        private String label;
        private String action;
        private String ipcChannel;
    }

    @Data @Builder @Jacksonized
    public static class FileSystemConfig {
        private List<String> allowedPaths;
        private String mode;          // "read" | "read-write"
    }

    @Data @Builder @Jacksonized
    public static class GlobalHotkeyConfig {
        private String accelerator;
        private String action;
        private String ipcChannel;
    }
}
```

---

### 6.3 — `LicenseService.java` (complete)

```java
package com.example.conversion_service.service;

import com.example.conversion_service.dto.FeatureConfig;
import com.example.conversion_service.entity.ConversionProject;
import com.example.conversion_service.exception.LicenseViolationException;
import com.example.conversion_service.repository.ConversionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseService {

    private static final int FREE_MAX_ACTIVE_APPS   = 1;
    private static final int FREE_TRIAL_DAYS        = 30;
    private static final int STARTER_BUILDS_PER_MO  = 10;
    private static final int FIVE_YEAR_BUILDS_PER_MO= 50;
    private static final int LIFETIME_SOFT_CAP_PER_MO = 500; // fair-use

    private final ConversionRepository repository;

    /**
     * Called by BuildService BEFORE triggering a build.
     * Throws LicenseViolationException with a user-facing message on any violation.
     */
    public void validateBuildAllowed(ConversionProject project) {
        FeatureConfig config = project.getFeatureConfig();
        if (config == null) {
            // Legacy project with no featureConfig — treat as FREE
            config = FeatureConfig.builder().tier(FeatureConfig.Tier.FREE).build();
        }

        switch (config.getTier()) {
            case FREE    -> validateFree(project, config);
            case STARTER -> validateQuota(project, STARTER_BUILDS_PER_MO, "Starter");
            case FIVE_YEAR -> validateQuota(project, FIVE_YEAR_BUILDS_PER_MO, "Five-Year");
            case LIFETIME  -> validateQuota(project, LIFETIME_SOFT_CAP_PER_MO, "Lifetime");
        }
    }

    /**
     * Called when creating a new project, to enforce active app limits.
     */
    public void validateProjectCreationAllowed(String userEmail, FeatureConfig.Tier tier) {
        if (tier == FeatureConfig.Tier.FREE) {
            long activeCount = repository.countActiveByUser(userEmail);
            if (activeCount >= FREE_MAX_ACTIVE_APPS) {
                throw new LicenseViolationException(
                    "Free tier is limited to " + FREE_MAX_ACTIVE_APPS + " active app. " +
                    "Upgrade to create more apps.");
            }
        }
    }

    // ── Private validators ────────────────────────────────

    private void validateFree(ConversionProject project, FeatureConfig config) {
        // Check app-level expiry
        if (config.hasExpiry() && config.getExpiry().getExpiresAt() != null) {
            if (Instant.now().isAfter(config.getExpiry().getExpiresAt())) {
                throw new LicenseViolationException(
                    "Your free trial has expired. Please upgrade to continue building.");
            }
        } else {
            // Enforce 30-day window from project creation if no explicit expiry set
            Instant trialEnd = project.getCreatedAt().plus(FREE_TRIAL_DAYS, ChronoUnit.DAYS);
            if (Instant.now().isAfter(trialEnd)) {
                throw new LicenseViolationException(
                    "Your 30-day free trial has expired. Upgrade to rebuild or extend your app.");
            }
        }
        // Free tier only gets 1 build total (the initial build)
        if (project.getBuildCount() >= 1) {
            throw new LicenseViolationException(
                "Free tier allows 1 build. Upgrade to Starter or higher to rebuild.");
        }
    }

    private void validateQuota(ConversionProject project, int monthlyLimit, String tierName) {
        resetMonthlyCounterIfNeeded(project);
        if (project.getBuildCount() >= monthlyLimit) {
            throw new LicenseViolationException(
                tierName + " plan allows " + monthlyLimit + " builds/month. " +
                "You have used all builds for this period. " +
                "Extra builds are available as pay-per-use add-ons.");
        }
    }

    /**
     * Resets build counter if a new calendar month has started since last reset.
     * Mutates project in place — caller must save.
     */
    public void resetMonthlyCounterIfNeeded(ConversionProject project) {
        Instant lastReset = project.getLastBuildResetAt();
        if (lastReset == null || Instant.now().isAfter(lastReset.plus(30, ChronoUnit.DAYS))) {
            project.setBuildCount(0);
            project.setLastBuildResetAt(Instant.now());
        }
    }

    /** Increments build counter after a successful build. Caller must save. */
    public void incrementBuildCount(ConversionProject project) {
        project.setBuildCount(project.getBuildCount() + 1);
    }
}
```

---

### 6.4 — `ConversionProject.java` Entity (updated fields)

Add the following fields to the existing `ConversionProject` entity class:

```java
// ── Tier & Licensing ─────────────────────────────────────
@Enumerated(EnumType.STRING)
@Column(nullable = false)
@Builder.Default
private FeatureConfig.Tier tier = FeatureConfig.Tier.FREE;

@Column(columnDefinition = "TEXT")
private String featureConfigJson;  // Jackson-serialized FeatureConfig

@Column
private Instant expiresAt;         // denormalized from featureConfig for quick queries

@Column
@Builder.Default
private int buildCount = 0;

@Column
private Instant lastBuildResetAt;

// ── Transient helper (not persisted) ─────────────────────
@Transient
private FeatureConfig featureConfig; // deserialized on-demand via @PostLoad

@PostLoad
public void deserializeFeatureConfig() {
    if (featureConfigJson != null) {
        try {
            this.featureConfig = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .readValue(featureConfigJson, FeatureConfig.class);
        } catch (Exception e) {
            this.featureConfig = FeatureConfig.builder()
                .tier(this.tier)
                .build();
        }
    }
}

@PrePersist @PreUpdate
public void serializeFeatureConfig() {
    if (featureConfig != null) {
        try {
            this.featureConfigJson = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .writeValueAsString(featureConfig);
            this.tier = featureConfig.getTier();
            if (featureConfig.hasExpiry()) {
                this.expiresAt = featureConfig.getExpiry().getExpiresAt();
            }
        } catch (Exception e) {
            // log and continue — don't fail a save due to serialization
        }
    }
}
```

---

### 6.5 — `BuildService.java` Changes (diff-style)

Add these two calls inside `triggerBuild()` immediately after loading the project:

```java
// AFTER: project.setStatus(ConversionStatus.BUILDING);
// ADD:
licenseService.validateBuildAllowed(project);          // throws on violation
licenseService.resetMonthlyCounterIfNeeded(project);   // resets counter if new month
repository.save(project);
```

And after `uploadArtifact()` succeeds:

```java
// ADD after project.setStatus(ConversionStatus.READY):
licenseService.incrementBuildCount(project);
repository.save(project);
```

Add priority queue routing:

```java
// NEW method — replaces single @Async("buildExecutor") annotation approach
public void dispatchBuild(String projectId) {
    ConversionProject project = repository.findById(projectId)
        .orElseThrow(() -> new ProjectNotFoundException(projectId));
    if (project.getFeatureConfig() != null && project.getFeatureConfig().isPro()) {
        proBuildExecutor.execute(() -> triggerBuild(projectId));
    } else {
        freeBuildExecutor.execute(() -> triggerBuild(projectId));
    }
}
```

---

### 6.6 — `AsyncConfig.java` (new — thread pool configuration)

```java
package com.example.conversion_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    /** Free tier: 1 concurrent build, queue up to 50 */
    @Bean("freeBuildExecutor")
    public Executor freeBuildExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(2);
        exec.setQueueCapacity(50);
        exec.setThreadNamePrefix("build-free-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(300);
        exec.initialize();
        return exec;
    }

    /** Pro tiers: 5 concurrent builds, minimal queue */
    @Bean("proBuildExecutor")
    public Executor proBuildExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(5);
        exec.setMaxPoolSize(10);
        exec.setQueueCapacity(5);
        exec.setThreadNamePrefix("build-pro-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(300);
        exec.initialize();
        return exec;
    }
}
```

---

## Phase 7 — Complete Electron Module Code

### 7.1 — `SplashScreenModule.java`

```java
public class SplashScreenModule implements ElectronModule {

    @Override public Target target() { return Target.MAIN_JS; }
    @Override public boolean isEnabled(FeatureConfig c) { return c.hasSplash(); }

    @Override
    public String render(FeatureConfig c) {
        var splash = c.getSplashScreen();
        int duration = splash.getDurationMs() > 0 ? splash.getDurationMs() : 2500;
        String ourLogoHtml = splash.isShowOurLogo()
            ? "<div style='position:absolute;bottom:10px;right:10px;font-size:11px;color:#888;font-family:sans-serif;'>Powered by WebToDesk</div>"
            : "";
        String logoHtml = splash.getLogoUrl() != null
            ? "<img src='" + splash.getLogoUrl() + "' style='max-width:200px;max-height:120px;object-fit:contain;'/>"
            : "<div style='font-size:24px;font-weight:bold;color:#333;font-family:sans-serif;'>Loading...</div>";

        return """
            // ── SPLASH SCREEN MODULE ──────────────────────────────
            let splashWindow = null;
            function createSplashWindow() {
              splashWindow = new BrowserWindow({
                width: 400, height: 300,
                frame: false, transparent: true,
                alwaysOnTop: true, skipTaskbar: true,
                webPreferences: { nodeIntegration: false, contextIsolation: true }
              });
              splashWindow.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(`
                <!DOCTYPE html><html><body style="margin:0;background:transparent;display:flex;
                  align-items:center;justify-content:center;height:100vh;flex-direction:column;
                  background:#fff;border-radius:12px;box-shadow:0 8px 32px rgba(0,0,0,0.18);">
                  %s%s
                </body></html>
              `)}`.replace(/%s/g,''));
            }
            // Close splash after %d ms OR when main window finishes loading
            const splashTimer = setTimeout(() => splashWindow?.close(), %d);
            mainWindow.webContents.once('did-finish-load', () => {
              clearTimeout(splashTimer);
              setTimeout(() => splashWindow?.close(), 300);
            });
            // ─────────────────────────────────────────────────────
            """.formatted(logoHtml, ourLogoHtml, duration, duration);
    }
}
```

---

### 7.2 — `ExpiryLockModule.java`

```java
public class ExpiryLockModule implements ElectronModule {

    @Override public Target target() { return Target.MAIN_JS; }
    @Override public boolean isEnabled(FeatureConfig c) { return c.isFree() && c.hasExpiry(); }

    @Override
    public String render(FeatureConfig c) {
        var expiry = c.getExpiry();
        long expiresEpochMs = expiry.getExpiresAt().toEpochMilli();
        String upgradeUrl = expiry.getUpgradeUrl() != null
            ? expiry.getUpgradeUrl() : "https://webtodesk.com/pricing";

        return """
            // ── EXPIRY LOCK MODULE ────────────────────────────────
            const APP_EXPIRES_AT = %dL; // epoch ms
            const UPGRADE_URL    = '%s';
            if (Date.now() > APP_EXPIRES_AT) {
              mainWindow.loadFile(path.join(__dirname, 'expired.html'));
              mainWindow.once('ready-to-show', () => mainWindow.show());
              return; // DO NOT load the website URL
            }
            // ─────────────────────────────────────────────────────
            """.formatted(expiresEpochMs, upgradeUrl);
    }
}
```

---

### 7.3 — `DomainLockModule.java`

```java
public class DomainLockModule implements ElectronModule {

    @Override public Target target() { return Target.MAIN_JS; }
    @Override public boolean isEnabled(FeatureConfig c) { return c.hasDomainLock(); }

    @Override
    public String render(FeatureConfig c) {
        var lock = c.getDomainLock();
        boolean isFree = c.isFree();

        // Free tier: only base URL allowed — derive hostname from websiteUrl
        String allowedDomainsJs = isFree
            ? "const ALLOWED_DOMAINS = [new URL(websiteUrl).hostname];"
            : "const ALLOWED_DOMAINS = " + toJsArray(lock.getAllowedDomains()) + ";";

        String blockedDomainsJs = isFree
            ? "const BLOCKED_DOMAINS = [];"
            : "const BLOCKED_DOMAINS = " + toJsArray(lock.getBlockedDomains()) + ";";

        String blockMsg = lock.getBlockMessage() != null
            ? lock.getBlockMessage() : "External navigation is restricted in this app.";

        return """
            // ── DOMAIN LOCK MODULE ────────────────────────────────
            %s
            %s
            function isDomainAllowed(urlStr) {
              try {
                const host = new URL(urlStr).hostname;
                if (BLOCKED_DOMAINS.some(b => new RegExp(b).test(host))) return false;
                if (ALLOWED_DOMAINS.length === 0) return true;
                return ALLOWED_DOMAINS.some(a => new RegExp(a).test(host));
              } catch { return false; }
            }
            mainWindow.webContents.on('will-navigate', (event, url) => {
              if (!isDomainAllowed(url)) {
                event.preventDefault();
                mainWindow.webContents.executeJavaScript(
                  `window.__wtdToast && window.__wtdToast('%s')` );
              }
            });
            mainWindow.webContents.setWindowOpenHandler(({ url }) => {
              if (isDomainAllowed(url)) return { action: 'allow' };
              return { action: 'deny' };
            });
            // ─────────────────────────────────────────────────────
            """.formatted(allowedDomainsJs, blockedDomainsJs, blockMsg.replace("'", "\\'"));
    }

    private String toJsArray(java.util.List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        return "[" + list.stream()
            .map(s -> "'" + s.replace("'", "\\'") + "'")
            .collect(java.util.stream.Collectors.joining(",")) + "]";
    }
}
```

---

### 7.4 — `SystemTrayModule.java`

```java
public class SystemTrayModule implements ElectronModule {

    @Override public Target target() { return Target.MAIN_JS; }
    @Override public boolean isEnabled(FeatureConfig c) { return c.hasTray(); }

    @Override
    public String render(FeatureConfig c) {
        var tray = c.getSystemTray();
        String tooltip = tray.getTooltip() != null ? tray.getTooltip() : "App";

        StringBuilder menuItems = new StringBuilder();
        if (tray.getContextMenu() != null) {
            for (var item : tray.getContextMenu()) {
                if ("separator".equals(item.getAction())) {
                    menuItems.append("{ type: 'separator' },\n");
                } else {
                    String clickAction = switch (item.getAction()) {
                        case "show"   -> "mainWindow.show()";
                        case "hide"   -> "mainWindow.hide()";
                        case "reload" -> "mainWindow.reload()";
                        case "quit"   -> "app.quit()";
                        default       -> "mainWindow.webContents.send('" + item.getIpcChannel() + "')";
                    };
                    menuItems.append("{ label: '").append(item.getLabel())
                             .append("', click: () => ").append(clickAction).append(" },\n");
                }
            }
        } else {
            menuItems.append("""
                { label: 'Show', click: () => mainWindow.show() },
                { type: 'separator' },
                { label: 'Quit', click: () => app.quit() },
                """);
        }

        return """
            // ── SYSTEM TRAY MODULE ────────────────────────────────
            const { Tray, Menu } = require('electron');
            let appTray = null;
            app.whenReady().then(() => {
              appTray = new Tray(getIconPath());
              appTray.setToolTip('%s');
              const trayMenu = Menu.buildFromTemplate([
                %s
              ]);
              appTray.setContextMenu(trayMenu);
              appTray.on('double-click', () => {
                mainWindow.isVisible() ? mainWindow.focus() : mainWindow.show();
              });
            });
            // Minimize to tray instead of closing
            mainWindow.on('close', (event) => {
              if (!app.isQuitting) {
                event.preventDefault();
                mainWindow.hide();
              }
            });
            app.on('before-quit', () => { app.isQuitting = true; });
            // ─────────────────────────────────────────────────────
            """.formatted(tooltip.replace("'", "\\'"), menuItems);
    }
}
```

---

### 7.5 — `AutoUpdateModule.java`

```java
public class AutoUpdateModule implements ElectronModule {

    @Override public Target target() { return Target.MAIN_JS; }
    @Override public boolean isEnabled(FeatureConfig c) { return c.hasAutoUpdate(); }

    @Override
    public String render(FeatureConfig c) {
        var update = c.getAutoUpdate();
        boolean silent     = update.isSilent();
        boolean autoInstall = update.isAutoInstall();

        return """
            // ── AUTO-UPDATE MODULE ────────────────────────────────
            // Requires: electron-updater in package.json
            const { autoUpdater } = require('electron-updater');
            autoUpdater.setFeedURL({ url: '%s' });
            autoUpdater.logger = require('electron').app ? null : console;
            autoUpdater.autoDownload = true;
            autoUpdater.autoInstallOnAppQuit = %b;
            app.whenReady().then(() => {
              autoUpdater.checkForUpdatesAndNotify().catch(e =>
                console.warn('Update check failed:', e.message));
            });
            autoUpdater.on('update-available', (info) => {
              %s
            });
            autoUpdater.on('update-downloaded', () => {
              %s
            });
            // ─────────────────────────────────────────────────────
            """.formatted(
                update.getFeedUrl(),
                autoInstall,
                silent ? "// silent — no dialog" :
                    "dialog.showMessageBox({ type:'info', title:'Update Available', message:`Version ${info.version} is available. It will be installed on restart.` });",
                autoInstall ? "autoUpdater.quitAndInstall(true, true);" :
                    silent ? "// silent — user will get it on next quit" :
                    "dialog.showMessageBox({ type:'info', title:'Update Ready', message:'Restart to apply the update.', buttons:['Restart Now','Later'] }).then(r => { if (r.response === 0) autoUpdater.quitAndInstall(); });"
            );
    }
}
```

---

### 7.6 — `OfflineCacheModule.java`

```java
public class OfflineCacheModule implements ElectronModule {

    @Override public Target target() { return Target.PRELOAD_JS; }
    @Override public boolean isEnabled(FeatureConfig c) { return c.hasOfflineCache(); }

    @Override
    public String render(FeatureConfig c) {
        var cache = c.getOfflineCache();
        String strategy = cache.getStrategy() != null ? cache.getStrategy() : "network-first";
        int maxAge = cache.getMaxAgeSeconds() > 0 ? cache.getMaxAgeSeconds() : 86400;

        return """
            // ── OFFLINE CACHE MODULE (Service Worker) ─────────────
            (function registerOfflineSW() {
              if (!('serviceWorker' in navigator)) return;
              const swCode = `
                const CACHE = 'wtd-offline-v1';
                const MAX_AGE = %d; // seconds
                self.addEventListener('install', e => {
                  e.waitUntil(caches.open(CACHE));
                  self.skipWaiting();
                });
                self.addEventListener('activate', e => {
                  e.waitUntil(clients.claim());
                });
                self.addEventListener('fetch', e => {
                  if (e.request.method !== 'GET') return;
                  %s
                });
              `;
              const blob = new Blob([swCode], { type: 'application/javascript' });
              const url  = URL.createObjectURL(blob);
              navigator.serviceWorker.register(url).catch(console.warn);
            })();
            // ─────────────────────────────────────────────────────
            """.formatted(maxAge, buildFetchStrategy(strategy));
    }

    private String buildFetchStrategy(String strategy) {
        return switch (strategy) {
            case "cache-first" -> """
                e.respondWith(caches.match(e.request).then(cached => {
                  if (cached) return cached;
                  return fetch(e.request).then(resp => {
                    const clone = resp.clone();
                    caches.open(CACHE).then(c => c.put(e.request, clone));
                    return resp;
                  }).catch(() => new Response('Offline', { status: 503 }));
                }));""";
            case "stale-while-revalidate" -> """
                e.respondWith(caches.open(CACHE).then(async cache => {
                  const cached = await cache.match(e.request);
                  const fresh  = fetch(e.request).then(r => { cache.put(e.request, r.clone()); return r; });
                  return cached || fresh;
                }));""";
            default -> // network-first
                """
                e.respondWith(fetch(e.request).then(resp => {
                  const clone = resp.clone();
                  caches.open(CACHE).then(c => c.put(e.request, clone));
                  return resp;
                }).catch(() => caches.match(e.request)
                  .then(c => c || new Response('Offline', { status: 503 }))));""";
        };
    }
}
```

---

### 7.7 — `WatermarkModule.java`

```java
public class WatermarkModule implements ElectronModule {

    @Override public Target target() { return Target.PRELOAD_JS; }
    @Override public boolean isEnabled(FeatureConfig c) { return c.getWatermark() != null; }

    @Override
    public String render(FeatureConfig c) {
        var wm = c.getWatermark();

        // Free tier always gets our branding
        if (wm.isUseOurBranding() || c.isFree()) {
            return """
                // ── WATERMARK MODULE (WebToDesk Brand) ────────────────
                (function injectWatermark() {
                  const injectWtdMark = () => {
                    if (document.getElementById('__wtd_watermark')) return;
                    const el = document.createElement('div');
                    el.id = '__wtd_watermark';
                    el.innerText = 'Powered by WebToDesk';
                    el.style.cssText = `position:fixed;bottom:10px;right:12px;
                      font-size:11px;color:rgba(120,120,120,0.5);
                      font-family:sans-serif;pointer-events:none;
                      z-index:2147483647;user-select:none;`;
                    document.body.appendChild(el);
                  };
                  document.readyState === 'loading'
                    ? document.addEventListener('DOMContentLoaded', injectWtdMark)
                    : injectWtdMark();
                })();
                // ─────────────────────────────────────────────────────
                """;
        }

        // Pro: custom watermark
        String position = wm.getPosition() != null ? wm.getPosition() : "bottom-right";
        String positionCss = switch (position) {
            case "top-left"     -> "top:10px;left:12px;";
            case "top-right"    -> "top:10px;right:12px;";
            case "bottom-left"  -> "bottom:10px;left:12px;";
            case "center"       -> "top:50%;left:50%;transform:translate(-50%,-50%);";
            default             -> "bottom:10px;right:12px;";
        };
        String color   = wm.getColor()    != null ? wm.getColor()    : "#888888";
        double opacity = wm.getOpacity()  > 0     ? wm.getOpacity()  : 0.15;
        int fontSize   = wm.getFontSize() > 0     ? wm.getFontSize() : 12;
        String content = wm.getImageUrl() != null
            ? "<img src='" + wm.getImageUrl() + "' style='max-height:32px;opacity:" + opacity + ";'/>"
            : wm.getText();

        return """
            // ── WATERMARK MODULE (Custom) ─────────────────────────
            (function injectWatermark() {
              const injectMark = () => {
                if (document.getElementById('__wtd_watermark')) return;
                const el = document.createElement('div');
                el.id = '__wtd_watermark';
                el.innerHTML = `%s`;
                el.style.cssText = `position:fixed;%sfont-size:%dpx;
                  color:%s;opacity:%s;font-family:sans-serif;
                  pointer-events:none;z-index:2147483647;user-select:none;`;
                document.body.appendChild(el);
              };
              document.readyState === 'loading'
                ? document.addEventListener('DOMContentLoaded', injectMark)
                : injectMark();
            })();
            // ─────────────────────────────────────────────────────
            """.formatted(content, positionCss, fontSize, color, opacity);
    }
}
```

---

### 7.8 — `DarkLightSyncModule.java`

```java
public class DarkLightSyncModule implements ElectronModule {

    @Override public Target target() { return Target.MAIN_JS; }
    @Override public boolean isEnabled(FeatureConfig c) { return c.isDarkLightSyncEnabled(); }

    @Override
    public String render(FeatureConfig c) {
        return """
            // ── DARK/LIGHT SYNC MODULE ────────────────────────────
            const { nativeTheme } = require('electron');
            function sendTheme() {
              if (!mainWindow?.isDestroyed()) {
                mainWindow.webContents.send('wtd-theme-changed', {
                  isDark: nativeTheme.shouldUseDarkColors,
                  theme: nativeTheme.shouldUseDarkColors ? 'dark' : 'light'
                });
              }
            }
            nativeTheme.on('updated', sendTheme);
            mainWindow.webContents.once('did-finish-load', sendTheme);
            // ─────────────────────────────────────────────────────
            """;
    }
}
```

Corresponding `preload.js` injection (add to `PreloadDarkLightSyncModule`):

```javascript
// ── DARK/LIGHT SYNC PRELOAD BRIDGE ──────────────────────
ipcRenderer.on('wtd-theme-changed', (_, { isDark, theme }) => {
  document.documentElement.setAttribute('data-theme', theme);
  document.documentElement.classList.toggle('wtd-dark',  isDark);
  document.documentElement.classList.toggle('wtd-light', !isDark);
  window.dispatchEvent(new CustomEvent('wtd-theme-changed', { detail: { isDark, theme } }));
});
// ─────────────────────────────────────────────────────────
```

---

### 7.9 — `WindowPolishModule.java`

```java
public class WindowPolishModule implements ElectronModule {

    @Override public Target target() { return Target.MAIN_JS; }
    @Override public boolean isEnabled(FeatureConfig c) { return c.hasWindowPolish(); }

    @Override
    public String render(FeatureConfig c) {
        var wp = c.getWindowPolish();
        StringBuilder sb = new StringBuilder();
        sb.append("// ── WINDOW POLISH MODULE ────────────────────────────\n");

        if (wp.isAlwaysOnTop()) {
            sb.append("mainWindow.setAlwaysOnTop(true, 'floating');\n");
        }
        if (wp.getOpacity() > 0 && wp.getOpacity() < 1.0) {
            sb.append("mainWindow.setOpacity(").append(wp.getOpacity()).append(");\n");
        }
        if (wp.isBlur()) {
            sb.append("""
                // Blur/vibrancy (OS-gated)
                if (process.platform === 'darwin') {
                  mainWindow.setVibrancy('%s');
                } else if (process.platform === 'win32') {
                  // Windows 11 Build 22000+ only
                  try { mainWindow.setBackgroundMaterial('acrylic'); } catch (_) {}
                }
                """.formatted(
                    wp.getVibrancy() != null ? wp.getVibrancy() : "under-window"));
        }
        sb.append("// ─────────────────────────────────────────────────────\n");
        return sb.toString();
    }
}
```

---

### 7.10 — `package.json` Extra for Auto-Updates (`PackageJsonAutoUpdateFragment`)

Add this to `generatePackageJson()` when `autoUpdate` is enabled:

```json
"dependencies": {
  "electron-updater": "^6.3.4"
},
"build": {
  ...existing...,
  "publish": {
    "provider": "generic",
    "url": "${feedUrl}"
  }
}
```

**Implementation note in `ConversionService.generatePackageJson()`:** Parse the base JSON, merge the extra dependency + publish config using Jackson `ObjectNode` merging — do not string-concatenate JSON.

---

## Phase 8 — `expired.html` Asset (full)

Save to `conversion-service/src/main/resources/templates/electron/expired.html`.  
Copied into build workspace as `expired.html` by `BuildService.writeElectronFiles()`.

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <title>Trial Expired</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      background: linear-gradient(135deg, #0f0c29, #302b63, #24243e);
      color: #fff; height: 100vh;
      display: flex; align-items: center; justify-content: center;
    }
    .card {
      background: rgba(255,255,255,0.05);
      backdrop-filter: blur(12px);
      border: 1px solid rgba(255,255,255,0.1);
      border-radius: 20px;
      padding: 48px 56px;
      text-align: center;
      max-width: 480px;
    }
    .icon { font-size: 56px; margin-bottom: 16px; }
    h1 { font-size: 28px; font-weight: 700; margin-bottom: 8px; }
    p  { color: rgba(255,255,255,0.65); font-size: 15px; line-height: 1.6; margin-bottom: 32px; }
    .btn {
      display: inline-block;
      background: linear-gradient(135deg, #667eea, #764ba2);
      color: #fff; text-decoration: none;
      padding: 14px 36px; border-radius: 50px;
      font-size: 16px; font-weight: 600;
      cursor: pointer; border: none;
      transition: transform 0.15s, box-shadow 0.15s;
    }
    .btn:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(102,126,234,0.5); }
    .brand { margin-top: 32px; font-size: 12px; color: rgba(255,255,255,0.3); }
  </style>
</head>
<body>
  <div class="card">
    <div class="icon">⏰</div>
    <h1>Your trial has expired</h1>
    <p>Your 30-day free trial of this app has ended.<br/>
       Upgrade to keep using it with full features and no time limit.</p>
    <button class="btn" onclick="openUpgrade()">Upgrade Now</button>
    <div class="brand">Powered by WebToDesk</div>
  </div>
  <script>
    // Loaded in Electron — use IPC bridge to open browser
    function openUpgrade() {
      if (window.electronAPI?.openExternal) {
        window.electronAPI.openExternal(
          typeof UPGRADE_URL !== 'undefined' ? UPGRADE_URL : 'https://webtodesk.com/pricing'
        );
      } else {
        window.open('https://webtodesk.com/pricing', '_blank');
      }
    }
  </script>
</body>
</html>
```

---

## Phase 9 — NSIS Watermark Script (Free Tier)

Add the following to the `win` block of `package.json` when tier is `FREE`:

```json
"win": {
  "target": "nsis",
  "icon": "build/${iconFile}",
  "artifactName": "${productName}-Trial-Setup.${ext}"
},
"nsis": {
  "installerHeaderIcon": "build/${iconFile}",
  "installerIcon": "build/${iconFile}",
  "installerSidebar": "build/sidebar.bmp",
  "uninstallerSidebar": "build/sidebar.bmp",
  "include": "build/installer.nsh"
}
```

Create `build/installer.nsh` (injected into workspace):

```nsis
!macro customHeader
  !system "echo Powered by WebToDesk > NUL"
!macroend

!macro customInstall
  ; Add trial notice to start menu shortcut name
  CreateShortCut "$SMPROGRAMS\${PRODUCT_NAME} (Trial).lnk" "$INSTDIR\${PRODUCT_FILENAME}.exe"
!macroend

!macro customUnInstall
  Delete "$SMPROGRAMS\${PRODUCT_NAME} (Trial).lnk"
!macroend
```

**Java implementation in `ConversionService`:** When tier is `FREE`, copy `installer.nsh` template from `src/main/resources/templates/electron/` into `workspace/build/installer.nsh` during `writeElectronFiles()`.

---

## Phase 10 — Testing Strategy

### 10.1 — Unit Tests (per module)

Each `ElectronModule` implementation gets its own test class. Pattern:

```java
@Test
void splashScreen_freeWithOurLogo_includesWtdBranding() {
    FeatureConfig config = FeatureConfig.builder()
        .tier(Tier.FREE)
        .splashScreen(SplashScreenConfig.builder()
            .logoUrl("https://r2.example.com/logo.png")
            .showOurLogo(true)
            .durationMs(2500)
            .build())
        .build();
    String code = new SplashScreenModule().render(config);
    assertThat(code).contains("Powered by WebToDesk");
    assertThat(code).contains("https://r2.example.com/logo.png");
    assertThat(code).contains("2500");
}

@Test
void expiryLock_expired_loadsExpiredHtml() {
    FeatureConfig config = FeatureConfig.builder()
        .tier(Tier.FREE)
        .expiry(ExpiryConfig.builder()
            .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .upgradeUrl("https://webtodesk.com/pricing")
            .build())
        .build();
    String code = new ExpiryLockModule().render(config);
    assertThat(code).contains("loadFile");
    assertThat(code).contains("expired.html");
}
```

### 10.2 — Integration Tests

`BuildServiceIntegrationTest`:
- Mock R2 storage service.
- Provide a test project with each module enabled.
- Verify generated file content via `generateFiles()` output before actual `npm` execution.
- Assert each module's code block is present/absent based on tier.

### 10.3 — License Service Tests

```java
@Test
void freeTier_secondBuildBlocked() {
    var project = testProject(Tier.FREE);
    project.setBuildCount(1); // already used the one free build
    assertThrows(LicenseViolationException.class,
        () -> licenseService.validateBuildAllowed(project));
}

@Test
void starterTier_11thBuildInMonth_blocked() {
    var project = testProject(Tier.STARTER);
    project.setBuildCount(10);
    project.setLastBuildResetAt(Instant.now().minus(5, ChronoUnit.DAYS));
    assertThrows(LicenseViolationException.class,
        () -> licenseService.validateBuildAllowed(project));
}

@Test
void starterTier_monthlyReset_allows() {
    var project = testProject(Tier.STARTER);
    project.setBuildCount(10);
    project.setLastBuildResetAt(Instant.now().minus(31, ChronoUnit.DAYS)); // past 30-day window
    licenseService.resetMonthlyCounterIfNeeded(project);
    assertThat(project.getBuildCount()).isEqualTo(0); // reset
    assertDoesNotThrow(() -> licenseService.validateBuildAllowed(project));
}
```

### 10.4 — End-to-End Smoke Test

One full build per tier using a real `npm` + `electron-builder` environment (CI pipeline only — not local unit tests). Verify: installer artifact produced, R2 upload URL returned, correct filename suffix (`-Trial-` for Free), correct modules present in unpacked `main.js`.

---

## Appendix — ConversionService Refactored Method Skeleton

```java
// Full refactored generateFiles() in ConversionService.java
public Map<String, String> generateFiles(ConversionProject project) {
    FeatureConfig config = project.getFeatureConfig() != null
        ? project.getFeatureConfig()
        : FeatureConfig.builder().tier(FeatureConfig.Tier.FREE).build();

    Map<String, String> files = new LinkedHashMap<>();
    files.put("config.js",    generateConfigJs(project, config));
    files.put("main.js",      generateMainJs(project, config));
    files.put("preload.js",   generatePreloadJs(config));
    files.put("package.json", generatePackageJson(project, config));

    // Bundle static assets for applicable modules
    if (config.isFree() && config.hasExpiry()) {
        files.put("expired.html", loadStaticAsset("templates/electron/expired.html"));
    }
    if (config.isFree()) {
        files.put("build/installer.nsh", loadStaticAsset("templates/electron/installer.nsh"));
    }

    return files;
}

private String generateMainJs(ConversionProject project, FeatureConfig config) {
    List<ElectronModule> mainModules = List.of(
        new SplashScreenModule(),
        new ExpiryLockModule(),
        new DomainLockModule(),
        new ScreenCaptureModule(),
        new SystemTrayModule(),
        new AutoUpdateModule(),
        new DarkLightSyncModule(),
        new WindowPolishModule(),
        new GlobalHotkeysModule(),
        new CustomKeyBindingsModule(),
        new FileSystemModule(),
        new ClipboardModule()
    );

    StringBuilder sb = new StringBuilder();
    sb.append(MAIN_JS_IMPORTS);
    sb.append(buildWindowOptions(config));
    sb.append(MAIN_JS_CREATE_WINDOW_OPEN);

    mainModules.stream()
        .filter(m -> m.target() == ElectronModule.Target.MAIN_JS)
        .filter(m -> m.isEnabled(config))
        .forEach(m -> sb.append(m.render(config)));

    sb.append(MAIN_JS_APP_LIFECYCLE);
    return sb.toString();
}

private String generatePreloadJs(FeatureConfig config) {
    List<ElectronModule> preloadModules = List.of(
        new ScreenCapturePreloadModule(),
        new WatermarkModule(),
        new DomainLockToastModule(),
        new RightClickModule(),
        new DarkLightSyncPreloadModule(),
        new ClipboardPreloadModule(),
        new NativeNotificationsModule(),
        new OfflineCacheModule()
    );

    StringBuilder sb = new StringBuilder();
    sb.append(PRELOAD_JS_HEADER);

    preloadModules.stream()
        .filter(m -> m.target() == ElectronModule.Target.PRELOAD_JS)
        .filter(m -> m.isEnabled(config))
        .forEach(m -> sb.append(m.render(config)));

    sb.append(PRELOAD_JS_FOOTER);
    return sb.toString();
}
```
