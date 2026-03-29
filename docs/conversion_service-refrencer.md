# WEBTODESK
### URL → Desktop Application Conversion Platform
> Complete Architectural Blueprint · Technical Stack · Feature Modules · Build Pipeline · Business Model

| | |
|---|---|
| **Platform Type** | SaaS — Website to Native Desktop App Converter |
| **Core Runtime** | Electron (Chromium + Node.js) |
| **Target OS** | Windows · Linux · macOS (host-dependent build target) |
| **Frontend** | React 19 · TypeScript · Vite |
| **Backend** | Java 17 + Spring Boot 3.3 |
| **Billing** | Deferred |
| **Document** | March 2026 — Internal Reference & Roadmap |

*Inspired by: Appilix mobile architecture | Built on: Electron + electron-builder | Ref repos: nileshcf/webtodesk · thecheesybit/desktop_app_project*

---

> ⚠️ **Alignment Note (Current Codebase Wins):**
> This file contains roadmap/vision material. For implementation truth, use current Java/Spring controllers/services and `skills/conversion-service.md`.
> Current runtime flow is local `ProcessBuilder` build orchestration + R2 upload + SSE status updates.
> Payment/subscription gateway wiring is intentionally deferred until core tiered build/test flows are complete.

## 01 Executive Summary

WebToDesk is a SaaS platform that converts any publicly accessible website URL into a fully packaged, native-feeling desktop application for macOS, Windows, and (roadmap) Linux — in under 10 minutes, without any code changes to the target website. The user pastes a URL, names their app, uploads a logo, configures modules, and downloads a signed installer ready for distribution.

The technology foundation is Electron — the same framework powering VS Code, Slack, Figma, and Notion. Electron bundles Chromium (for rendering the website) with Node.js (for native OS access) into a single cross-platform executable. WebToDesk automates the otherwise manual process of creating, configuring, compiling, and packaging an Electron app — democratising desktop software distribution for anyone with a website.

> **Core Insight:** The average website owner cannot create a desktop app. Building one manually requires Electron knowledge, Node.js, a build toolchain, code signing certificates, CI/CD, and update infrastructure. WebToDesk collapses all of this into a 3-step web form. The same insight drove Appilix to $50K+ customers on mobile — the desktop opportunity is equally large and far less competitive.

The platform is currently implemented as a Java Spring Boot microservices backend with a React/Vite frontend. Build orchestration runs inside `conversion-service` using local ProcessBuilder execution (`npm install` + `electron-builder`) and uploads artifacts to Cloudflare R2. Real-time build feedback is delivered via SSE endpoints, with billing integration intentionally deferred for the current testing phase.

---

## 02 How It Works — End to End

### User Flow (10-Minute Promise)

| Step | Name | Description |
|------|------|-------------|
| **Step 1** | Create | Authenticated user uses dashboard, creates a conversion project, and provides website URL plus metadata. |
| **Step 2** | Configure | User fills out app metadata: App Name, App ID (e.g. `com.mybrand.app`), Window size/min-size, custom icon (512×512 PNG), splash screen, and selects which native Modules to enable. |
| **Step 3** | Choose Tier + Modules | User selects a tier view (Free/TRIAL, STARTER, PRO) and module set; tier-gated modules are validated in backend. |
| **Step 4** | Build | Dashboard triggers async build in conversion-service (`/conversions/{id}/build` or `/build/trigger`); service runs local build and uploads artifact to R2. |
| **Step 5** | Monitor + Download | UI reads status via SSE/polling endpoints; when status is `READY`, user downloads from R2 redirect endpoint. |

### System Architecture

```
Browser (React/Vite)
  │
  ├─ /user/**       → API Gateway (:8080) → user-service (:8081)
  └─ /conversion/** → API Gateway (:8080) → conversion-service (:8082)

conversion-service build path:
  1) validate environment
  2) create temp workspace
  3) write Electron template files + selected modules
  4) run npm install + electron-builder
  5) upload artifact to Cloudflare R2
  6) emit status/log updates via SSE + status endpoints
```

