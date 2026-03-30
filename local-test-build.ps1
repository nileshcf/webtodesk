<#
.SYNOPSIS
  Builds and launches a local Electron test app — no microservices needed.
  Copies module templates directly, renders config/entry files, then runs Electron.

  Modules: ALL TRIAL (splash-screen, offline, badge, domain-lock, title-bar, watermark, expiry)
  URL    : https://www.youtube.com
  Expiry : 4 pm today (local time) — change system clock past 4 pm to test lock screen.
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$TemplatesDir = "$PSScriptRoot\conversion-service\src\main\resources\templates\electron"
$TestDir      = "$PSScriptRoot\test-local-app"
$AppTitle     = "YouTube Desktop"
$WebsiteUrl   = "https://www.youtube.com"
$Version      = "1.0.0"

# ── Expiry: 4 pm today in UTC ISO-8601 ───────────────────────────────────────
$expiry4pm = Get-Date -Hour 16 -Minute 0 -Second 0 -Millisecond 0
$expiryISO = $expiry4pm.ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.000Z")

Write-Host ""
Write-Host "  +==========================================+" -ForegroundColor Magenta
Write-Host "  |    WebToDesk - Local Module Test Run     |" -ForegroundColor Magenta
Write-Host "  +==========================================+" -ForegroundColor Magenta
Write-Host ""
Write-Host "  Dir       : $TestDir"         -ForegroundColor White
Write-Host "  App       : $AppTitle"         -ForegroundColor White
Write-Host "  URL       : $WebsiteUrl"       -ForegroundColor White
Write-Host "  Expiry    : $expiry4pm (local time)" -ForegroundColor Yellow
Write-Host "  Modules   : splash-screen, offline, badge, domain-lock, title-bar, watermark, expiry" -ForegroundColor White
Write-Host ""

