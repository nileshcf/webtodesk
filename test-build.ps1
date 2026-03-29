<#
.SYNOPSIS
  End-to-end module build test for WebToDesk.

.DESCRIPTION
  Authenticates (or skips auth for direct service calls), creates a project with
  the specified modules, triggers a build via POST /build/quick-build, then polls
  until the build is READY or FAILED and prints a result summary.

  Requires DEVELOPMENT_BUILD=true on the target server (set in .env for Docker).

.PARAMETER BaseUrl
  Base URL of the target. Defaults to Docker container on localhost:7860.
    Docker:         http://localhost:7860
    Local gateway:  http://localhost:8080
    Direct service: http://localhost:8082

.PARAMETER Email
  User email for login/register. Ignored when -Direct is set.

.PARAMETER Password
  User password for login/register. Ignored when -Direct is set.

.PARAMETER WebsiteUrl
  The website URL to wrap into an Electron app.

.PARAMETER AppTitle
  Window title for the generated app.

.PARAMETER ProjectName
  Project name slug (letters, numbers, spaces, hyphens, underscores).
  Defaults to a timestamped name.

.PARAMETER Modules
  Comma-separated list of module keys to enable.
  Available: splash-screen, offline, badge, screen-protect, deep-link
  Default: splash-screen,offline,badge  (all TRIAL-tier modules)

.PARAMETER Platform
  Build target: auto | win | linux | mac  (default: auto)

.PARAMETER TimeoutSec
  How long to wait for build completion before giving up. Default: 600s.

.PARAMETER PollIntervalSec
  Status poll interval in seconds. Default: 8s.

.PARAMETER Direct
  Skip authentication and call the conversion-service directly (port 8082).
  Uses X-User-Email header instead of Bearer token.

.PARAMETER AutoRegister
  Automatically register the user if login fails with 401.

.PARAMETER ListModules
  Just list all available modules and exit without building.

.EXAMPLE
  # Test with default modules against Docker container
  .\test-build.ps1

.EXAMPLE
  # Full module set, Windows target, local gateway
  .\test-build.ps1 -BaseUrl http://localhost:8080 -Modules "splash-screen,offline,badge,screen-protect,deep-link" -Platform win

.EXAMPLE
  # Direct service call (no auth), all modules
  .\test-build.ps1 -Direct -Modules "splash-screen,offline,badge" -WebsiteUrl https://example.com

.EXAMPLE
  # Just list available modules
  .\test-build.ps1 -ListModules
#>

[CmdletBinding()]
param(
    [string]$BaseUrl         = "http://localhost:7860",
    [string]$Email           = "dev@webtodesk.local",
    [string]$Password        = "DevTest1234!",
    [string]$WebsiteUrl      = "https://example.com",
    [string]$AppTitle        = "Test App",
    [string]$ProjectName     = "",
    [string]$Modules         = "splash-screen,offline,badge",
    [string]$Platform        = "auto",
    [int]   $TimeoutSec      = 600,
    [int]   $PollIntervalSec = 8,
    [switch]$Direct,
    [switch]$AutoRegister,
    [switch]$ListModules
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ─── Colour helpers ──────────────────────────────────────────────────────────

function Write-Ok   { param($m) Write-Host "[OK]  $m" -ForegroundColor Green }
function Write-Info { param($m) Write-Host "[..] $m"  -ForegroundColor Cyan }
function Write-Warn { param($m) Write-Host "[!!] $m"  -ForegroundColor Yellow }
function Write-Fail { param($m) Write-Host "[XX] $m"  -ForegroundColor Red }

# ─── URL helpers ─────────────────────────────────────────────────────────────

# Determine whether requests go through the API gateway (proxy prefix) or direct
$IsDirect  = $Direct.IsPresent
$ApiPrefix = if ($IsDirect) { "" } else { "/conversion" }

function Url-User  { param($path) "$BaseUrl/user$path" }
function Url-Build { param($path) "$BaseUrl${ApiPrefix}/build$path" }

# ─── HTTP helpers ─────────────────────────────────────────────────────────────

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        [object]$Body = $null,
        [switch]$AllowError
    )
    $params = @{
        Method      = $Method
        Uri         = $Url
        ContentType = "application/json"
        Headers     = $Headers
    }
    if ($Body) { $params.Body = ($Body | ConvertTo-Json -Depth 10 -Compress) }

    try {
        $resp = Invoke-WebRequest @params -UseBasicParsing
        return @{ Ok = $true; Status = $resp.StatusCode; Body = ($resp.Content | ConvertFrom-Json -Depth 20) }
    } catch {
        if ($AllowError) {
            $statusCode = 0
            try { $statusCode = $_.Exception.Response.StatusCode.value__ } catch {}
            $errorBody = $null
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $errorBody = $reader.ReadToEnd() | ConvertFrom-Json -Depth 20
            } catch {}
            return @{ Ok = $false; Status = $statusCode; Body = $errorBody; Error = $_.Exception.Message }
        }
        throw
    }
}

