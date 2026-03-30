# ELECTRON_SKILL.md — WebToDesk Module Injection
*(Enhanced with full Electron API reference — compiled from https://www.electronjs.org/docs/latest/)*

> **Scope:** Generating correct, production-ready Electron JavaScript code blocks
> that are injected into `main.js`, `preload.js`, `config.js`, and `package.json`
> by `ConversionService.java` via the `ElectronModule` interface.

---

## 1. Architecture Contract

Every module is a **self-contained JS string** returned by `ElectronModule.render(FeatureConfig config)`.

| Target constant | Injected into | Injection point |
|---|---|---|
| `MAIN_JS` | `main.js` | After `mainWindow` is created, before `app.whenReady` closes |
| `PRELOAD_JS` | `preload.js` | After `contextBridge` header, before footer |
| `CONFIG_JS_EXTRA` | `config.js` | Appended as extra exports |
| `PACKAGE_JSON_EXTRA` | `package.json` | Merged via Jackson `ObjectNode` — never string-concat |

**Golden rule:** Each code block must be **idempotent** — running it twice must not break the app.

---

## 2. Process Model (Critical Foundation)

Electron has three process types. Understanding which process owns what API prevents all class-of-bugs.

| Process | Runtime | Owns | Can access |
|---|---|---|---|
| **Main** | Node.js | App lifecycle, windows, native APIs | All Electron + Node APIs |
| **Renderer** | Chromium | Web page UI | DOM, Web APIs, contextBridge-exposed APIs only |
| **Preload** | Node.js (in renderer context) | Bridge between main and renderer | Limited Node + `contextBridge`, `ipcRenderer` |
| **Utility** | Node.js | Heavy background tasks (CPU/IO) | Node APIs + `parentPort` IPC |

**Communication rules:**
- Renderer → Main: `ipcRenderer.invoke()` → `ipcMain.handle()` *(two-way, async)*
- Renderer → Main: `ipcRenderer.send()` → `ipcMain.on()` *(one-way, fire-and-forget)*
- Main → Renderer: `mainWindow.webContents.send('event', data)` → `ipcRenderer.on('event', cb)`
- **Never** expose `ipcRenderer` directly via `contextBridge` — wrap every call in a named function.

---

## 3. Complete Electron API Reference

Every module available in the current stable release. Main-process-only, renderer-only, and shared are noted.

### 3.1 Core App & Window APIs

#### `app` *(main)*
```javascript
const { app } = require('electron');

app.whenReady().then(() => { /* safe to create windows + register shortcuts */ });
app.quit();
app.getPath('userData');      // platform-safe user data dir — use for license.json, cache
app.getPath('temp');          // OS temp dir
app.getPath('documents');     // user Documents folder
app.getPath('downloads');     // user Downloads folder
app.getPath('desktop');       // user Desktop folder
app.getVersion();             // reads from package.json version
app.getName();                // reads from package.json name
app.isQuitting = false;       // custom flag pattern for tray minimize-to-tray
app.requestSingleInstanceLock(); // prevent multiple instances
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
app.on('activate', () => { /* macOS dock click — recreate window if needed */ });
app.on('will-quit', () => { globalShortcut.unregisterAll(); });
app.on('before-quit', () => { app.isQuitting = true; });
app.on('second-instance', () => { mainWindow?.restore(); mainWindow?.focus(); });
app.on('open-url', (event, url) => { /* macOS URL scheme handler */ });
app.on('open-file', (event, filePath) => { /* macOS drag-to-dock */ });
```

#### `BrowserWindow` *(main)*
```javascript
const { BrowserWindow } = require('electron');

const win = new BrowserWindow({
  // Size & Position
  width: 1200, height: 800,
  minWidth: 400, minHeight: 300,
  x, y,
  center: true,

  // Chrome & Frame
  frame: true,                  // false = completely frameless
  titleBarStyle: 'default',     // 'hidden'|'hiddenInset'|'customButtonsOnHover' (macOS)
  titleBarOverlay: true,        // Windows/macOS overlay traffic-light buttons
  trafficLightPosition: { x, y }, // macOS only

  // Appearance
  transparent: false,
  opacity: 1.0,                 // 0.0–1.0
  backgroundColor: '#fff',
  vibrancy: 'under-window',     // macOS 10.14+
  backgroundMaterial: 'acrylic', // Windows 11 build 22000+
  roundedCorners: true,         // macOS 12+ / Windows 11

  // Behaviour
  alwaysOnTop: false,
  skipTaskbar: false,
  resizable: true,
  movable: true,
  minimizable: true,
  maximizable: true,
  closable: true,
  focusable: true,
  fullscreen: false,
  kiosk: false,
  autoHideMenuBar: false,
  icon: getIconPath(),

  // Security — these three are non-negotiable
  webPreferences: {
    preload: path.join(__dirname, 'preload.js'),
    nodeIntegration: false,     // ALWAYS false
    contextIsolation: true,     // ALWAYS true
    sandbox: true,              // strongly recommended
    webSecurity: true,          // never disable in production
    allowRunningInsecureContent: false,
  }
});

// Key methods
win.loadURL('https://example.com');
win.loadFile('index.html');
win.reload();
win.close();
win.destroy();                  // force-close, skips 'close' event
win.show(); win.hide();
win.focus(); win.blur();
win.minimize(); win.maximize(); win.unmaximize(); win.restore();
win.setFullScreen(bool);
win.setAlwaysOnTop(true, 'floating');
win.setContentProtection(true); // hide from screen capture APIs
win.setOpacity(0.9);
win.setResizable(false);
win.setTitle('My App');
win.setProgressBar(0.5);        // taskbar progress indicator (Windows/macOS)
win.setOverlayIcon(image, desc); // Windows taskbar overlay icon
win.setVibrancy('under-window'); // macOS
win.setBackgroundMaterial('acrylic'); // Windows 11
win.setMenuBarVisibility(false);
win.webContents.send('channel', data);
win.webContents.executeJavaScript('document.title');
win.webContents.insertCSS('body { background: red; }');
win.webContents.openDevTools();
win.webContents.goBack(); win.webContents.goForward();
win.webContents.reload(); win.webContents.stop();

// Key events
win.on('close', (event) => { event.preventDefault(); win.hide(); }); // intercept close
win.on('closed',   () => { /* window destroyed */ });
win.on('focus',    () => {}); win.on('blur', () => {});
win.on('resize',   () => {}); win.on('move', () => {});
win.on('enter-full-screen', () => {}); win.on('leave-full-screen', () => {});
win.webContents.on('did-finish-load',     () => { /* page loaded */ });
win.webContents.on('did-fail-load',       (e, code, desc) => { /* handle offline */ });
win.webContents.on('will-navigate',       (event, url) => { event.preventDefault(); });
win.webContents.on('did-navigate',        (event, url, statusCode) => {});
win.webContents.on('did-navigate-in-page',(event, url, isMainFrame) => {}); // SPA hash change
win.webContents.on('dom-ready',           () => {});
win.webContents.on('page-title-updated',  (event) => { event.preventDefault(); }); // lock title
win.webContents.on('before-input-event',  (event, input) => {}); // input.key, .modifiers
win.webContents.on('context-menu',        (event, params) => {}); // params.x, .y, .selectionText
win.webContents.on('media-started-playing', () => {});
win.webContents.on('media-paused',          () => {});
win.webContents.setWindowOpenHandler(({ url }) => {
  // Preferred over 'new-window' event (deprecated)
  return { action: 'deny' }; // or { action: 'allow', overrideBrowserWindowOptions: {} }
});
```

---

### 3.2 IPC — Inter-Process Communication

#### `ipcMain` *(main)*
```javascript
const { ipcMain } = require('electron');

// Two-way async (preferred for request-response)
ipcMain.handle('channel', async (event, arg1, arg2) => {
  const sender = event.sender; // WebContents of the calling renderer
  return result;               // resolves the renderer's invoke() promise
});

// One-way (fire-and-forget from renderer)
ipcMain.on('channel', (event, ...args) => {
  event.reply('response-channel', data); // reply directly to sender
});

// Cleanup
ipcMain.removeHandler('channel');
ipcMain.removeAllListeners('channel');
```

#### `ipcRenderer` *(preload only — never expose directly)*
```javascript
const { ipcRenderer } = require('electron');

// Two-way (use inside contextBridge wrapper functions only)
const result = await ipcRenderer.invoke('channel', arg1, arg2);

// One-way send
ipcRenderer.send('channel', data);

// Listen for main-pushed events
ipcRenderer.on('event', (_, data) => { /* … */ });
ipcRenderer.once('event', (_, data) => { /* fires once then auto-removes */ });
ipcRenderer.removeAllListeners('event');
```

#### `contextBridge` *(preload only)*
```javascript
const { contextBridge, ipcRenderer } = require('electron');

// CORRECT: wrap every IPC call, never expose ipcRenderer itself
contextBridge.exposeInMainWorld('electronAPI', {
  readFile:      (path) => ipcRenderer.invoke('fs-read-file', path),
  writeFile:     (path, data) => ipcRenderer.invoke('fs-write-file', path, data),
  logEvent:      (msg) => ipcRenderer.send('log', msg),
  // Listener — return cleanup function so renderer can unsubscribe
  onThemeChange: (cb) => {
    const handler = (_, data) => cb(data);
    ipcRenderer.on('wtd-theme-changed', handler);
    return () => ipcRenderer.removeListener('wtd-theme-changed', handler);
  },
});
```

---

### 3.3 Native UI APIs

#### `dialog` *(main)*
```javascript
const { dialog } = require('electron');

// File open
const result = await dialog.showOpenDialog(win, {
  title: 'Open File',
  defaultPath: app.getPath('documents'),
  filters: [{ name: 'All Files', extensions: ['*'] }],
  properties: ['openFile', 'multiSelections', 'createDirectory']
});
// result.canceled, result.filePaths[]

// File save
const result = await dialog.showSaveDialog(win, {
  title: 'Save File',
  defaultPath: path.join(app.getPath('documents'), 'untitled.txt'),
  filters: [{ name: 'Text Files', extensions: ['txt'] }]
});
// result.canceled, result.filePath

// Message / confirm dialog
const { response } = await dialog.showMessageBox(win, {
  type: 'question',   // 'none'|'info'|'error'|'question'|'warning'
  buttons: ['OK', 'Cancel'],
  title: 'Confirm',
  message: 'Are you sure?',
  detail: 'This cannot be undone.',
  checkboxLabel: "Don't show again",
  defaultId: 0,
  cancelId: 1
});
// response = button index clicked

// Synchronous error dialog (use sparingly — blocks event loop)
dialog.showErrorBox('Error Title', 'Error content');
```

#### `Menu` and `MenuItem` *(main)*
```javascript
const { Menu, MenuItem } = require('electron');

const template = [
  {
    label: 'File',
    submenu: [
      { label: 'New', accelerator: 'CmdOrCtrl+N', click: () => {} },
      { type: 'separator' },
      { role: 'quit' }
    ]
  },
  { role: 'editMenu' },   // full built-in Edit menu
  { role: 'viewMenu' },   // full built-in View menu
  { role: 'windowMenu' }, // full built-in Window menu (macOS)
];
const menu = Menu.buildFromTemplate(template);
Menu.setApplicationMenu(menu);
Menu.setApplicationMenu(null); // hide menu bar entirely

// Context menu popup
const ctxMenu = Menu.buildFromTemplate([
  { label: 'Copy',   role: 'copy' },
  { label: 'Paste',  role: 'paste' },
  { type: 'separator' },
  { label: 'Reload', click: () => win.reload() },
]);
ctxMenu.popup({ window: win, x: 100, y: 200 });

// All built-in MenuItem roles:
// undo, redo, cut, copy, paste, pasteAndMatchStyle, delete, selectAll,
// reload, forceReload, toggleDevTools, resetZoom, zoomIn, zoomOut,
// togglefullscreen, window, minimize, close, help, about,
// services (macOS), hide, hideOthers, unhide, quit,
// startSpeaking, stopSpeaking (macOS), zoom, front,
// appMenu, fileMenu, editMenu, viewMenu, shareMenu,
// recentDocuments, toggleTabBar, selectNextTab, selectPreviousTab,
// mergeAllWindows, clearRecentDocuments, moveTabToNewWindow, windowMenu
```

#### `Tray` *(main)*
```javascript
const { Tray, Menu } = require('electron');

const tray = new Tray(path.join(__dirname, 'build/icon.png'));
tray.setToolTip('My App');
tray.setTitle('My App'); // macOS — text next to tray icon

tray.setContextMenu(Menu.buildFromTemplate([
  { label: 'Show App', click: () => win.show() },
  { type: 'separator' },
  { label: 'Quit', click: () => { app.isQuitting = true; app.quit(); } }
]));

tray.on('click',        () => win.isVisible() ? win.hide() : win.show());
tray.on('double-click', () => win.show());
tray.on('right-click',  () => tray.popUpContextMenu());

// Update icon dynamically (e.g. status indicator)
tray.setImage(path.join(__dirname, 'build/icon-active.png'));
tray.destroy(); // cleanup
```

#### `Notification` *(main)*
```javascript
const { Notification } = require('electron');

if (Notification.isSupported()) {
  const notif = new Notification({
    title: 'Hello',
    body: 'World',
    icon: path.join(__dirname, 'build/icon.png'),
    silent: false,
    urgency: 'normal',      // Linux: 'low'|'normal'|'critical'
    timeoutType: 'default', // Windows: 'default'|'never'
    actions: [{ type: 'button', text: 'Action' }], // macOS
    closeButtonText: 'Dismiss', // macOS
  });
  notif.show();
  notif.on('click',  () => win.show());
  notif.on('close',  () => {});
  notif.on('action', (event, index) => {}); // macOS action buttons
}
```

#### `shell` *(main + renderer via IPC)*
```javascript
const { shell } = require('electron');

// ALWAYS validate URL against allowlist before calling openExternal
await shell.openExternal('https://webtodesk.com/pricing');
await shell.openPath('/path/to/file');    // open in default app
shell.showItemInFolder('/path/to/file');  // reveal in Finder/Explorer
shell.beep();                             // system audio beep
shell.moveItemToTrash('/path/to/file');   // cross-platform delete to trash
```
**Security:** Never pass user-controlled URLs to `openExternal` without allowlist check.

---

### 3.4 Session, Protocol & Network

#### `session` *(main)*
```javascript
const { session } = require('electron');
const ses = session.defaultSession;
// Or partition for isolated storage: session.fromPartition('persist:myapp')

// Permission handler — grant/deny renderer permission requests
ses.setPermissionRequestHandler((webContents, permission, cb, details) => {
  const allowed = ['notifications', 'clipboard-read', 'media'];
  cb(allowed.includes(permission));
});

// Intercept/block requests (ad blocking, offline cache, analytics blocking)
ses.webRequest.onBeforeRequest({ urls: ['*://*/*'] }, (details, cb) => {
  cb({ cancel: details.url.includes('tracker.js') });
});

// Modify request headers
ses.webRequest.onBeforeSendHeaders((details, cb) => {
  details.requestHeaders['X-Custom-Header'] = 'value';
  cb({ requestHeaders: details.requestHeaders });
});

// Modify response headers (e.g. CSP override)
ses.webRequest.onHeadersReceived((details, cb) => {
  cb({ responseHeaders: {
    ...details.responseHeaders,
    'Content-Security-Policy': ["default-src 'self'"]
  }});
});

// Cookie management
await ses.cookies.set({ url: 'https://example.com', name: 'token', value: 'abc' });
const cookies = await ses.cookies.get({ url: 'https://example.com' });
await ses.cookies.remove('https://example.com', 'token');

// Cache & storage management
await ses.clearCache();
await ses.clearStorageData({ storages: ['cookies', 'localstorage', 'indexdb'] });

// Download management
ses.on('will-download', (event, item, webContents) => {
  item.setSavePath(path.join(app.getPath('downloads'), item.getFilename()));
  item.on('updated', (e, state) => { /* 'progressing'|'interrupted' */ });
  item.once('done',  (e, state) => { /* 'completed'|'cancelled'|'interrupted' */ });
});
```

#### `protocol` *(main)*
```javascript
const { protocol } = require('electron');

app.whenReady().then(() => {
  // Register custom URL scheme (e.g. wtd://)
  protocol.registerFileProtocol('wtd', (request, cb) => {
    const filePath = request.url.replace('wtd://', '');
    cb(path.normalize(path.join(__dirname, filePath)));
  });

  // Intercept https for offline cache serving
  protocol.interceptHttpProtocol('https', (request, cb) => {
    cb({ url: request.url }); // passthrough or serve from cache
  });
});
// Must register in whenReady, before any window loads
```

#### `net` *(main)* — Chromium's network stack (respects proxy, sessions)
```javascript
const { net } = require('electron');

const request = net.request({
  method: 'GET',
  url: 'https://api.example.com/data',
  session: session.defaultSession,
});
request.on('response', (response) => {
  let body = '';
  response.on('data', (chunk) => { body += chunk; });
  response.on('end',  () => { console.log(JSON.parse(body)); });
});
request.end();
```

---

### 3.5 System APIs

#### `globalShortcut` *(main)*
```javascript
const { globalShortcut } = require('electron');

app.whenReady().then(() => {
  const ok = globalShortcut.register('CommandOrControl+Shift+K', () => {
    win.show();
  });
  if (!ok) console.warn('Shortcut registration failed — may be OS-reserved');
  console.log(globalShortcut.isRegistered('CommandOrControl+Shift+K'));
});

app.on('will-quit', () => {
  globalShortcut.unregister('CommandOrControl+Shift+K');
  globalShortcut.unregisterAll(); // always call on quit
});
```

**Full accelerator token list:**
`Ctrl` / `Command` / `CommandOrControl`, `Alt` / `Option`, `Shift`, `Super`,
`F1–F24`, `PrintScreen`, `Insert`, `Delete`, `Home`, `End`, `PageUp`, `PageDown`,
`ArrowUp` / `ArrowDown` / `ArrowLeft` / `ArrowRight`,
`Space`, `Tab`, `Backspace`, `Enter`, `Escape`,
`VolumeUp` / `VolumeDown` / `VolumeMute`,
`MediaNextTrack` / `MediaPreviousTrack` / `MediaStop` / `MediaPlayPause`,
`A–Z`, `0–9`, `Numpad0–9`, `NumpadAdd` / `NumpadSubtract` / etc.

#### `nativeTheme` *(main)*
```javascript
const { nativeTheme } = require('electron');

nativeTheme.themeSource = 'system'; // 'system'|'light'|'dark' — force override OS theme
console.log(nativeTheme.shouldUseDarkColors);
console.log(nativeTheme.shouldUseHighContrastColors); // accessibility
console.log(nativeTheme.shouldUseInvertedColorScheme); // legacy Windows

nativeTheme.on('updated', () => {
  mainWindow.webContents.send('wtd-theme-changed', {
    isDark: nativeTheme.shouldUseDarkColors
  });
});
```

#### `clipboard` *(main + renderer)*
```javascript
const { clipboard } = require('electron');

clipboard.readText('clipboard');          // 'clipboard'|'selection' (Linux selection buffer)
clipboard.writeText('Hello', 'clipboard');
clipboard.readHTML();
clipboard.writeHTML('<b>bold</b>');
clipboard.readImage();                    // returns NativeImage
clipboard.writeImage(nativeImage);
clipboard.readRTF();
clipboard.writeRTF(rtf);
clipboard.readBookmark();                 // macOS: { title, url }
clipboard.clear();
const formats = clipboard.availableFormats(); // ['text/plain', 'text/html', ...]
```

#### `screen` *(main + renderer)*
```javascript
const { screen } = require('electron');

const primary   = screen.getPrimaryDisplay();
const { width, height } = primary.workAreaSize; // excludes taskbar
const all       = screen.getAllDisplays();
const cursorPos = screen.getCursorScreenPoint();
const nearest   = screen.getDisplayNearestPoint(cursorPos);

screen.on('display-added',          (e, display) => {});
screen.on('display-removed',        (e, display) => {});
screen.on('display-metrics-changed',(e, display, changedMetrics) => {});
```

#### `powerMonitor` *(main)*
```javascript
const { powerMonitor } = require('electron');

powerMonitor.on('suspend',       () => { /* machine going to sleep */ });
powerMonitor.on('resume',        () => { /* machine waking up */ });
powerMonitor.on('on-ac',         () => { /* plugged in */ });
powerMonitor.on('on-battery',    () => { /* unplugged */ });
powerMonitor.on('lock-screen',   () => { /* screen locked */ });
powerMonitor.on('unlock-screen', () => { /* screen unlocked */ });
powerMonitor.on('shutdown',      () => { /* OS shutting down */ });

console.log(powerMonitor.getSystemIdleTime());        // seconds idle
console.log(powerMonitor.getSystemIdleState(60));     // 'active'|'idle'|'locked'|'unknown'
```

#### `powerSaveBlocker` *(main)*
```javascript
const { powerSaveBlocker } = require('electron');
// 'prevent-app-suspension' | 'prevent-display-sleep'
const id = powerSaveBlocker.start('prevent-display-sleep');
console.log(powerSaveBlocker.isStarted(id)); // true
powerSaveBlocker.stop(id);
```

#### `systemPreferences` *(main)* — OS system settings
```javascript
const { systemPreferences } = require('electron');

// macOS: media access
const status = await systemPreferences.askForMediaAccess('microphone'); // 'granted'|'denied'
systemPreferences.getMediaAccessStatus('camera'); // 'not-determined'|'granted'|'denied'|'restricted'
systemPreferences.getAccentColor(); // hex RGBA string

// macOS: read NSUserDefaults
systemPreferences.getUserDefault('AppleInterfaceStyle', 'string');

// Windows: system colors
systemPreferences.getColor('desktop'); // hex color string
```

#### `safeStorage` *(main)* — OS-level encrypted storage
```javascript
const { safeStorage } = require('electron');

if (safeStorage.isEncryptionAvailable()) {
  // Encrypt: returns Buffer, store to disk
  const encrypted = safeStorage.encryptString('secret-value');
  fs.writeFileSync(path.join(app.getPath('userData'), 'secret.enc'), encrypted);

  // Decrypt: read Buffer from disk
  const raw       = fs.readFileSync(path.join(app.getPath('userData'), 'secret.enc'));
  const decrypted = safeStorage.decryptString(raw);
}
// Use for: API keys, license tokens, auth tokens, any secret that must survive app restarts
```

---

### 3.6 File & OS Integration

#### `desktopCapturer` *(main/renderer)* — screen/window capture source list
```javascript
const { desktopCapturer } = require('electron');
const sources = await desktopCapturer.getSources({
  types: ['window', 'screen'],
  thumbnailSize: { width: 150, height: 150 }
});
// sources[i].id, .name, .thumbnail (NativeImage), .appIconImage
// Use with navigator.mediaDevices.getUserMedia in renderer for screen recording
// WebToDesk: enumerate in ScreenCaptureModule to detect/block active capture sources
```

#### `shell` file methods — see §3.3

---

### 3.7 Utility Process — heavy background tasks
```javascript
// main.js
const { utilityProcess } = require('electron');
const child = utilityProcess.fork(path.join(__dirname, 'worker.js'), [], {
  serviceName: 'background-worker',
  env: { ...process.env }
});
child.postMessage({ task: 'compute' });
child.on('message', (data) => { /* handle result */ });
child.kill();

// worker.js
const { parentPort } = require('electron');
parentPort.on('message', ({ task }) => {
  // do heavy work
  parentPort.postMessage({ result: 'done' });
});
```
**WebToDesk use case:** Offload large file downloads, intensive post-build processing, or update checks.

---

### 3.8 `nativeImage` — image manipulation
```javascript
const { nativeImage } = require('electron');

const img  = nativeImage.createFromPath(path.join(__dirname, 'icon.png'));
const img2 = nativeImage.createFromBuffer(buffer, { width: 32, height: 32, scaleFactor: 1.0 });
const img3 = nativeImage.createFromDataURL('data:image/png;base64,...');

const trayIcon  = img.resize({ width: 16, height: 16 });
const pngBuffer = img.toPNG();
const jpegBuf   = img.toJPEG(80);
const dataUrl   = img.toDataURL();
const empty     = nativeImage.createEmpty(); // transparent placeholder
const { width, height } = img.getSize();
```
**WebToDesk use:** Build tray icons, splash logos, and watermark images from R2-fetched data.

---

### 3.9 `autoUpdater` *(main)*
```javascript
// Built-in uses Squirrel (Windows/macOS only, no Linux).
// Use electron-updater npm package for cross-platform support:
const { autoUpdater } = require('electron-updater');

autoUpdater.setFeedURL({ url: 'https://your-update-server.com/releases' });
autoUpdater.autoDownload        = true;
autoUpdater.autoInstallOnAppQuit = true;
autoUpdater.allowDowngrade      = false;
autoUpdater.logger              = require('electron-log'); // optional logging

autoUpdater.checkForUpdates();
autoUpdater.checkForUpdatesAndNotify(); // shows OS system notification automatically

autoUpdater.on('checking-for-update',  () => {});
autoUpdater.on('update-available',     (info) => { /* info.version */ });
autoUpdater.on('update-not-available', (info) => {});
autoUpdater.on('error',                (err)  => { console.error(err.message); });
autoUpdater.on('download-progress',    (prog) => { /* prog.percent, .bytesPerSecond */ });
autoUpdater.on('update-downloaded',    (info) => {
  autoUpdater.quitAndInstall(/*isSilent=*/true, /*isForceRunAfter=*/true);
});
```

---

### 3.10 `crashReporter` *(main + renderer)*
```javascript
const { crashReporter } = require('electron');
crashReporter.start({
  submitURL: 'https://your-crash-server.com/submit',
  uploadToServer: true,
  compress: true,
  extra: { version: app.getVersion(), tier: 'pro' }
});
```

---

### 3.11 `contentTracing` *(main)* — Chromium performance profiling
```javascript
const { contentTracing } = require('electron');
await contentTracing.startRecording({ included_categories: ['*'] });
const tracePath = await contentTracing.stopRecording();
// Open tracePath .json in chrome://tracing
```

---

### 3.12 `inAppPurchase` *(macOS App Store only)*
```javascript
const { inAppPurchase } = require('electron');
if (inAppPurchase.canMakePayments()) {
  const products = await inAppPurchase.getProducts(['com.myapp.pro']);
  await inAppPurchase.purchaseProduct('com.myapp.pro');
  inAppPurchase.on('transactions-updated', (e, transactions) => {});
}
```

---

### 3.13 `pushNotifications` *(macOS 10.14+)*
```javascript
const { pushNotifications } = require('electron');
const token = await pushNotifications.registerForAPNSNotifications();
pushNotifications.unregisterForAPNSNotifications();
```

---

### 3.14 `TouchBar` *(macOS Touch Bar)*
```javascript
const { TouchBar } = require('electron');
const { TouchBarButton, TouchBarLabel, TouchBarSpacer,
        TouchBarSegmentedControl, TouchBarSlider,
        TouchBarPopover, TouchBarScrubber } = TouchBar;

const touchBar = new TouchBar({
  items: [
    new TouchBarButton({ label: 'Back', click: () => win.webContents.goBack() }),
    new TouchBarSpacer({ size: 'flexible' }),
    new TouchBarLabel({ label: 'My App' }),
  ]
});
win.setTouchBar(touchBar);
```

---

### 3.15 `webContents` Navigation Events Reference
```javascript
win.webContents.on('will-navigate',           (event, url) => {});
win.webContents.on('did-start-navigation',    (event, url, isInPlace, isMainFrame) => {});
win.webContents.on('did-navigate',            (event, url, statusCode) => {});
win.webContents.on('did-navigate-in-page',    (event, url, isMainFrame) => {}); // SPA
win.webContents.on('did-finish-load',         () => {});
win.webContents.on('did-fail-load',           (e, code, desc, url) => {});
win.webContents.on('did-start-loading',       () => {});
win.webContents.on('did-stop-loading',        () => {});
win.webContents.on('dom-ready',               () => {});
win.webContents.on('page-title-updated',      (event, title) => { event.preventDefault(); });
win.webContents.on('page-favicon-updated',    (event, favicons) => {});
win.webContents.on('before-input-event',      (event, input) => { /* input.key, .type, .modifiers */ });
win.webContents.on('context-menu',            (event, params) => { /* params.x, .y, .selectionText */ });
win.webContents.on('media-started-playing',   () => {});
win.webContents.on('media-paused',            () => {});
```

---

## 4. Electron API Quick Reference Card

### Main Process Imports
```javascript
const {
  app, BrowserWindow, BaseWindow,
  ipcMain, dialog, shell,
  Tray, Menu, MenuItem,
  globalShortcut, nativeTheme, nativeImage,
  clipboard, session, protocol, net,
  screen, powerMonitor, powerSaveBlocker,
  systemPreferences, safeStorage,
  Notification, desktopCapturer,
  utilityProcess, crashReporter,
  contentTracing, inAppPurchase,
  pushNotifications, TouchBar,
  autoUpdater                    // built-in Squirrel — prefer electron-updater npm package
} = require('electron');

const path = require('node:path');
const fs   = require('node:fs');
const os   = require('node:os');
```

### Preload Process Imports
```javascript
const { contextBridge, ipcRenderer, webFrame } = require('electron');
```

### Renderer Process (contextBridge access only)
```javascript
// Access only what's been exposed via contextBridge.exposeInMainWorld()
window.electronAPI.methodName();
```

---

## 5. Module Code Templates

### 5.1 Splash Screen (`MAIN_JS`)
```javascript
// ── SPLASH SCREEN MODULE ──────────────────────────────
let splashWindow = null;
function createSplashWindow() {
  splashWindow = new BrowserWindow({
    width: 400, height: 300,
    frame: false, transparent: true,
    alwaysOnTop: true, skipTaskbar: true,
    webPreferences: { nodeIntegration: false, contextIsolation: true }
  });
  splashWindow.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(SPLASH_HTML)}`);
}
createSplashWindow();
const splashTimer = setTimeout(() => splashWindow?.close(), DURATION_MS);
mainWindow.webContents.once('did-finish-load', () => {
  clearTimeout(splashTimer);
  setTimeout(() => splashWindow?.close(), 300);
});
// ─────────────────────────────────────────────────────
```
**Java placeholders:** `SPLASH_HTML` (inline HTML string), `DURATION_MS` (int).
**Free-tier note:** always include `Powered by WebToDesk` div inside `SPLASH_HTML`.

---

### 5.2 Expiry Lock (`MAIN_JS`)
```javascript
// ── EXPIRY LOCK MODULE ────────────────────────────────
const APP_EXPIRES_AT = EPOCH_MS_LONG;
if (Date.now() > APP_EXPIRES_AT) {
  mainWindow.loadFile(path.join(__dirname, 'expired.html'));
  mainWindow.once('ready-to-show', () => mainWindow.show());
  return; // MUST return — do not call mainWindow.loadURL(websiteUrl)
}
// ─────────────────────────────────────────────────────
```
**Placement:** inject BEFORE `mainWindow.loadURL(websiteUrl)` call.
**Java placeholder:** `EPOCH_MS_LONG` (long — `expiresAt.toEpochMilli()`).

---

### 5.3 Domain Lock (`MAIN_JS`)
```javascript
// ── DOMAIN LOCK MODULE ────────────────────────────────
const ALLOWED_DOMAINS = [/* array of regex strings */];
const BLOCKED_DOMAINS = [/* array of regex strings */];
function isDomainAllowed(urlStr) {
  try {
    const host = new URL(urlStr).hostname;
    if (BLOCKED_DOMAINS.some(b => new RegExp(b).test(host))) return false;
    if (ALLOWED_DOMAINS.length === 0) return true;
    return ALLOWED_DOMAINS.some(a => new RegExp(a).test(host));
  } catch { return false; }
}
mainWindow.webContents.on('will-navigate', (event, url) => {
  if (!isDomainAllowed(url)) event.preventDefault();
});
mainWindow.webContents.setWindowOpenHandler(({ url }) =>
  isDomainAllowed(url) ? { action: 'allow' } : { action: 'deny' }
);
// ─────────────────────────────────────────────────────
```
**Free tier:** `ALLOWED_DOMAINS = [new URL(websiteUrl).hostname]`.

---

### 5.4 Screen Capture Protection (`MAIN_JS`)
```javascript
// ── SCREEN CAPTURE PROTECTION MODULE ─────────────────
mainWindow.setContentProtection(true);
const SCREENSHOT_KEYS = ['PrintScreen', 'Ctrl+PrintScreen', 'Alt+PrintScreen',
  'Shift+PrintScreen', 'Ctrl+Shift+3', 'Ctrl+Shift+4', 'Ctrl+Shift+5'];