---

## 03 Technology Stack

### 3.1 Generated Desktop App Shell

| Layer | Technology | Notes |
|-------|-----------|-------|
| Core Runtime | Electron (v28+) | Node.js + Chromium bundled. Main process (Node) + Renderer process (Chromium WebView of target URL) |
| App Packaging | electron-builder | `npm i -D electron-builder`. Produces `.dmg` (macOS), `.exe` NSIS installer (Windows), `.AppImage/.deb/.rpm` (Linux) |
| WebView | Chromium BrowserView / WebContentsView | Renders the target website URL inside the Electron window. Full Chromium engine — JS, WebSockets, media, cookies all work |
| Config Injection | Node.js fs + template literals | User config (URL, appName, icon, modules) injected into `main.js`, `package.json`, and module files at build time via a `build-config.js` script |
| Native Bridge | Electron IPC (ipcMain/ipcRenderer) | Modules communicate between the website JS and Node.js main process using `preload.js` + `contextBridge`. Zero `nodeIntegration` in renderer. |
| Auto Updater | electron-updater (electron-builder) | Built-in auto-update support. Works with GitHub Releases or custom Hazel/Nuts server on Vercel |
| Code Signing | Apple Developer ID (macOS) / EV Certificate (Windows) | Required for Gatekeeper and SmartScreen bypass. Managed by user's own certs or via Appsign service for Pro tier |
| Icon Gen | electron-icon-builder / png2icons | Converts 512×512 PNG to `.icns` (macOS) and `.ico` (Windows) automatically in the build pipeline |

### 3.2 WebToDesk Platform Backend

| Layer | Technology | Notes |
|-------|-----------|-------|
| API Backend | Java 17 + Spring Boot 3.3 | Multi-service backend (`api-gateway`, `user-service`, `conversion-service`, `discovery-service`) |
| Build Orchestration | conversion-service `BuildService` | Async local ProcessBuilder flow with retries, timeout, and build log capture |
| Queueing | In-process build queue services | Priority/normal queue tracking via service layer |
| Databases | PostgreSQL + MongoDB + Redis | User/auth in PostgreSQL, conversion/build docs in MongoDB, token blacklist/cache in Redis |
| File Storage | AWS S3 / Cloudflare R2 | Stores uploaded icons, splash images, and compiled `.dmg/.exe/.AppImage` artifacts with signed URLs |
| Realtime | SSE + status polling endpoints | Build progress streamed from backend (`/conversions/{id}/build/stream` and `/build/progress/{projectId}`) |
| Build Agents | Local host/container runtime | Build target resolves based on host/platform config |
| Frontend Host | Vite dev + standard static deploy options | React dashboard served by Vite in development |
| Billing | Deferred | License tiers exist; payment gateway integration intentionally deferred |
| Email | Resend / Mailgun | Transactional: signup, build complete, password reset. Template-based HTML emails |
| Monitoring | In progress | Basic logs/health available; observability hardening planned |

---

## 04 Electron App Template — File Structure

Every app WebToDesk generates is based on a master Electron template stored on the build server. User config is injected into this template, then it is compiled.

