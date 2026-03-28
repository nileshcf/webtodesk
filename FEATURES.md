# WebToDesk \u2014 Feature Module Injection Plan

## Project Architecture Overview

WebToDesk is a **Spring Boot microservices** platform that converts websites into desktop Electron apps. The core build pipeline lives in `conversion-service` and works as follows:

1. **ConversionService.java** \u2014 Holds the project entity and *generates* the raw Electron source files (`main.js`, `preload.js`, `config.js`, `package.json`) as in-memory strings.
2. **BuildService.java** \u2014 Takes those files, writes them to a temp workspace, runs `npm install` + `electron-builder`, finds the installer artifact, then uploads it to Cloudflare R2.
3. **ConversionProject entity** \u2014 Stores project metadata (URL, title, icon, status, artifact path).

**The injection point is clear:** all feature modules are code strings appended/merged into `generateMainJs()`, `generatePreloadJs()`, `generateConfigJs()`, and `generatePackageJson()` inside `ConversionService.java`, gated by tier logic before `writeElectronFiles()` is called in `BuildService.java`.

---

## Implementation Strategy

### Core Pattern: Module Injection via `FeatureConfig`

Introduce a `FeatureConfig` record that travels with every build request. `ConversionService` reads this config and conditionally includes module code blocks. This keeps every feature as an isolated, testable snippet rather than entangled spaghetti.

```
ConversionProject
  \u2514\u2500\u2500 featureConfig: FeatureConfig (JSON column)
        \u2514\u2500\u2500 tier: FREE | STARTER | FIVE_YEAR | LIFETIME
        \u2514\u2500\u2500 splashScreen: SplashScreenConfig { logoUrl, showOurLogo }
        \u2514\u2500\u2500 titleBar: TitleBarConfig { enabled, text, style }
        \u2514\u2500\u2500 domainLock: DomainLockConfig { allowedDomains[], blockedDomains[] }
        \u2514\u2500\u2500 fileDownload: boolean
        \u2514\u2500\u2500 screenCaptureProtection: boolean
        \u2514\u2500\u2500 watermark: WatermarkConfig { text, position, opacity, useOurs }
        \u2514\u2500\u2500 keyBindings: KeyBindingConfig[]
        \u2514\u2500\u2500 offlineCache: OfflineCacheConfig { strategy, maxAge }
        \u2514\u2500\u2500 autoUpdate: AutoUpdateConfig { feedUrl }
        \u2514\u2500\u2500 notifications: boolean
        \u2514\u2500\u2500 systemTray: TrayConfig { tooltip, contextMenu[] }
        \u2514\u2500\u2500 darkLightSync: boolean
        \u2514\u2500\u2500 clipboardIntegration: boolean
        \u2514\u2500\u2500 windowPolish: WindowPolishConfig { blur, alwaysOnTop, frame }
        \u2514\u2500\u2500 rightClickDisable: RightClickConfig { disable, customMenuItems[] }
        \u2514\u2500\u2500 fileSystemAccess: FileSystemConfig { allowedPaths[] }
        \u2514\u2500\u2500 globalHotkeys: GlobalHotkeyConfig[]
        \u2514\u2500\u2500 expiry: ExpiryConfig { expiresAt, lockMessage }
        \u2514\u2500\u2500 buildLimit: BuildLimitConfig { buildsAllowed, buildsUsed }
```

---

## Tier Capability Matrix

