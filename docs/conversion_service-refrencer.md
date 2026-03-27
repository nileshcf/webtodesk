# WEBTODESK
### URL → Desktop Application Conversion Platform
> Complete Architectural Blueprint · Technical Stack · Feature Modules · Build Pipeline · Business Model

| | |
|---|---|
| **Platform Type** | SaaS — Website to Native Desktop App Converter |
| **Core Runtime** | Electron (Chromium + Node.js) |
| **Target OS** | macOS (primary) · Windows · Linux (roadmap) |
| **Frontend** | HTML5 · CSS3 · JavaScript (Vanilla) |
| **Backend** | PHP / Laravel · Firebase · Netlify / Vercel |
| **Billing** | Paddle (Merchant of Record) |
| **Document** | March 2026 — Internal Reference & Roadmap |

*Inspired by: Appilix mobile architecture | Built on: Electron + electron-builder | Ref repos: nileshcf/webtodesk · thecheesybit/desktop_app_project*

---

## 01 Executive Summary

WebToDesk is a SaaS platform that converts any publicly accessible website URL into a fully packaged, native-feeling desktop application for macOS, Windows, and (roadmap) Linux — in under 10 minutes, without any code changes to the target website. The user pastes a URL, names their app, uploads a logo, configures modules, and downloads a signed installer ready for distribution.

The technology foundation is Electron — the same framework powering VS Code, Slack, Figma, and Notion. Electron bundles Chromium (for rendering the website) with Node.js (for native OS access) into a single cross-platform executable. WebToDesk automates the otherwise manual process of creating, configuring, compiling, and packaging an Electron app — democratising desktop software distribution for anyone with a website.

> **Core Insight:** The average website owner cannot create a desktop app. Building one manually requires Electron knowledge, Node.js, a build toolchain, code signing certificates, CI/CD, and update infrastructure. WebToDesk collapses all of this into a 3-step web form. The same insight drove Appilix to $50K+ customers on mobile — the desktop opportunity is equally large and far less competitive.

The platform is built using familiar, low-cost, fast-to-deploy technologies: PHP/Laravel for the backend, HTML/JS/CSS for the dashboard frontend, Firebase for real-time job status and auth, Netlify/Vercel for static hosting, and Paddle for global billing. Build agents run on cloud VMs executing Electron-builder to compile platform-specific binaries on demand.

---

## 02 How It Works — End to End

### User Flow (10-Minute Promise)

| Step | Name | Description |
|------|------|-------------|
| **Step 1** | Create | User visits webtodesk.app, signs up (Firebase Auth), and pastes their website URL into the dashboard. The system performs a URL health-check (HTTP fetch) to confirm the site is reachable and mobile-friendly. |
| **Step 2** | Configure | User fills out app metadata: App Name, App ID (e.g. `com.mybrand.app`), Window size/min-size, custom icon (512×512 PNG), splash screen, and selects which native Modules to enable. |
| **Step 3** | Choose Platform | User selects macOS (`.dmg`), Windows (`.exe` installer), or both. Platform selection determines which build agent and code signing path is used. |
| **Step 4** | Build | Dashboard sends a build job to the backend queue. A cloud build agent picks it up, injects user config into the Electron template, runs electron-builder, and uploads the resulting installer to CDN storage. |
| **Step 5** | Download | Firebase Realtime Database signals the dashboard when the build is complete. User sees a download button + direct installer URL. For auto-updates, a Hazel update server URL is also provided. |

### System Architecture