```
webtodesk-template/
├── package.json          ← appId, productName, version, build targets, electron-builder config
├── build-config.js       ← BUILD SCRIPT: reads user config → injects into all files
├── user-config.json      ← INJECTED: url, appName, windowWidth, windowHeight, modules[]
│
├── src/
│   ├── main.js           ← MAIN PROCESS: creates BrowserWindow, loads URL, handles IPC
│   ├── preload.js        ← BRIDGE: exposes safe APIs to renderer via contextBridge
│   ├── menu.js           ← Native menu bar (File, Edit, View, Window) + custom items
│   ├── tray.js           ← System tray icon + context menu (module: optional)
│   └── updater.js        ← Auto-update logic using electron-updater
│
├── modules/              ← NATIVE ADD-ON MODULES (enabled/disabled per user config)
│   ├── auth/
│   │   ├── biometric.js  ← systemPreferences.promptTouchID / Windows Hello via node-addon
│   │   └── auth-lock.js  ← Lock screen shown on app launch or resume
│   ├── notifications/
│   │   ├── fcm-client.js     ← Firebase Cloud Messaging (desktop push via FCM HTTP v1 API)
│   │   └── notif-handler.js  ← Maps FCM payload → Electron Notification API
│   ├── security/
│   │   ├── screen-protect.js ← setContentProtection(true) — blocks screenshots/screen record
│   │   └── dlp.js            ← Disables right-click save/copy on specific URL patterns
│   ├── deeplink/
│   │   └── deeplink.js   ← Registers custom protocol (myapp://) → opens app + navigates
│   ├── offline/
│   │   └── offline.js    ← Service Worker cache strategy + custom offline HTML page
│   ├── analytics/
│   │   └── analytics.js  ← Firebase Analytics events sent from main process
│   ├── sidebar/
│   │   └── sidebar.js    ← Injected left sidebar with custom navigation links
│   └── badge/
│       └── badge.js      ← app.setBadgeCount(n) for macOS Dock / Windows taskbar
│
├── assets/
│   ├── icon.png          ← INJECTED: user's 512×512 icon
│   ├── icon.icns         ← Generated: macOS icon bundle
│   ├── icon.ico          ← Generated: Windows icon
│   └── splash.html       ← INJECTED: custom splash/loading screen
│
├── renderer/             ← Renderer process UI (for auth screens, error pages)
│   ├── auth-screen.html  ← Biometric / password lock screen
│   ├── offline.html      ← No internet error page
│   └── loading.html      ← Initial loading spinner
│
└── build/                ← electron-builder output directory
    ├── *.dmg             ← macOS disk image
    ├── *.exe             ← Windows NSIS installer
    └── *.AppImage        ← Linux AppImage
```

### Core File: `main.js`

```javascript
const { app, BrowserWindow, session, ipcMain, Notification, shell } = require('electron')
const { setupModules } = require('./modules/loader')
const config = require('./user-config.json')

let mainWindow

function createWindow() {
  mainWindow = new BrowserWindow({
    width: config.windowWidth || 1280,
    height: config.windowHeight || 800,
    minWidth: config.minWidth || 800,
    minHeight: config.minHeight || 600,
    title: config.appName,
    icon: path.join(__dirname, 'assets/icon.png'),
    titleBarStyle: process.platform === 'darwin' ? 'hiddenInset' : 'default',
    webPreferences: {
      preload: path.join(__dirname, 'src/preload.js'),
      nodeIntegration: false,   // SECURITY: disabled
      contextIsolation: true,   // SECURITY: enabled
      sandbox: false,           // needed for preload IPC
      webSecurity: true,
    }
  })

  mainWindow.loadURL(config.websiteUrl)
  setupModules(mainWindow, config)
}

app.whenReady().then(createWindow)
```

---

## 05 Native Module System — Complete Implementation

> ℹ️ **Scope note:** this section mixes currently implemented modules and roadmap modules. For exact live module availability by tier, use backend endpoint `GET /conversion/build/modules?tier=TRIAL|STARTER|PRO|LIFETIME`.

Modules are the heart of the WebToDesk value proposition. Each module is an optional Node.js file that hooks into Electron's main process APIs to add native OS capabilities beyond what a browser can offer. Modules are enabled/disabled per user config and tree-shaken out of the build if not enabled.

---

### 🔒 Screen Protection `[Pro]`

Prevents screenshots, screen recording (QuickTime, OBS), and AirPlay mirroring of the app window.

```javascript
mainWindow.setContentProtection(true) // macOS + Windows
// On macOS: uses NSWindow setContentProtectionEnabled
// On Windows: WDA_EXCLUDEFROMCAPTURE via SetWindowDisplayAffinity
// Cannot be bypassed by user without admin access
// Also blocks window preview in macOS Mission Control
```