| Feature | Free (Trial) | Starter/1-Yr | Five-Year | Lifetime |
|---|---|---|---|---|
| Splash screen (our logo visible) | \u2705 | \u2705 | \u2705 | \u2705 |
| File download | \u2705 | \u2705 | \u2705 | \u2705 |
| 1 active app | \u2705 | \u2014 | \u2014 | \u2014 |
| Domain lock (base URL only) | \u2705 | \u2014 | \u2014 | \u2014 |
| Title bar basic | \u2705 | \u2705 | \u2705 | \u2705 |
| Queued builds | \u2705 | \u2014 | \u2014 | \u2014 |
| Watermark (our brand) | \u2705 | \u2014 | \u2014 | \u2014 |
| 30-day expiry \u2192 upgrade screen | \u2705 | \u2014 | \u2014 | \u2014 |
| Priority queue | \u2014 | \u2705 | \u2705 | \u2705 |
| Full domain whitelist/blacklist | \u2014 | \u2705 | \u2705 | \u2705 |
| Screen capture protection | \u2014 | \u2705 | \u2705 | \u2705 |
| Custom watermark (live, no ours) | \u2014 | \u2705 | \u2705 | \u2705 |
| Custom key bindings | \u2014 | \u2705 | \u2705 | \u2705 |
| Offline caching | \u2014 | \u2705 | \u2705 | \u2705 |
| Auto-updates | \u2014 | \u2705 | \u2705 | \u2705 |
| Native notifications | \u2014 | \u2705 | \u2705 | \u2705 |
| System tray + context menu | \u2014 | \u2705 | \u2705 | \u2705 |
| Dark/light mode sync | \u2014 | \u2705 | \u2705 | \u2705 |
| Clipboard integration | \u2014 | \u2705 | \u2705 | \u2705 |
| Window polish (blur, always-on-top) | \u2014 | \u2705 | \u2705 | \u2705 |
| Right-click disable / custom menu | \u2014 | \u2705 | \u2705 | \u2705 |
| File system access | \u2014 | \u2705 | \u2705 | \u2705 |
| Global hotkeys | \u2014 | \u2705 | \u2705 | \u2705 |
| Build quota | 1 active | 10/mo | 50/mo | Unlimited (fair use) |
| App license duration | 30 days | 1 year | 5 years | Lifetime |

---

## Phase 1 \u2014 Foundation & Free Tier Modules

**Goal:** Restructure `ConversionService.java` to accept `FeatureConfig`, add DB column, and implement all Free Tier features.

### 1.1 \u2014 DB & Entity Changes

- Add `feature_config` JSON column to `ConversionProject` entity using `@Column(columnDefinition = "TEXT")` + Jackson serialization.
- Add `tier` enum field: `FREE | STARTER | FIVE_YEAR | LIFETIME`.
- Add `expiresAt` (Instant), `buildCount` (int), `maxBuilds` (int) fields.
- Update `CreateConversionRequest` DTO to accept `featureConfig`.

### 1.2 \u2014 Module: Splash Screen (Free + All Tiers)

**Where:** `generateMainJs()` \u2014 inject before `mainWindow.loadURL()`.

**Logic:**
- Create a borderless `BrowserWindow` (400\u00d7300) showing the logo image.
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

### 1.3 \u2014 Module: File Download (Free + All Tiers)

**Where:** `generateMainJs()` \u2014 add `will-download` handler on `webContents.session`.

**Logic:**
- Intercept `session.defaultSession.on('will-download')`.
- Show native Save dialog using `dialog.showSaveDialogSync`.
- Set download path, attach progress listener, emit IPC events for UI progress display.
- Free tier: downloads work but installer is watermarked.

### 1.4 \u2014 Module: Domain Lock (Free Tier Only)

**Where:** `generateMainJs()` \u2014 add `will-navigate` and `new-window` event handlers.

**Logic:**
- `allowedDomains` array (from `DomainLockConfig`) contains only the base URL hostname.
- On every navigation attempt, parse URL hostname and block if not in allowlist.
- Show in-app toast: "Navigation to external sites is disabled in this version."
- Pro tier: replaces with full `DomainWhitelistBlacklist` module (Phase 2).

### 1.5 \u2014 Module: Title Bar Customization (Free + All Tiers)

**Where:** `generateMainJs()` \u2014 `BrowserWindow` constructor options + `setTitle` logic.

**Logic:**
- `enabled: false` \u2192 `frame: false`, custom frameless window with draggable CSS region injected via `preload.js`.
- `enabled: true` \u2192 standard frame, `title` set from `TitleBarConfig.text`.
- Style options (`style: 'hidden' | 'hiddenInset' | 'default'`) mapped to Electron's `titleBarStyle` option on macOS.
- `page-title-updated` event always prevented to keep the custom title.

### 1.6 \u2014 Module: Build Queue Priority

**Where:** `BuildService.java` \u2014 `@Async("buildExecutor")` thread pool configuration.