app.whenReady().then(() => {
  SCREENSHOT_KEYS.forEach(key => {
    try { globalShortcut.register(key, () => {}); } catch (_) {}
  });
});
// ─────────────────────────────────────────────────────
```
**Preload companion** — inject blackout overlay on `visibilitychange`.

---

### 5.5 System Tray (`MAIN_JS`)
```javascript
// ── SYSTEM TRAY MODULE ────────────────────────────────
let appTray = null;
app.whenReady().then(() => {
  appTray = new Tray(getIconPath());
  appTray.setToolTip('TOOLTIP_TEXT');
  appTray.setContextMenu(Menu.buildFromTemplate([
    MENU_ITEMS
  ]));
  appTray.on('double-click', () =>
    mainWindow.isVisible() ? mainWindow.focus() : mainWindow.show()
  );
});
mainWindow.on('close', event => {
  if (!app.isQuitting) { event.preventDefault(); mainWindow.hide(); }
});
app.on('before-quit', () => { app.isQuitting = true; });
// ─────────────────────────────────────────────────────
```

---

### 5.6 Auto-Update (`MAIN_JS`)
```javascript
// ── AUTO-UPDATE MODULE ────────────────────────────────
const { autoUpdater } = require('electron-updater');
autoUpdater.setFeedURL({ url: 'FEED_URL' });
autoUpdater.autoDownload        = true;
autoUpdater.autoInstallOnAppQuit = AUTO_INSTALL_BOOL;
app.whenReady().then(() =>
  autoUpdater.checkForUpdatesAndNotify().catch(e =>
    console.warn('Update check failed:', e.message))
);
autoUpdater.on('update-downloaded', () => {
  autoUpdater.quitAndInstall(true, true);
});
// ─────────────────────────────────────────────────────
```
**package.json deps:** `"electron-updater"` + `"publish": { "provider": "generic", "url": "FEED_URL" }`.

---

### 5.7 Dark/Light Sync (`MAIN_JS`)
```javascript
// ── DARK/LIGHT SYNC MODULE ────────────────────────────
const { nativeTheme } = require('electron');
function sendTheme() {
  if (!mainWindow?.isDestroyed())
    mainWindow.webContents.send('wtd-theme-changed', {
      isDark: nativeTheme.shouldUseDarkColors,
      theme:  nativeTheme.shouldUseDarkColors ? 'dark' : 'light'
    });
}
nativeTheme.on('updated', sendTheme);
mainWindow.webContents.once('did-finish-load', sendTheme);
// ─────────────────────────────────────────────────────
```
**Preload companion:**
```javascript
ipcRenderer.on('wtd-theme-changed', (_, { isDark, theme }) => {
  document.documentElement.setAttribute('data-theme', theme);
  document.documentElement.classList.toggle('wtd-dark',  isDark);
  document.documentElement.classList.toggle('wtd-light', !isDark);
});
```

---

### 5.8 Window Polish (`MAIN_JS`)
```javascript
// ── WINDOW POLISH MODULE ──────────────────────────────
mainWindow.setAlwaysOnTop(true, 'floating');  // if alwaysOnTop enabled
mainWindow.setOpacity(OPACITY_VALUE);          // skip if 1.0
if (BLUR_ENABLED) {
  if (process.platform === 'darwin') {
    mainWindow.setVibrancy('VIBRANCY_VALUE');
  } else if (process.platform === 'win32') {
    try { mainWindow.setBackgroundMaterial('acrylic'); } catch (_) {}
  }
}
// ─────────────────────────────────────────────────────
```

---

### 5.9 Global Hotkeys (`MAIN_JS`)
```javascript
// ── GLOBAL HOTKEYS MODULE ─────────────────────────────
app.whenReady().then(() => {
  const hotkeys = HOTKEYS_ARRAY;
  hotkeys.forEach(({ accelerator, action, ipcChannel }) => {
    const ok = globalShortcut.register(accelerator, () => {
      if (action === 'reload')    mainWindow.reload();
      else if (action === 'show') mainWindow.show();
      else if (action === 'ipc')  mainWindow.webContents.send(ipcChannel);
    });
    if (!ok) console.warn(`Global hotkey failed to register: ${accelerator}`);
  });
});
// ─────────────────────────────────────────────────────
```

---

### 5.10 File System Access (`MAIN_JS`)
```javascript
// ── FILE SYSTEM MODULE ────────────────────────────────
const ALLOWED_PATHS = ALLOWED_PATHS_ARRAY;
function isPathAllowed(p) {
  return ALLOWED_PATHS.length === 0 ||
    ALLOWED_PATHS.some(a => path.normalize(p).startsWith(path.normalize(a)));
}
ipcMain.handle('fs-open-dialog', async () =>
  dialog.showOpenDialogSync(mainWindow, { properties: ['openFile'] })
);
ipcMain.handle('fs-read-file', async (_, filePath) => {
  if (!isPathAllowed(filePath)) throw new Error('Path not allowed');
  return fs.readFileSync(filePath, 'utf8');
});
ipcMain.handle('fs-write-file', async (_, filePath, data) => {
  if (!isPathAllowed(filePath)) throw new Error('Path not allowed');
  if (MODE !== 'read-write') throw new Error('Write not permitted');
  fs.writeFileSync(filePath, data, 'utf8');
});
// ─────────────────────────────────────────────────────
```

---

### 5.11 Clipboard Integration (`PRELOAD_JS` + `MAIN_JS`)
```javascript
// preload.js
contextBridge.exposeInMainWorld('wtdClipboard', {
  read:  ()     => ipcRenderer.invoke('clipboard-read'),
  write: (text) => ipcRenderer.invoke('clipboard-write', text),
});
// main.js handlers
ipcMain.handle('clipboard-read',  () => clipboard.readText());
ipcMain.handle('clipboard-write', (_, text) => clipboard.writeText(text));
```

---

### 5.12 Right-Click Disable / Custom Menu (`PRELOAD_JS`)
```javascript
// ── RIGHT-CLICK MODULE ────────────────────────────────
document.addEventListener('contextmenu', e => {
  e.preventDefault();
  if (!DISABLE_BOOL) {
    ipcRenderer.send('show-context-menu', { x: e.clientX, y: e.clientY });
  }
});
```
**Main process handler:**
```javascript
ipcMain.on('show-context-menu', (event, { x, y }) => {
  const menu = Menu.buildFromTemplate(CUSTOM_MENU_TEMPLATE);
  menu.popup({ window: mainWindow, x, y });
});
```

---

### 5.13 Watermark — Free Brand (`PRELOAD_JS`)
```javascript
// ── WATERMARK MODULE (WebToDesk Brand) ────────────────
(function injectWatermark() {
  const inject = () => {
    if (document.getElementById('__wtd_watermark')) return;
    const el = document.createElement('div');
    el.id = '__wtd_watermark';
    el.innerText = 'Powered by WebToDesk';
    el.style.cssText =
      'position:fixed;bottom:10px;right:12px;font-size:11px;' +
      'color:rgba(120,120,120,0.5);font-family:sans-serif;' +
      'pointer-events:none;z-index:2147483647;user-select:none;';
    document.body?.appendChild(el);
  };
  document.readyState === 'loading'
    ? document.addEventListener('DOMContentLoaded', inject)
    : inject();
  window.addEventListener('load', inject); // SPA-safe re-inject
})();
// ─────────────────────────────────────────────────────
```

### 5.14 Watermark — Custom Pro (`PRELOAD_JS`)
Same pattern. Position CSS map:
```
top-left         → top:10px;left:12px;
top-right        → top:10px;right:12px;
bottom-left      → bottom:10px;left:12px;
center           → top:50%;left:50%;transform:translate(-50%,-50%);
bottom-right (default) → bottom:10px;right:12px;
```

---

### 5.15 Native Notifications (`MAIN_JS`)
```javascript
// ── NATIVE NOTIFICATIONS MODULE ───────────────────────
session.defaultSession.setPermissionRequestHandler(
  (webContents, permission, cb) => cb(permission === 'notifications')
);
// ─────────────────────────────────────────────────────
```
Electron forwards `window.Notification` to OS natively once permission is granted — no preload bridge needed.

---

### 5.16 Offline Cache (`PRELOAD_JS`)
```javascript
// ── OFFLINE CACHE MODULE ──────────────────────────────
(function registerOfflineSW() {
  if (!('serviceWorker' in navigator)) return;
  const swCode = `/* SW_CODE_PLACEHOLDER */`;
  const blob = new Blob([swCode], { type: 'application/javascript' });
  navigator.serviceWorker.register(URL.createObjectURL(blob))
    .catch(e => console.warn('SW registration failed:', e));
})();
// ─────────────────────────────────────────────────────
```
**Warning:** `session.webRequest` intercepts and SW can conflict with `loadURL https://`. If CORS issues arise, use `session.defaultSession.webRequest.onBeforeRequest` as fallback instead of a SW.