---

### 👆 Biometric Authentication `[Pro]`

Locks the app on launch or resume. Shows a native auth prompt (Touch ID, Face ID on Mac; Windows Hello, fingerprint on Windows). The website is hidden until auth succeeds.

```javascript
// macOS: systemPreferences.promptTouchID(reason)
// Windows: node-windows-hello npm package
// Fallback: password dialog via dialog.showInputBox
// On lock: mainWindow.hide() → show auth-lock.html
// On success: mainWindow.show(), load website URL
// Keytar stores encrypted session token across restarts
```

---

### 🔔 Push Notifications (FCM) `[Pro]`

Delivers push notifications to the desktop app from any server using Firebase Cloud Messaging.

```javascript
// 1. App registers with FCM → gets device token
// 2. Token stored in Firebase RTDB under user's app
// 3. Backend calls FCM HTTP v1 API with token + payload
// 4. FCM delivers to desktop via persistent WebSocket
// 5. Main process receives message → Electron Notification API

const notif = new Notification({ title, body, icon })
notif.on('click', () => mainWindow.loadURL(payload.clickUrl))
```

---

### 🔗 Deep Links `[Pro]`

Registers a custom URL protocol (e.g. `myapp://`) so clicking links in emails, browsers, or other apps opens the desktop app and navigates to a specific page.

```javascript
// Register protocol on install:
app.setAsDefaultProtocolClient('myapp')

// Handle on macOS:
app.on('open-url', (event, url) => navigate(url))

// Handle on Windows:
const gotLock = app.requestSingleInstanceLock()
app.on('second-instance', (event, argv) => {
  const url = argv.find(a => a.startsWith('myapp://'))
  if (url) navigate(url)
})
```

---

### 📡 Offline Mode `[Pro]`

Shows a branded offline error screen when the device has no internet. Optionally caches the last-visited page.

```javascript
require('electron').net.isOnline()
// On 'offline' event: mainWindow.loadFile('renderer/offline.html')
// On 'online' event: reload original URL
// Optional: electron-store caches page HTML for offline read
```

---

### 🔑 Custom Auth Screen `[Free]`

Shows a login/register page hosted on WebToDesk's renderer before loading the website.

```javascript
mainWindow.loadFile('renderer/auth-screen.html')
// auth-screen.html calls: window.electronAPI.authComplete(token)
// main.js receives IPC → loads website with auth cookie injected:
session.defaultSession.cookies.set({ url, name, value })
```

---

### 🔴 Badge Counter `[Free]`

Shows a numeric badge on the macOS Dock icon or Windows taskbar.

```javascript
// Website JS calls:
window.electronAPI.setBadge(5)

// preload.js exposes:
contextBridge.exposeInMainWorld('electronAPI', {
  setBadge: (n) => ipcRenderer.send('set-badge', n)
})

// main.js:
ipcMain.on('set-badge', (_, n) => app.setBadgeCount(n))
```

---

### 🪟 Window Switcher Protection `[Pro]`

Replaces the app thumbnail in Alt+Tab / Mission Control with a custom privacy screen.

```javascript
// macOS: setContentProtection(true) hides Mission Control preview
// Windows: SetWindowDisplayAffinity WDA_EXCLUDEFROMCAPTURE
// Custom: on 'hide' event, briefly show a splash BrowserWindow
```

---

### ⌨️ Global Shortcuts `[Free]`

```javascript
const { globalShortcut } = require('electron')
app.on('ready', () => {
  globalShortcut.register('CommandOrControl+Shift+Space', () => {
    if (mainWindow.isVisible()) mainWindow.hide()
    else mainWindow.show()
  })
})
```

---

## 06 Build Pipeline — Step by Step