**Logic:**
- Free tier: uses a `buildExecutorFree` thread pool with `corePoolSize=1`, `queueCapacity=50`.
- Pro tiers: uses `buildExecutorPro` with `corePoolSize=5`, instant dispatch.
- `BuildQueueService` bean selects executor based on project tier before triggering.

### 1.7 \u2014 Module: Watermark (Free Tier \u2014 Our Brand)

**Where:** `generatePreloadJs()` \u2014 inject CSS overlay into the page DOM.

**Logic:**
- Inject a fixed-position `div` with "Powered by WebToDesk" text + logo image.
- `opacity: 0.15`, `pointer-events: none`, bottom-right corner.
- Cannot be removed by the website (injected after every `did-finish-load`).
- Free tier: always our watermark. Pro: replaced by custom watermark module.

### 1.8 \u2014 Module: 30-Day Expiry + Upgrade Lock

**Where:** `generateMainJs()` + `generatePreloadJs()` + backend enforcement.

**Logic (two-layer):**

**Layer 1 \u2014 Backend:** `BuildService` checks `project.getExpiresAt()` before allowing a new build. Expired free-tier projects get `status: EXPIRED`, no new builds allowed.

**Layer 2 \u2014 Runtime (in the built app):** `config.js` includes `expiresAt` timestamp. On app start, `main.js` checks `Date.now() > expiresAt`. If expired:
- Load a local `expired.html` page instead of the website URL.
- `expired.html` is bundled with the build, shows upgrade CTA with link to WebToDesk pricing page.
- App is otherwise fully locked \u2014 no way past the screen.

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

## Phase 2 \u2014 Pro Tier Modules

**Goal:** Implement all 16 Pro features as injectable modules. Each module is a self-contained code block in `ConversionService`.