# ─── Module list helper ───────────────────────────────────────────────────────

function Show-Modules {
    Write-Info "Fetching module list from $(Url-Build '/quick-build/modules') ..."
    $r = Invoke-Api -Method GET -Url (Url-Build "/quick-build/modules") -AllowError
    if (-not $r.Ok) {
        Write-Fail "Failed to fetch modules (status $($r.Status)). Is the service running?"
        return
    }
    Write-Host ""
    Write-Host "  Available modules:" -ForegroundColor White
    foreach ($m in $r.Body.allModules) {
        $tier  = $m.requiredTier
        $color = switch ($tier) {
            "TRIAL"    { "Green" }
            "STARTER"  { "Cyan" }
            "PRO"      { "Magenta" }
            "LIFETIME" { "Yellow" }
            default    { "White" }
        }
        Write-Host ("  {0,-20} tier={1,-10} — {2}" -f $m.key, $m.requiredTier, $m.description) -ForegroundColor $color
    }
    Write-Host ""
}

if ($ListModules) {
    Show-Modules
    exit 0
}

# ─── Banner ───────────────────────────────────────────────────────────────────

$moduleList = $Modules -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" }
$ts = Get-Date -Format "yyyyMMdd-HHmmss"
if (-not $ProjectName) { $ProjectName = "test-build-$ts" }

Write-Host ""
Write-Host "  ╔══════════════════════════════════════════╗" -ForegroundColor Blue
Write-Host "  ║       WebToDesk — Module Build Test      ║" -ForegroundColor Blue
Write-Host "  ╚══════════════════════════════════════════╝" -ForegroundColor Blue
Write-Host ""
Write-Host "  BaseUrl     : $BaseUrl"
Write-Host "  Mode        : $(if ($IsDirect) { 'DIRECT (no auth)' } else { 'GATEWAY (auth required)' })"
Write-Host "  WebsiteUrl  : $WebsiteUrl"
Write-Host "  AppTitle    : $AppTitle"
Write-Host "  ProjectName : $ProjectName"
Write-Host "  Modules     : $($moduleList -join ', ')"
Write-Host "  Platform    : $Platform"
Write-Host "  Timeout     : ${TimeoutSec}s"
Write-Host ""

# ─── Step 1: Authenticate (skipped for direct calls) ─────────────────────────

$authHeaders = @{}

if (-not $IsDirect) {
    Write-Info "Logging in as $Email ..."
    $loginResult = Invoke-Api -Method POST -Url (Url-User "/auth/login") -AllowError -Body @{
        email    = $Email
        password = $Password
    }

    if (-not $loginResult.Ok -and $loginResult.Status -eq 401 -and $AutoRegister) {
        Write-Warn "Login failed (401) — attempting auto-register ..."
        $regResult = Invoke-Api -Method POST -Url (Url-User "/auth/register") -AllowError -Body @{
            email       = $Email
            password    = $Password
            username    = ($Email -split "@")[0]
            displayName = "Dev Test"
        }
        if ($regResult.Ok) {
            Write-Ok "Registered. Re-attempting login ..."
            $loginResult = Invoke-Api -Method POST -Url (Url-User "/auth/login") -AllowError -Body @{
                email    = $Email
                password = $Password
            }
        } else {
            Write-Fail "Registration failed (status $($regResult.Status)): $($regResult.Error)"
            exit 1
        }
    }

    if (-not $loginResult.Ok) {
        Write-Fail "Login failed (status $($loginResult.Status)): $($loginResult.Error)"
        Write-Warn "Tip: use -AutoRegister to auto-create the account, or -Direct to skip auth."
        exit 1
    }

    $token = $loginResult.Body.accessToken
    if (-not $token) { $token = $loginResult.Body.access_token }
    if (-not $token) {
        Write-Fail "Login succeeded but no accessToken in response. Body: $($loginResult.Body | ConvertTo-Json)"
        exit 1
    }
    $authHeaders["Authorization"] = "Bearer $token"
    Write-Ok "Authenticated as $Email"
} else {
    $authHeaders["X-User-Email"] = $Email
    Write-Warn "Direct mode — skipping auth, using X-User-Email: $Email"
}

# ─── Step 2: Quick-build request ─────────────────────────────────────────────

Write-Host ""
Write-Info "Submitting quick-build ..."

$qbUrl  = Url-Build "/quick-build"
$qbBody = @{
    projectName = $ProjectName
    websiteUrl  = $WebsiteUrl
    appTitle    = $AppTitle
    modules     = $moduleList
    platform    = $Platform
    userEmail   = $Email
}

$qbResult = Invoke-Api -Method POST -Url $qbUrl -Headers $authHeaders -Body $qbBody -AllowError