### Phase 1 — Config Validation & Job Creation
- Backend receives `POST /conversion/conversions/{id}/build` or `POST /conversion/build/trigger`
- Validates project/tier and build preconditions
- Marks project `BUILDING`, stores build metadata, and starts async flow
- Emits initial progress (`PREPARING`) via SSE/status endpoints

### Phase 2 — Template Cloning & Config Injection
- Build service creates workspace in configured output dir (default temp path)
- Writes generated Electron files from templates (`config/main/preload/package`) and selected module templates

### Phase 3 — Dependency Install
- Runs `npm install --no-audit --no-fund` in workspace
- Uses configured process env (`CI`, npm/electron cache paths where available)

### Phase 4 — Compilation

| Platform | Command | Output | Time |
|----------|---------|--------|------|
| macOS | `electron-builder --mac` | `.dmg` (arm64 + x64 universal) | 3–6 min |
| Windows | `electron-builder --win` | NSIS `.exe` + portable `.exe` | 2–4 min |
| Linux | `electron-builder --linux` | `.AppImage` + `.deb` + `.rpm` | 2–3 min |

> **Note:** target platform is resolved by backend configuration/host capabilities (`auto|win|linux|mac`).

### Phase 5 — Code Signing

| Platform | Requirements | Free Tier |
|----------|-------------|-----------|
| macOS | `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID` + auto-notarization | Unsigned (Gatekeeper warning) |
| Windows | EV Code Signing Certificate (`.pfx`) or Azure Trusted Signing | Unsigned (SmartScreen warning) |

### Phase 6 — Upload & Delivery
- Artifacts uploaded to R2: `builds/{user_email}/{project_id}/{filename}`
- Project updated to `READY` with `downloadUrl`
- UI consumes SSE/status endpoints and shows download action through redirect endpoint
- On failure, project is marked `FAILED` with error details and last output tail

---

## 07 Dashboard — Frontend Architecture

The WebToDesk dashboard is implemented as a React + TypeScript + Vite application.

### Page Structure

| Page | File | Description |
|------|------|-------------|
| Landing Page | `index.html` | Marketing page — hero, features, pricing, testimonials |
| Sign Up / Login | frontend auth pages | JWT login/register flow via user-service through gateway |
| Dashboard Home | `dashboard.html` | Lists all user's apps with status, last build date, download links |
| App Creator | `create.html` | Multi-step form: URL → Name & Icon → Window Settings → Modules → Platform → Build |
| App Editor | `app/{id}.html` | Edit existing app config, re-trigger builds, view build history |
| Build Status | dashboard build views | Real-time progress via SSE + build status polling |
| Control Panel | `control.html` | Push notification sender, badge reset, analytics dashboard (Pro) |
| Billing | deferred | Payment gateway integration intentionally deferred in current phase |
| Docs | `docs/` | Module guides, code signing walkthrough, API reference |

### Real-Time Build Status Pattern

```javascript
const emitter = conversionApi.subscribeToBuildProgress(projectId)
// fallback polling
const status = await conversionApi.getBuildStatus(projectId)

if (status.status === 'READY') {
  showDownload(status.downloadUrl)
}

if (status.status === 'FAILED') {
  showError(status.buildError)
}
```

---

## 08 Complete Feature Matrix

> ℹ️ **Interpretation note:** matrix entries are product-level targets; some items remain planned. Treat `skills/conversion-service.md` and current API responses as source of truth for what is currently active.