```
┌─────────────────────────────────────────────────────────┐
│              USER BROWSER (Netlify/Vercel)              │
│   HTML Dashboard → Firebase Auth → Config Form → Build  │
└─────────────────────────────────────────────────────────┘
                       │ HTTPS POST /api/build
                       ▼
┌─────────────────────────────────────────────────────────┐
│          BACKEND API (PHP Laravel / Vercel Functions)   │
│    Validate config → Save to DB → Enqueue job → job_id  │
└─────────────────────────────────────────────────────────┘
                       │ Queue (Redis / Firebase RTDB)
          ┌────────────┼────────────┐
          ▼            ▼            ▼
   ┌────────────┐ ┌──────────┐ ┌──────────┐
   │  macOS VM  │ │Windows VM│ │ Linux VM │
   │   Xcode    │ │electron- │ │electron- │
   │  builder   │ │ builder  │ │ builder  │
   └────────────┘ └──────────┘ └──────────┘
          │            │            │
          └────────────┴────────────┘
                       │ Upload artifact
                       ▼
          ┌────────────────────────┐
          │     CDN / S3 Storage   │
          │  .dmg / .exe / .AppImage│
          └────────────────────────┘
                       │ Signed download URL
                       ▼
          ┌────────────────────────┐
          │   Firebase RTDB update │
          │   → Dashboard notified │
          └────────────────────────┘
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
| API Backend | PHP 8.x / Laravel 11 | REST API for user auth, app config CRUD, build job submission. Deployed on shared VPS or Railway.app |
| Job Queue | Redis + Laravel Queues | Async build job processing. `build:dispatch` → Redis queue → Worker picks up → runs build script |
| Database | MySQL 8 (via PlanetScale) | Users, apps, builds, module configs, subscription status |
| File Storage | AWS S3 / Cloudflare R2 | Stores uploaded icons, splash images, and compiled `.dmg/.exe/.AppImage` artifacts with signed URLs |
| CDN | Cloudflare | Static assets + artifact download acceleration. R2 + CDN = near-zero egress cost |
| Auth | Firebase Authentication | Email/password + Google Sign-In for dashboard login. JWT tokens validated by Laravel backend |
| Realtime | Firebase Realtime Database | Build job status (queued → building → done/failed) pushed to dashboard in real time without polling |
| Build Agents | GitHub Actions / DigitalOcean Droplets | macOS: GitHub Actions macOS runner. Windows/Linux: DigitalOcean Droplets with persistent Electron SDK |
| Frontend Host | Netlify / Vercel | Static HTML/CSS/JS dashboard hosted for free. API calls proxied to Laravel backend |
| Billing | Paddle | Merchant of Record — handles VAT, global tax, subscriptions, refunds. Paddle.js checkout overlay in dashboard |
| Email | Resend / Mailgun | Transactional: signup, build complete, password reset. Template-based HTML emails |
| Monitoring | Sentry + UptimeRobot | Error tracking for Laravel + Electron. Uptime alerts for API and build agents |

### 3.3 macOS Build Agent (Critical Path)

> **Why macOS Matters:** macOS `.dmg` creation and Apple code signing can **ONLY** be done on macOS (Apple restriction). GitHub Actions provides free macOS runners for public repos (`macos-latest`). For private/production use, a dedicated macOS VM on MacStadium or a GitHub Actions self-hosted runner is needed. Windows builds **CAN** be done on Linux using Wine+cross-compile or a Windows VM.

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

```js
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

Modules are the heart of the WebToDesk value proposition. Each module is an optional Node.js file that hooks into Electron's main process APIs to add native OS capabilities beyond what a browser can offer. Modules are enabled/disabled per user config and tree-shaken out of the build if not enabled.

---

### 🔒 Screen Protection `[Pro]`

Prevents screenshots, screen recording (QuickTime, OBS), and AirPlay mirroring of the app window.

```js
mainWindow.setContentProtection(true) // macOS + Windows
// On macOS: uses NSWindow setContentProtectionEnabled
// On Windows: WDA_EXCLUDEFROMCAPTURE via SetWindowDisplayAffinity
// Cannot be bypassed by user without admin access
// Also blocks window preview in macOS Mission Control
```

---

### 👆 Biometric Authentication `[Pro]`

Locks the app on launch or resume. Shows a native auth prompt (Touch ID, Face ID on Mac; Windows Hello, fingerprint on Windows). The website is hidden until auth succeeds.

```js
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

```js
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

```js
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

```js
require('electron').net.isOnline()
// On 'offline' event: mainWindow.loadFile('renderer/offline.html')
// On 'online' event: reload original URL
// Optional: electron-store caches page HTML for offline read
```

---

### 🔑 Custom Auth Screen `[Free]`

Shows a login/register page hosted on WebToDesk's renderer before loading the website.

```js
mainWindow.loadFile('renderer/auth-screen.html')
// auth-screen.html calls: window.electronAPI.authComplete(token)
// main.js receives IPC → loads website with auth cookie injected:
session.defaultSession.cookies.set({ url, name, value })
```

---

### 🔴 Badge Counter `[Free]`

Shows a numeric badge on the macOS Dock icon or Windows taskbar.

```js
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

### 🖥️ System Tray `[Free]`

Places the app in the system tray. App can minimise to tray and be restored without appearing in Dock/Taskbar.

```js
const { Tray, Menu } = require('electron')
const tray = new Tray(path.join(__dirname, 'assets/tray-icon.png'))
tray.setContextMenu(Menu.buildFromTemplate([
  { label: 'Open', click: () => mainWindow.show() },
  { label: 'Quit', click: () => app.quit() }
]))
mainWindow.on('minimize', () => mainWindow.hide())
```

---

### 🎨 CSS/JS Injection `[Free]`

Injects custom CSS or JavaScript into every page the WebView loads.