---

### 5.17 Safe Storage / License Persistence (`MAIN_JS`)
```javascript
// ── SAFE STORAGE / LICENSE PERSISTENCE ───────────────
const { safeStorage } = require('electron');
const LICENSE_PATH = path.join(app.getPath('userData'), 'license.enc');

function saveLicense(licenseObj) {
  if (!safeStorage.isEncryptionAvailable()) {
    fs.writeFileSync(LICENSE_PATH, JSON.stringify(licenseObj), 'utf8');
    return;
  }
  const encrypted = safeStorage.encryptString(JSON.stringify(licenseObj));
  fs.writeFileSync(LICENSE_PATH, encrypted);
}

function loadLicense(fallback) {
  try {
    const raw = fs.readFileSync(LICENSE_PATH);
    if (!safeStorage.isEncryptionAvailable()) return JSON.parse(raw.toString());
    return JSON.parse(safeStorage.decryptString(raw));
  } catch (_) { return fallback; }
}
// ─────────────────────────────────────────────────────
```
**Use for:** persisting `expiresAt`, `tier`, `licenseId` across version upgrades and reinstalls. OS-encrypted — cannot be read by other apps or users.

---

### 5.18 Download Manager (`MAIN_JS`)
```javascript
// ── FILE DOWNLOAD MODULE ──────────────────────────────
ipcMain.handle('download-file', async (_, url, filename) => {
  return new Promise((resolve, reject) => {
    mainWindow.webContents.downloadURL(url);
    session.defaultSession.once('will-download', (event, item) => {
      const savePath = path.join(app.getPath('downloads'), filename || item.getFilename());
      item.setSavePath(savePath);
      item.on('updated', (e, state) => {
        if (state === 'interrupted') reject(new Error('Download interrupted'));
      });
      item.once('done', (e, state) => {
        if (state === 'completed') resolve(savePath);
        else reject(new Error(`Download failed: ${state}`));
      });
    });
  });
});
// ─────────────────────────────────────────────────────
```