# -- 1. Create / clean test directory ---------------------------------------------------
if (Test-Path $TestDir) {
    Write-Host "[..] Cleaning previous test dir..." -ForegroundColor Cyan
    Remove-Item $TestDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path "$TestDir\modules" | Out-Null

# -- 2. Copy module templates as .js files
Write-Host "[..] Copying TRIAL module templates..." -ForegroundColor Cyan
$trialModules = @('splash-screen','watermark','navigation')
foreach ($mod in $trialModules) {
    $src = "$TemplatesDir\modules\$mod.mustache"
    if (Test-Path $src) {
        Copy-Item $src "$TestDir\modules\$mod.js" -Force
        Write-Host "      $mod.js" -ForegroundColor DarkGray
    } else {
        Write-Host "  [!!] MISSING: $mod.mustache" -ForegroundColor Yellow
    }
}

# ── 3. config.js ─────────────────────────────────────────────────────────────
Write-Host "[..] Writing config.js..." -ForegroundColor Cyan
@"
// Local test build — WebToDesk PRO module test (splash-screen + watermark + navigation)
module.exports = {
  projectName:    'youtube-desktop-test',
  currentVersion: '$Version',
  appTitle:       '$AppTitle',
  websiteUrl:     '$WebsiteUrl',
  iconFile:       'icon.ico',
  modules: ["splash-screen","watermark","navigation"],

  splashScreen: {
    "duration":     10000,
    "primaryColor": "#6C63FF",
    "title":        "$AppTitle",
    "tagline":      "Your favourite video platform — desktop edition",
    "showBranding": true
  },

  watermark: {
    "text":              "PRO",
    "position":          "top-right",
    "showDaysRemaining": false,
    "badgeColor":        "rgba(99,102,241,0.92)",
    "textColor":         "#ffffff",
    "opacity":           1.0,
    "tierLabel":         "PRO",
    "showBadge":         true,
    "overlayWatermark": {
      "enabled":        true,
      "showAppName":    true,
      "showIp":         true,
      "showTime":       true,
      "showCustomText": false,
      "customText":     "",
      "fontSize":       13,
      "color":          "rgba(255,255,255,0.11)",
      "angle":          -30,
      "spacing":        160,
      "order":          ["appName","time"]
    }
  }
};
"@ | Set-Content -Path "$TestDir\config.js" -Encoding UTF8

# ── 4. main.js ────────────────────────────────────────────────────────────────
Write-Host "[..] Writing main.js..." -ForegroundColor Cyan
@'
const { app, BrowserWindow, globalShortcut, ipcMain, shell } = require('electron');
const path   = require('path');
const config = require('./config');
const { appTitle, websiteUrl } = config;

const splash_screen = require('./modules/splash-screen');
const watermark     = require('./modules/watermark');
const navigation    = require('./modules/navigation');

async function createWindow() {
  const mainWindow = new BrowserWindow({
    width: 1280, height: 800,
    title: appTitle,
    show: false,
    webPreferences: {
      nodeIntegration:  false,
      contextIsolation: true,
      preload: path.join(__dirname, 'preload.js')
    }
  });

  mainWindow.once('ready-to-show', () => mainWindow.show());
  mainWindow.loadURL(websiteUrl).catch(err => console.error('[wtd] load error:', err.message));
  mainWindow.webContents.on('page-title-updated', e => { e.preventDefault(); mainWindow.setTitle(appTitle); });
  mainWindow.setMenuBarVisibility(false);

  // ── Module setups ─────────────────────────────────────────────────────────
  splash_screen.setup(mainWindow, config);
  watermark.setup(mainWindow, config);
  navigation.setup(mainWindow, config);
}

app.whenReady().then(createWindow);
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
app.on('activate', () => { if (!BrowserWindow.getAllWindows().length) createWindow(); });
app.on('will-quit', () => globalShortcut.unregisterAll());
ipcMain.on('open-external', (event, url) => shell.openExternal(url));
'@ | Set-Content -Path "$TestDir\main.js" -Encoding UTF8

# ── 5. preload.js ─────────────────────────────────────────────────────────────
Write-Host "[..] Writing preload.js..." -ForegroundColor Cyan
@'
const { contextBridge, ipcRenderer } = require('electron');
const config = require('./config');

const splash_screen = require('./modules/splash-screen');
const watermark     = require('./modules/watermark');
const navigation    = require('./modules/navigation');

contextBridge.exposeInMainWorld('electronAPI', {
  openExternal: url => ipcRenderer.send('open-external', url)
});

splash_screen.preloadSetup(contextBridge, ipcRenderer, config);
watermark.preloadSetup(contextBridge, ipcRenderer, config);
navigation.preloadSetup(contextBridge, ipcRenderer, config);
'@ | Set-Content -Path "$TestDir\preload.js" -Encoding UTF8

# ── 6. package.json ───────────────────────────────────────────────────────────
Write-Host "[..] Writing package.json..." -ForegroundColor Cyan
@'
{
  "name": "youtube-desktop-test",
  "version": "1.0.0",
  "description": "WebToDesk local module test — YouTube Desktop (TRIAL)",
  "main": "main.js",
  "scripts": { "start": "electron ." },
  "devDependencies": {
    "electron": "^38.2.2"
  }
}
'@ | Set-Content -Path "$TestDir\package.json" -Encoding UTF8

# ── 7. expired.html — substitute {{appTitle}} from template ───────────────────
Write-Host "[..] Writing expired.html..." -ForegroundColor Cyan
$htmlTemplate = "$TemplatesDir\expired.html.mustache"
if (Test-Path $htmlTemplate) {
    (Get-Content $htmlTemplate -Raw) -replace '\{\{appTitle\}\}', $AppTitle |
        Set-Content -Path "$TestDir\expired.html" -Encoding UTF8
} else {
    Write-Host "  [!!] expired.html.mustache not found - skipping" -ForegroundColor Yellow
}

# ── 8. npm install ────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "[..] Installing Electron (npm install in $TestDir)..." -ForegroundColor Cyan

Push-Location $TestDir
try {
    $npmOut = cmd /c "npm install --no-fund --no-audit 2>&1"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[!!] npm install output:" -ForegroundColor Red
        $npmOut | Where-Object { $_ -match "ERR!" } | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
        throw "npm install failed (exit $LASTEXITCODE)"
    }
    Write-Host "[OK] npm install complete" -ForegroundColor Green

    # -- 9. Launch
    Write-Host ""
    Write-Host "  ----------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host "  [LAUNCH] Starting YouTube Desktop..." -ForegroundColor Green
    Write-Host ""
    Write-Host "  What to expect:" -ForegroundColor White
    Write-Host "    * Splash screen (10 s, purple brand accent)" -ForegroundColor DarkCyan
    Write-Host "    * YouTube loads - PRO badge top-right (indigo)" -ForegroundColor DarkCyan
    Write-Host "    * Tiled canvas watermark fades in (PRO overlay, appName + time)" -ForegroundColor DarkCyan
    Write-Host "    * 4-min BrowserWindow TRIAL overlay also active - click anywhere to dismiss" -ForegroundColor DarkCyan
    Write-Host "    * Hover left/right edges for back/forward nav arrows" -ForegroundColor DarkCyan
    Write-Host "  ----------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host ""

    $electronBin = ".\node_modules\.bin\electron.cmd"
    if (-not (Test-Path $electronBin)) {
        $electronBin = ".\node_modules\electron\dist\electron.exe"
    }
    if (-not (Test-Path $electronBin)) {
        Write-Host "[..] Falling back to npx electron..." -ForegroundColor Yellow
        & npx electron .
    } else {
        & $electronBin .
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "[OK] Test session ended." -ForegroundColor Green