```js
mainWindow.webContents.on('did-finish-load', () => {
  mainWindow.webContents.insertCSS(config.customCSS)
  mainWindow.webContents.executeJavaScript(config.customJS)
})
```

---

### 🖨️ Print Support `[Free]`

```js
// In menu.js:
{ label: 'Print', accelerator: 'CmdOrCtrl+P',
  click: () => mainWindow.webContents.print() }

// Print to PDF:
mainWindow.webContents.printToPDF({}).then(data =>
  fs.writeFile(savePath, data))
```

---

### ⬅️ Navigation Controls `[Free]`

```js
const toolbar = new BrowserView()
app.addBrowserView(toolbar)
toolbar.webContents.loadFile('renderer/toolbar.html')
ipcMain.on('nav-back', () => mainWindow.webContents.goBack())
```

---

### 🔂 Single Instance Lock `[Free]`

```js
const gotLock = app.requestSingleInstanceLock()
if (!gotLock) { app.quit() }
app.on('second-instance', () => {
  if (mainWindow.isMinimized()) mainWindow.restore()
  mainWindow.focus()
})
```

---

### 🔍 Zoom Controls `[Free]`

```js
{ role: 'zoomIn' }, { role: 'zoomOut' }, { role: 'resetZoom' }

// Persist zoom:
const store = new Store()
mainWindow.webContents.setZoomFactor(store.get('zoom', 1))
mainWindow.webContents.on('zoom-changed', () => {
  store.set('zoom', mainWindow.webContents.getZoomFactor())
})
```

---

### 🛡️ Content Security Policy `[Pro]`

```js
session.defaultSession.webRequest.onHeadersReceived((details, cb) => {
  cb({ responseHeaders: {
    ...details.responseHeaders,
    'Content-Security-Policy': ["default-src 'self' " + config.websiteUrl]
  }})
})
```

---

### 📊 Analytics Bridge `[Pro]`

```js
app.on('ready', () => analytics.logEvent('app_open', { appId }))
mainWindow.webContents.on('did-navigate', (_, url) =>
  analytics.logEvent('page_view', { url }))
```

---

### 🪟 Window Switcher Protection `[Pro]`

Replaces the app thumbnail in Alt+Tab / Mission Control with a custom privacy screen.

```js
// macOS: setContentProtection(true) hides Mission Control preview
// Windows: SetWindowDisplayAffinity WDA_EXCLUDEFROMCAPTURE
// Custom: on 'hide' event, briefly show a splash BrowserWindow
```

---

### ⌨️ Global Shortcuts `[Free]`

```js
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
- Backend receives `POST /api/build` with `user_id`, app config JSON
- Validates: URL reachable, `appId` format (`com.brand.app`), icon is valid PNG ≥512px
- Creates build record in MySQL: `{id, user_id, status:'queued', platform, config_json}`
- Dispatches job to Redis queue: `BuildElectronApp::dispatch($build_id)`
- Returns `job_id` to frontend; Firebase RTDB node `builds/{job_id}/status = 'queued'`

### Phase 2 — Template Cloning & Config Injection
- Worker pulls job from queue. Clones master template to `/tmp/build-{job_id}/`
- Replaces placeholders in: `package.json`, `src/main.js`, `assets/`, `modules/loader.js`, `renderer/splash.html`, `electron-builder.yml`

### Phase 3 — Dependency Install
- Runs: `npm install --production` in `/tmp/build-{job_id}/`
- Uses cached `node_modules` layer (Docker or rsync cache) — saves 2-3 minutes
- Native modules (`keytar`, `node-addon-api`) rebuilt via `electron-rebuild`

### Phase 4 — Compilation

| Platform | Command | Output | Time |
|----------|---------|--------|------|
| macOS | `electron-builder --mac` | `.dmg` (arm64 + x64 universal) | 3–6 min |
| Windows | `electron-builder --win` | NSIS `.exe` + portable `.exe` | 2–4 min |
| Linux | `electron-builder --linux` | `.AppImage` + `.deb` + `.rpm` | 2–3 min |

> **Note:** Windows builds CAN run on Linux. macOS CANNOT cross-compile — must use `macos-latest` runner.

### Phase 5 — Code Signing

| Platform | Requirements | Free Tier |
|----------|-------------|-----------|
| macOS | `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID` + auto-notarization | Unsigned (Gatekeeper warning) |
| Windows | EV Code Signing Certificate (`.pfx`) or Azure Trusted Signing | Unsigned (SmartScreen warning) |

### Phase 6 — Upload & Delivery
- Artifacts uploaded to S3/R2: `builds/{user_id}/{app_id}/{version}/{filename}`
- Pre-signed download URLs generated (expire in 48h for free; permanent for Pro)
- Firebase RTDB updated with `status:'done'`, download URLs, file sizes
- Email sent via Resend: *"Your app is ready to download!"*
- Auto-update manifest (`latest.yml`) uploaded for `electron-updater` compatibility

---

## 07 Dashboard — Frontend Architecture

The WebToDesk dashboard is a static HTML/CSS/JS SPA hosted on Netlify. Vanilla JS — no framework overhead.

### Page Structure

| Page | File | Description |
|------|------|-------------|
| Landing Page | `index.html` | Marketing page — hero, features, pricing, testimonials |
| Sign Up / Login | `auth.html` | Firebase Auth UI — email/password + Google Sign-In |
| Dashboard Home | `dashboard.html` | Lists all user's apps with status, last build date, download links |
| App Creator | `create.html` | Multi-step form: URL → Name & Icon → Window Settings → Modules → Platform → Build |
| App Editor | `app/{id}.html` | Edit existing app config, re-trigger builds, view build history |
| Build Status | `build/{id}.html` | Real-time build log viewer via Firebase RTDB listener |
| Control Panel | `control.html` | Push notification sender, badge reset, analytics dashboard (Pro) |
| Billing | `billing.html` | Paddle checkout embed, current plan, upgrade/downgrade options |
| Docs | `docs/` | Module guides, code signing walkthrough, API reference |

### Real-Time Build Status Pattern

```js
import { getDatabase, ref, onValue } from 'firebase/database'