---

## 6. package.json Merge Rules

**NEVER string-concatenate package.json.** Always merge via Jackson `ObjectNode`:

```java
ObjectMapper mapper = new ObjectMapper();
ObjectNode base = (ObjectNode) mapper.readTree(existingPackageJson);

// Add dependency
((ObjectNode) base.get("dependencies")).put("electron-updater", "^6.3.4");

// Merge build.publish
ObjectNode publish = mapper.createObjectNode();
publish.put("provider", "generic");
publish.put("url", feedUrl);
((ObjectNode) base.get("build")).set("publish", publish);

return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(base);
```

---

## 7. Security Invariants (never violate)

| Rule | Why |
|---|---|
| `nodeIntegration: false` always | Prevents renderer from accessing Node APIs directly |
| `contextIsolation: true` always | Isolates preload from page JS; required for `contextBridge` |
| `sandbox: true` recommended | Limits renderer OS access |
| Never expose raw `ipcRenderer` via contextBridge | Allows renderer to call any IPC channel arbitrarily |
| Validate `filePath` before `fs.*` in main | Path traversal attack vector from renderer |
| Validate `url` before `shell.openExternal` | Open-redirect / remote code execution vector |
| Wrap `globalShortcut.register` in try/catch | OS-reserved keys return `false` silently |
| `setContentProtection` is not tamper-proof | Hardware capture still works — it is a UX gate only |
| Never disable `webSecurity` in production | Bypasses CORS — critical security hole |
| Use `safeStorage` for sensitive persistent values | OS-encrypted; not reversible by other apps |
| CSP header minimum: `default-src 'self'` | Blocks XSS and remote script injection |