if (-not $qbResult.Ok) {
    if ($qbResult.Status -eq 403) {
        Write-Fail "Server returned 403 — DEVELOPMENT_BUILD is not enabled on the server."
        Write-Warn "Set DEVELOPMENT_BUILD=true in .env (Docker) or application.yml (local) and restart."
    } else {
        Write-Fail "quick-build failed (status $($qbResult.Status)): $($qbResult.Error)"
        if ($qbResult.Body) {
            Write-Host "  Response: $($qbResult.Body | ConvertTo-Json)" -ForegroundColor DarkRed
        }
    }
    exit 1
}

$projectId = $qbResult.Body.projectId
$pollUrl   = Url-Build "/status/$projectId"
$logsUrl   = Url-Build "/logs/$projectId"

Write-Ok "Build accepted"
Write-Host "  Project ID  : $projectId"
Write-Host "  Project Name: $($qbResult.Body.projectName)"
Write-Host "  Modules     : $($qbResult.Body.modules -join ', ')"
Write-Host "  Poll URL    : $pollUrl"
Write-Host ""

# ─── Step 3: Poll for completion ─────────────────────────────────────────────

Write-Info "Polling build status (timeout ${TimeoutSec}s, interval ${PollIntervalSec}s) ..."

$startTime  = Get-Date
$lastStage  = ""
$finalStatus = $null

while ($true) {
    $elapsed = [int]((Get-Date) - $startTime).TotalSeconds
    if ($elapsed -gt $TimeoutSec) {
        Write-Fail "Timed out after ${elapsed}s waiting for build to complete."
        exit 2
    }

    $pollResult = Invoke-Api -Method GET -Url $pollUrl -Headers $authHeaders -AllowError
    if (-not $pollResult.Ok) {
        Write-Warn "Poll returned $($pollResult.Status) — retrying ..."
        Start-Sleep -Seconds $PollIntervalSec
        continue
    }

    $status   = $pollResult.Body.status
    $progress = $pollResult.Body.buildProgress
    $stage    = if ($progress) { $progress } else { $status }

    if ($stage -ne $lastStage) {
        $color = switch ($stage) {
            "VALIDATING_ENV"  { "Cyan" }
            "PREPARING"       { "Cyan" }
            "WRITING_FILES"   { "Cyan" }
            "INSTALLING"      { "Yellow" }
            "BUILDING"        { "Yellow" }
            "FINDING_ARTIFACT"{ "Yellow" }
            "UPLOADING_R2"    { "Magenta" }
            "COMPLETE"        { "Green" }
            "FAILED"          { "Red" }
            default           { "White" }
        }
        Write-Host ("  [{0,4}s] {1}" -f $elapsed, $stage) -ForegroundColor $color
        $lastStage = $stage
    }

    if ($status -in @("READY", "FAILED")) {
        $finalStatus = $pollResult.Body
        break
    }

    Start-Sleep -Seconds $PollIntervalSec
}

# ─── Step 4: Result summary ───────────────────────────────────────────────────

$elapsed = [int]((Get-Date) - $startTime).TotalSeconds
Write-Host ""
Write-Host "  ─────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host "  BUILD RESULT" -ForegroundColor White

if ($finalStatus.status -eq "READY") {
    Write-Ok "Build SUCCEEDED in ${elapsed}s"
    Write-Host ""
    Write-Host "  Project ID : $projectId"
    Write-Host "  Modules    : $($moduleList -join ', ')"
    if ($finalStatus.downloadUrl) {
        Write-Host "  Artifact   : $($finalStatus.downloadUrl)" -ForegroundColor Green
        Write-Host ""
        Write-Host "  Download command:" -ForegroundColor DarkGray
        Write-Host "    curl -L '$($finalStatus.downloadUrl)' -o installer$(if ($Platform -eq 'win') { '.exe' } elseif ($Platform -eq 'linux') { '.deb' } else { '' })" -ForegroundColor DarkGray
    } else {
        Write-Warn "Build READY but no downloadUrl in status response."
        Write-Host "  Try: GET $($BaseUrl)$($ApiPrefix)/build/download/$projectId"
    }
} else {
    Write-Fail "Build FAILED after ${elapsed}s"
    Write-Host ""
    Write-Host "  Error: $($finalStatus.buildError)" -ForegroundColor Red
    Write-Host ""
    Write-Info "Fetching last known logs ..."
    $logsResult = Invoke-Api -Method GET -Url $logsUrl -Headers $authHeaders -AllowError
    if ($logsResult.Ok -and $logsResult.Body) {
        foreach ($line in $logsResult.Body) {
            if ($line) { Write-Host "  $line" -ForegroundColor DarkRed }
        }
    }
    Write-Host ""
    Write-Warn "Troubleshooting tips:"
    Write-Host "  1. Check container logs: docker compose logs -f webtodesk-app"
    Write-Host "  2. Verify DEVELOPMENT_BUILD=true is set in .env"
    Write-Host "  3. Check disk space inside container: docker exec webtodesk-app df -h /tmp"
    Write-Host "  4. Verify Wine (for win target): docker exec webtodesk-app wine --version"
    exit 1
}

Write-Host ""