| Feature | Tier | Implementation |
|---------|------|---------------|
| Any Website URL Conversion | Free | Electron `WebContentsView` loads any HTTPS URL |
| macOS `.dmg` Build | Free | `electron-builder --mac`. ARM64+x64 universal. Unsigned on free tier. |
| Windows `.exe` Installer | Free | `electron-builder --win` with NSIS target. Unsigned on free tier. |
| Linux `.AppImage` | Free | `electron-builder --linux`. Single portable executable. |
| Custom App Name | Free | Injected into `package.json` `productName` + `BrowserWindow` title |
| Custom App Icon | Free | 512×512 PNG → auto-converted to `.icns` and `.ico` |
| Custom Window Size | Free | `width/height/minWidth/minHeight` injected into `BrowserWindow` |
| Splash / Loading Screen | Free | Custom HTML splash hidden on `did-finish-load` |
| Native Menu Bar | Free | File/Edit/View/Window menus with standard Electron roles |
| Print Support | Free | `Cmd+P` triggers `webContents.print()` |
| Zoom Controls | Free | `Cmd+/-` for zoom. Persisted via `electron-store`. |
| Single Instance Lock | Free | `app.requestSingleInstanceLock()` |
| System Tray | Free | Tray icon with show/hide/quit. Minimise to tray option. |
| Custom CSS Injection | Free | `webContents.insertCSS()` on every page load |
| Custom JS Injection | Free | `webContents.executeJavaScript()` on every page |
| Global Keyboard Shortcuts | Free | `globalShortcut.register()` — works system-wide |
| Navigation Controls | Free | Back/Forward/Reload/Home as toolbar overlay |
| Badge Counter | Free | `app.setBadgeCount(n)` via JS bridge from website |
| Offline Error Page | Free | `net` module `offline` event → loads custom `offline.html` |
| Auto Updater | Free | `electron-updater` checks GitHub Releases or Hazel server |
| Code Signing (macOS) | Pro | Apple Developer ID + Notarization. Removes Gatekeeper warning. |
| Code Signing (Windows) | Pro | EV Code Signing cert. Removes SmartScreen warning. |
| Screen Recording Protection | Pro | `mainWindow.setContentProtection(true)` |
| Biometric Auth Lock | Pro | Touch ID / Windows Hello. Locks entire app. |
| Custom Auth Screen | Pro | Login/PIN screen before website. Session via `keytar`. |
| Firebase Push Notifications | Pro | FCM HTTP v1 API. Device token stored in Firebase. |
| Deep Links | Pro | Custom protocol via `app.setAsDefaultProtocolClient()` |
| Analytics | Pro | Firebase Analytics + BigQuery export |
| White Label | Pro | Removes 'Made with WebToDesk' from About screen |
| Mac App Store Build | Pro | Sandboxed build with entitlements |
| Linux `.deb` + `.rpm` | Pro | Additional Linux package formats. GPG signed. |

---

## 09 Security Architecture

| Layer | Setting | Detail |
|-------|---------|--------|
| Context Isolation | **ON** | `contextIsolation: true` — website JS cannot access Node.js APIs directly |
| Node Integration | **OFF** | `nodeIntegration: false` — all native calls go through IPC bridge |
| Sandbox Mode | `sandbox: true` where possible | Limits renderer to strict sandbox, no filesystem access |
| Preload + contextBridge | Only channel | Only explicitly exposed functions are callable |
| Credential Storage | `keytar` | Stores tokens in OS Keychain / Windows Credential Manager / Linux libsecret. Never plaintext. |
| safeStorage API | `electron.safeStorage.encryptString()` | OS-level encryption for `electron-store` data |
| Permission Handler | `setPermissionRequestHandler()` | Camera, mic, geolocation blocked by default |
| webSecurity | **ON** | Enforces same-origin policy. Blocks mixed content. |
| Insecure Content | **OFF** | `allowRunningInsecureContent: false` — all resources must be HTTPS |
| CSP Headers | Injected | `Content-Security-Policy` via `onHeadersReceived` |
| Remote Code Execution | **Blocked** | No `eval()`, no remote scripts in main process |
| Auto-Update Integrity | SHA512 verified | `electron-updater` verifies checksum + signature before applying |

---

## 10 Business Model & Pricing Strategy

> ⚠️ **Current implementation note:** tiered licensing and feature gating are in testing; payment/subscription gateway integration is deferred until core Free/TRIAL, STARTER, and PRO build flows are stable.

### Pricing Tiers