---

## 8. OS-Specific Caveats

| Feature | Windows | macOS | Linux |
|---|---|---|---|
| `setBackgroundMaterial('acrylic')` | Win 11 build 22000+ only — wrap try/catch | N/A | N/A |
| `setVibrancy(...)` | N/A | macOS 10.14+ | N/A |
| `titleBarStyle: 'hiddenInset'` | N/A | ✅ | N/A |
| `setContentProtection` | BitBlt-only | ✅ native | Partial (compositor-dependent) |
| Tray icon format | `.ico` preferred | `.png` 32×32 | `.png` |
| Auto-update code signing | Authenticode required | Notarization required | Not required |
| `systemPreferences.askForMediaAccess` | N/A | ✅ required for mic/cam | N/A |
| `inAppPurchase` | N/A | ✅ Mac App Store only | N/A |
| `TouchBar` | N/A | ✅ older MacBooks with Touch Bar | N/A |
| `powerMonitor` lock-screen events | ✅ | ✅ | ✅ partial |
| `safeStorage.isEncryptionAvailable()` | ✅ DPAPI | ✅ Keychain | Depends on libsecret |
| `dialog.showOpenDialogSync` with sandbox | Avoid — use async version | Avoid | OK |

**Always gate platform-specific code** with `process.platform === 'darwin'` / `'win32'` / `'linux'`.