### 2.1 \u2014 Module: Full Domain Whitelist / Blacklist

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
  // same check \u2014 return { action: 'deny' } or { action: 'allow' }
});
```

### 2.2 \u2014 Module: Screen Capture Protection

**Note:** Already partially present in current codebase (`setContentProtection`, screenshot shortcut blocking). This module **enhances and makes it configurable.**

**Where:** `generateMainJs()` + `generatePreloadJs()`.

**Additions:**
- `mainWindow.setContentProtection(true)` \u2014 always on if feature enabled (currently unconditional, gate it).
- Block all known screenshot/recording shortcuts (already done \u2014 ensure exhaustive cross-platform list).
- Preload overlay (already implemented) \u2014 make it configurable (custom message text, countdown seconds).
- Add `visibilitychange` listener in preload to trigger blackout when app goes to background (OBS detection heuristic).

### 2.3 \u2014 Module: Custom Watermark

**Replaces** the "Powered by WebToDesk" free watermark.

**Where:** `generatePreloadJs()`.

**Config:** `text`, `imageUrl`, `position` (`top-left | top-right | bottom-left | bottom-right | center`), `opacity` (0.0\u20131.0), `fontSize`, `color`.

- On `did-finish-load`, inject CSS watermark div with user-specified content.
- `imageUrl` fetched from R2 (uploaded during project config).

### 2.4 \u2014 Module: Custom Key Bindings

**Where:** `generateMainJs()`.

**Config:** `KeyBindingConfig[]` \u2014 each entry: `{ accelerator: string, action: 'reload' | 'goBack' | 'goForward' | 'toggleDevTools' | 'custom', ipcChannel?: string }`.

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

### 2.5 \u2014 Module: Offline Caching

**Where:** `generateMainJs()` (session configuration) + new `service-worker.js` file injected into build.

**Config:** `strategy` (`cache-first | network-first | stale-while-revalidate`), `maxAge` (seconds), `maxSize` (MB).

**Implementation:**
- Use Electron's `session.defaultSession.webRequest` + `protocol.interceptHttpProtocol` to serve cached responses.
- Alternative approach: inject a Service Worker registration script via `preload.js` that caches app shell resources.
- Bundle a `sw.js` file with the configurable caching strategy.
- `did-fail-load` handler shows offline page if cache miss and network unavailable.

### 2.6 \u2014 Module: Auto-Updates

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

### 2.7 \u2014 Module: Native Notifications

**Where:** `generatePreloadJs()` \u2014 expose IPC bridge; `generateMainJs()` \u2014 IPC handler using `Notification` from Electron.

**Config:** `enabled` boolean. No deep config needed \u2014 apps use the standard Web Notifications API which Electron forwards.

**Implementation:** Grant `notifications` permission in `session.setPermissionRequestHandler`, and ensure `Notification.requestPermission()` resolves to `'granted'`.

### 2.8 \u2014 Module: System Tray + Context Menu

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

### 2.9 \u2014 Module: Dark/Light Mode Sync

**Where:** `generateMainJs()` + `generatePreloadJs()`.

**Logic:**
- Listen to `nativeTheme.on('updated')` in `main.js`.
- Send IPC message `theme-changed` with `{ isDark: nativeTheme.shouldUseDarkColors }`.
- Preload bridge exposes `onThemeChange(callback)` via `contextBridge`.
- Injects CSS class `wtd-dark` / `wtd-light` on `document.documentElement` for websites to optionally respond to.

### 2.10 \u2014 Module: Clipboard Integration

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

### 2.11 \u2014 Module: Window Polish

**Where:** `generateMainJs()` \u2014 `BrowserWindow` constructor options.

**Config:** `WindowPolishConfig { blur: boolean, alwaysOnTop: boolean, frame: boolean, opacity: number, vibrancy: string }`.

- `blur: true` \u2192 `vibrancy: 'under-window'` (macOS) or `backgroundMaterial: 'acrylic'` (Windows 11).
- `alwaysOnTop: true` \u2192 `mainWindow.setAlwaysOnTop(true, 'floating')`.
- `frame: false` \u2192 frameless window with injected drag region in preload.
- `opacity` \u2192 `mainWindow.setOpacity(value)`.

### 2.12 \u2014 Module: Right-Click Disable / Custom Menu

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

### 2.13 \u2014 Module: File System Access

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

### 2.14 \u2014 Module: Global Hotkeys

**Where:** `generateMainJs()`.

**Config:** `GlobalHotkeyConfig[]` \u2014 each: `{ accelerator, action, ipcChannel }`. Unlike custom key bindings, these work **even when the app window is not focused.**

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

## Phase 3 \u2014 License & Build Enforcement (Backend)

### 3.1 \u2014 License Validation Service

New `LicenseService` bean in `conversion-service`:

- On every build request, validate: `tier`, `expiresAt`, `buildsUsed < maxBuilds`, `activeAppsCount < maxActiveApps`.
- Free: 1 active app, 1 build ever, expires 30 days from creation.
- Starter: 10 builds/month rolling window, apps expire 1 year from purchase date.
- Five-Year: 50 builds/month, apps expire 5 years from purchase.
- Lifetime: unlimited builds (soft cap at 500/month for abuse prevention), no expiry.
- Return typed `LicenseViolation` exceptions that surface to the user as clear error messages.

### 3.2 \u2014 Build Counter & Quota Tracking

- `ConversionProject` gets `buildCount` (int) + `lastBuildResetAt` (Instant).
- Monthly rolling reset: on each build, if `now - lastBuildResetAt > 30 days`, reset `buildCount = 0`.
- Quota exceeded \u2192 return `402 Payment Required` with upgrade URL in response body.

### 3.3 \u2014 Watermarked Installer (Free Tier)

Two approaches \u2014 use approach (a) as primary:

**(a) NSIS script injection (Windows):** In `generatePackageJson()` for Free tier, add custom NSIS script that appends a "Trial Version" text to installer screens and desktop shortcut name.

**(b) Binary patching:** Post-build, use a script to patch the installer header. More fragile \u2014 avoid.

### 3.4 \u2014 Upgrade Screen Asset

Bundle `expired.html` as a static resource in the conversion-service (under `src/main/resources/templates/electron/`). It is copied into the build workspace alongside `main.js`. Content: branded WebToDesk upgrade page with a CTA button pointing to the pricing page URL (injected from `config.js`).

---

## Phase 4 \u2014 Frontend UI Changes

### 4.1 \u2014 Feature Config Builder (UI)

In the `frontend` service, add a multi-step project creation wizard:

**Step 1:** Basic info (URL, title, icon upload).  
**Step 2:** Feature toggles \u2014 grouped 