| Feature | Free | Pro Monthly (~$19/mo) | Pro Yearly (~$79/yr) | Lifetime (~$199) |
|---------|------|----------------------|---------------------|-----------------|
| macOS + Windows Build | ✅ | ✅ | ✅ | ✅ |
| Linux Build | ❌ | ✅ | ✅ | ✅ |
| Code Signing | ❌ | ✅ | ✅ | ✅ |
| Screen Protection | ❌ | ✅ | ✅ | ✅ |
| Push Notifications | ❌ | ✅ | ✅ | ✅ |
| Biometric Auth | ❌ | ✅ | ✅ | ✅ |
| Deep Links | ❌ | ✅ | ✅ | ✅ |
| Analytics | ❌ | ✅ | ✅ | ✅ |
| White Label | ❌ | ❌ | ✅ | ✅ |
| Mac App Store Build | ❌ | ❌ | ✅ | ✅ |
| All Future Modules | ❌ | ❌ | ✅ | ✅ |
| Download Expiry | 48h | 30 days | Never | Never |
| Apps per account | 3 | 10 | Unlimited | Unlimited |
| Priority Build Queue | ❌ | ❌ | ✅ | ✅ |
| 30-day refund | — | ✅ | ✅ | ✅ |

### Revenue Strategy

| Strategy | Detail |
|---------|--------|
| **Freemium Funnel** | Free tier gives a real, working app. User proves the concept → upgrades for signing + modules. Conversion target: 8–12% of free users. |
| **Lifetime Plan** | One-time purchase ($199) attracts bootstrappers. Provides upfront cash flow. |
| **Agency Tier (Future)** | Unlimited apps + white-label + reseller dashboard. Target: web agencies. Price: $49/mo or $399/yr. |
| **Billing Integration** | Deferred to later phase after tiered build/view stabilization and test coverage. |
| **Module Upsells** | New modules released Pro-only. Creates continuous upgrade incentives. |
| **Build Credits (Future)** | Free tier limited to N builds/month. Extra builds purchasable. |

---

## 11 Product Roadmap — Phased Plan

### Phase 1 — Core MVP (Month 1–2)
- Electron template: `main.js`, `preload.js`, `menu.js`
- Build pipeline: conversion-service local async builds + R2 uploads + SSE status
- Dashboard: URL input → icon upload → build → download
- JWT auth via user-service through API gateway
- macOS `.dmg` + Windows `.exe` output (unsigned)
- React/Vite frontend deployment path
- Payment integration deferred

### Phase 2 — Security & Auth Modules (Month 2–3)
- Screen recording protection module (`setContentProtection`)
- Biometric auth module (Touch ID / Windows Hello)
- Custom auth screen module (lock screen before website)
- `keytar` integration for secure credential storage
- Code signing integration (user provides Apple ID + `.pfx`)
- Splash screen customisation (color, logo, animation)

### Phase 3 — Communication Modules (Month 3–4)
- Firebase Push Notification module (FCM HTTP v1)
- WebToDesk Control Panel for sending push notifications
- Deep link module (custom protocol registration)
- Badge counter module (Dock / taskbar)
- System tray module with custom menu
- Auto-updater with Hazel/GitHub Releases backend

### Phase 4 — Platform Expansion + Agency (Month 4–6)
- Linux `.AppImage` + `.deb` + `.rpm` builds (DigitalOcean Linux agent)
- macOS App Store build target + entitlements
- White-label / remove WebToDesk branding
- Agency tier: unlimited apps + client management dashboard
- Analytics dashboard: installs, DAU, events
- Custom domain for update server (`updates.yourapp.com`)
- API for programmatic app creation (for agencies / CI pipelines)

---

## 12 Competitive Positioning