const db = getDatabase()
const buildRef = ref(db, `builds/${jobId}`)

onValue(buildRef, (snapshot) => {
  const data = snapshot.val()
  if (!data) return

  updateStatusBadge(data.status) // queued | building | done | failed

  if (data.status === 'done') {
    showDownloadButtons(data.urls) // { dmg, exe, appimage }
    showBuildStats(data.size, data.built_at)
    sendSuccessToast('Your app is ready!')
  }

  if (data.status === 'failed') {
    showErrorMessage(data.error)
    showRetryButton()
  }
})
```

---

## 08 Complete Feature Matrix

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
| Window Switcher Protection | Pro | Hides app from Alt+Tab/Mission Control previews |
| Analytics Dashboard | Pro | Firebase Analytics + BigQuery export |
| Content Security Policy | Pro | Strict CSP via `session.webRequest.onHeadersReceived` |
| Universal Binary (macOS) | Pro | arm64+x64 fat binary. Native on Apple Silicon + Intel. |
| White-Label / Remove Branding | Pro | Removes 'Made with WebToDesk' from About screen |
| Custom About Page | Pro | Configurable About dialog |
| macOS App Store Build | Pro | Sandboxed build with entitlements |
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
| **Paddle MoR** | Handles VAT, GST, sales tax in 150+ countries. Zero tax compliance burden. |
| **Module Upsells** | New modules released Pro-only. Creates continuous upgrade incentives. |
| **Build Credits (Future)** | Free tier limited to N builds/month. Extra builds purchasable. |

---

## 11 Product Roadmap — Phased Plan

### Phase 1 — Core MVP (Month 1–2)
- Electron template: `main.js`, `preload.js`, `menu.js`
- Build pipeline: Laravel API + Redis queue + GitHub Actions build agents
- Dashboard: URL input → icon upload → build → download
- Firebase Auth: email/password sign-up
- macOS `.dmg` + Windows `.exe` output (unsigned)
- Netlify hosting for dashboard static files
- Paddle checkout integration for Free → Pro

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

```js
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

```js
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

### GitHub Actions — macOS Build Agent

```yaml
# .github/workflows/build.yml
name: Build Electron App
on:
  workflow_dispatch:
    inputs:
      build_id:    { required: true }
      user_config: { required: true }

jobs:
  build-mac:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }

      - name: Inject config and build
        env:
          USER_CONFIG:                 ${{ inputs.user_config }}
          APPLE_ID:                    ${{ secrets.APPLE_ID }}
          APPLE_APP_SPECIFIC_PASSWORD: ${{ secrets.APPLE_APP_SPECIFIC_PASSWORD }}
          APPLE_TEAM_ID:               ${{ secrets.APPLE_TEAM_ID }}
          CSC_LINK:                    ${{ secrets.MAC_CERT_P12_BASE64 }}
          CSC_KEY_PASSWORD:            ${{ secrets.MAC_CERT_PASSWORD }}
        run: |
          node build-config.js
          npm install
          npx electron-builder --mac --publish never

      - name: Upload to S3
        run: aws s3 cp dist/*.dmg s3://webtodesk-builds/${{ inputs.build_id }}/
```

---

*WebToDesk — URL to Desktop App Conversion Platform | Architectural Blueprint · March 2026 · Internal Reference*