---

## 9. Electron-Builder Target Map

| `TargetOS` enum | CLI flag | Default `FileType` | Also supports |
|---|---|---|---|
| `WINDOWS` | `--win` | `nsis` (`.exe`) | `msi`, `portable`, `appx` |
| `LINUX` | `--linux` | `AppImage` | `deb`, `rpm`, `snap`, `flatpak` |
| `MACOS` | `--mac` | `dmg` | `pkg`, `mas` (App Store), `zip` |

```json
"build": {
  "appId": "com.webtodesk.APP_ID",
  "productName": "PRODUCT_NAME",
  "win": {
    "target": "nsis",
    "icon": "build/icon.ico",
    "artifactName": "${productName}-Setup.${ext}"
  },
  "linux": {
    "target": "AppImage",
    "icon": "build/icon.png"
  },
  "mac": {
    "target": "dmg",
    "icon": "build/icon.icns",
    "hardenedRuntime": true,
    "gatekeeperAssess": false
  }
}
```

---

## 10. Module Injection Order in `generateMainJs()`

Inject modules in this order to avoid reference-before-use errors:

1. `SplashScreenModule` — reads `mainWindow` ref, closes on `did-finish-load`
2. `ExpiryLockModule` — **must be before `loadURL` call** (returns early if expired)
3. `SafeStorageModule` — define `loadLicense`/`saveLicense` helpers before `loadURL`
4. `DomainLockModule` — attaches `will-navigate` listener
5. `ScreenCaptureModule` — calls `setContentProtection`, registers screenshot shortcuts
6. `DarkLightSyncModule` — attaches `nativeTheme.on('updated')`
7. `WindowPolishModule` — calls `setAlwaysOnTop`, `setOpacity`, `setVibrancy`
8. `CustomKeyBindingsModule` — registers non-global shortcuts
9. `GlobalHotkeysModule` — registers global shortcuts (conflict-safe with #5)
10. `SystemTrayModule` — creates Tray, overrides `close` event
11. `AutoUpdateModule` — calls `checkForUpdatesAndNotify` in `whenReady`
12. `FileSystemModule` — registers `ipcMain.handle` for fs operations
13. `ClipboardModule` — registers `ipcMain.handle` for clipboard operations
14. `DownloadModule` — registers `ipcMain.handle('download-file', …)`

---

## 11. App Lifecycle — Full Event Map

```
app launch
  └── 'will-finish-launching'     add crash reporter, open-url listeners
  └── 'ready' / whenReady()       create windows, tray, shortcuts, protocols
        └── BrowserWindow created
             └── webContents: did-start-loading → dom-ready → did-finish-load
  └── 'activate'                  macOS dock click; recreate window if none open
  └── 'open-url'                  macOS URL scheme handler (wtd://)
  └── 'open-file'                 macOS drag-to-dock file handler
  └── 'second-instance'           when requestSingleInstanceLock is active
  └── 'window-all-closed'         quit on Windows/Linux, stay alive on macOS
  └── 'before-quit'               set app.isQuitting = true, begin cleanup
  └── 'will-quit'                 unregister shortcuts, flush logs
  └── 'quit'                      app fully done
```

**Single instance lock pattern:**
```javascript
const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) { mainWindow.restore(); mainWindow.focus(); }
  });
}
```

---

## 12. Common Mistakes & Fixes

| Mistake | Fix |
|---|---|
| `mainWindow.loadURL()` before `app.whenReady()` resolves | Always create window and call `loadURL` inside `whenReady` |
| Using `ipcRenderer.send` for request-response | Use `ipcRenderer.invoke` + `ipcMain.handle` for async two-way |
| Not calling `event.preventDefault()` in `will-navigate` | Navigation proceeds regardless — must call it explicitly |
| Registering `globalShortcut` outside `app.whenReady()` | Always inside `whenReady` |
| SW Blob URL conflicts with custom protocol or webRequest intercepts | Use `webRequest.onBeforeRequest` as fallback; test thoroughly |
| `setBackgroundMaterial` on Windows 10 | Throws — always wrap in `try/catch` |
| Missing `globalShortcut.unregisterAll()` on quit | Shortcuts leak across hot-reload dev sessions |
| String-concatenating `package.json` | JSON corruption — always use Jackson `ObjectNode` merge |
| Passing user input directly to `shell.openExternal` | Validate against allowlist first — RCE vector |
| Creating `Tray` before `app.whenReady()` | Silently fails or crashes on some platforms |
| Missing `app.on('before-quit', () => app.isQuitting = true)` | Tray minimize-to-tray loop — app never quits |
| Using `safeStorage` before `app.whenReady()` | Returns empty or throws — init only after ready |
| Using `win.destroy()` instead of `win.close()` | Skips `'close'` event — only use for force-kill |
| `dialog.showOpenDialogSync` with `sandbox: true` on macOS | Use async `showOpenDialog` instead |
| Forgetting `contextIsolation: true` with `contextBridge` | `contextBridge` requires it — undefined behavior without |

---

## 13. `getIconPath()` Helper (include in `MAIN_JS_IMPORTS`)

```javascript
function getIconPath() {
  const ext = process.platform === 'win32'  ? 'ico'
            : process.platform === 'darwin' ? 'icns' : 'png';
  return path.join(__dirname, `build/icon.${ext}`);
}
```
Define **once** in `MAIN_JS_IMPORTS` — all modules that reference tray or window icons call it.

---

## 14. Java Rendering Tips

- **Escape single quotes:** `str.replace("'", "\\'")`
- **Epoch ms:** `config.getExpiry().getExpiresAt().toEpochMilli()` — no `L` suffix in JS
- **Boolean rendering:** use `.formatted(boolValue)` not string `"true"`
- **JS arrays from Java lists:** `toJsArray(List<String>)` helper → `['a','b']`
- **Multi-line JS:** use Java text blocks `"""..."""`; watch for indentation bleed into output
- **Empty module guard:** always implement `isEnabled()` — called before `render()` in `ConversionService`
- **Null-safe config access:** check `config.getX() != null` before calling `.getY()` on nested config objects
- **Version string injection:** write `"version"` field in `package.json` from `ConversionProject.version` — JS reads it via `app.getVersion()`

---

*Last updated from Electron docs: https://www.electronjs.org/docs/latest/*
*Aligned with WebToDesk FeatureConfig / ElectronModule architecture — Phases 1–10.*