| Competitor | Tier | Assessment |
|-----------|------|------------|
| **WebToDesk (This Project)** | Free/Pro | Electron-based. macOS/Win/Linux. SaaS with module system. 10-min delivery. Screen protection, biometric, FCM push built in. |
| Nativefier (npm CLI) | Free | CLI only — no dashboard. User must install Node.js. No signing, no modules. **Archived/unmaintained as of 2023.** |
| WebToDesk (TerminalDZ) | Free | Open source template, not SaaS. Manual clone + edit. No cloud build, no signing, no modules. |
| Fluid (macOS only) | Pro | macOS-only SSB. Native, not Electron. No Windows/Linux. No module system. Different audience. |
| Coherence X (macOS) | Pro | macOS-only. Chrome-based SSBs. $9.99/app. No SaaS, no cross-platform. |
| Tauri | Free | Developer framework (Rust + WebView). Not a SaaS — user writes code. Different audience (developers). |
| GoNative.io | Pro | Mobile-only (iOS/Android). Closest SaaS analogue — WebToDesk is the desktop equivalent. |

> **Strategic Gap:** There is NO dominant SaaS platform for URL-to-Desktop-App conversion with a module system, cloud build pipeline, and 10-minute delivery promise. WebToDesk occupies an uncontested category on desktop — the exact same gap that Appilix exploited on mobile.

---

## 13 Reference Code — Key Implementation Patterns

### `package.json` — electron-builder config

```json
{
  "name": "{{APP_ID}}",
  "productName": "{{APP_NAME}}",
  "version": "1.0.0",
  "main": "src/main.js",
  "build": {
    "appId": "{{APP_ID}}",
    "productName": "{{APP_NAME}}",
    "icon": "assets/icon",
    "mac": {
      "target": [{ "target": "dmg", "arch": ["universal"] }],
      "category": "public.app-category.productivity",
      "hardenedRuntime": true,
      "entitlements": "entitlements.mac.plist",
      "notarize": true
    },
    "win": { "target": "nsis", "signingHashAlgorithms": ["sha256"] },
    "linux": { "target": ["AppImage", "deb", "rpm"] },
    "publish": { "provider": "github", "releaseType": "release" }
  }
}
```

### `preload.js` — contextBridge (secure IPC)

```javascript
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('electronAPI', {
  // Badge
  setBadge: (count) => ipcRenderer.send('set-badge', count),
  // Navigation
  goBack:    () => ipcRenderer.send('nav-back'),
  goForward: () => ipcRenderer.send('nav-forward'),
  reload:    () => ipcRenderer.send('nav-reload'),
  // Auth
  authDone: (token) => ipcRenderer.send('auth-complete', token),
  // Deep link
  getDeepLinkUrl: () => ipcRenderer.invoke('get-deeplink-url'),
  // Notifications
  getNotifPermission: () => ipcRenderer.invoke('get-notif-permission'),
})
```

### `build-config.js` — Config Injection Script

```javascript
// Runs BEFORE electron-builder
const fs = require('fs')
const config = JSON.parse(process.env.USER_CONFIG || '{}')

// Inject into package.json
const pkg = JSON.parse(fs.readFileSync('package.json'))
pkg.name = config.appId
pkg.productName = config.appName
pkg.build.appId = config.appId
fs.writeFileSync('package.json', JSON.stringify(pkg, null, 2))

// Inject into user-config.json (read by main.js at runtime)
fs.writeFileSync('user-config.json', JSON.stringify({
  websiteUrl:    config.url,
  appName:       config.appName,
  windowWidth:   config.windowWidth  || 1280,
  windowHeight:  config.windowHeight || 800,
  modules:       config.enabledModules || [],
  customCSS:     config.customCSS || '',
  customJS:      config.customJS  || '',
}, null, 2))

// Copy user icon
fs.copyFileSync(config.iconPath, 'assets/icon.png')
```

### Deferred CI Example — macOS Build Agent

```yaml
# Example only — currently deferred in favor of local build flow in conversion-service
name: Build Electron App (Deferred)
on:
  workflow_dispatch:

jobs:
  build-mac:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - run: npm ci && npx electron-builder --mac --publish never
```

---

*WebToDesk — URL to Desktop App Conversion Platform | Architectural Blueprint · March 2026 · Internal Reference*