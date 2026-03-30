# Common utilities for WebToDesk PowerShell scripts
# Version: 1.0.0

# Write colored output functions
function Write-Info {
    param([string]$Message, [switch]$NoNewline)
    $color = if ($script:OutputJson) { $Host.UI.RawUI.ForegroundColor } else { "Yellow" }
    if ($NoNewline) {
        Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] $Message" -ForegroundColor $color -NoNewline
    } else {
        Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] $Message" -ForegroundColor $color
    }
}

function Write-Ok {
    param([string]$Message, [switch]$NoNewline)
    $color = if ($script:OutputJson) { $Host.UI.RawUI.ForegroundColor } else { "Green" }
    if ($NoNewline) {
        Write-Host "[OK] $Message" -ForegroundColor $color -NoNewline
    } else {
        Write-Host "[OK] $Message" -ForegroundColor $color
    }
}

function Write-Fail {
    param([string]$Message, [switch]$NoNewline)
    $color = if ($script:OutputJson) { $Host.UI.RawUI.ForegroundColor } else { "Red" }
    if ($NoNewline) {
        Write-Host "[ERROR] $Message" -ForegroundColor $color -NoNewline
    } else {
        Write-Host "[ERROR] $Message" -ForegroundColor $color
    }
}

function Write-Success {
    param([string]$Message)
    Write-Host "✓ $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "⚠ $Message" -ForegroundColor Yellow
}

function Write-Step {
    param([string]$Message, [int]$Step, [int]$Total)
    $prefix = if ($Step -and $Total) { "[$Step/$Total]" } else { "[]" }
    Write-Host "$prefix $Message" -ForegroundColor Cyan
}

# Confirmation helper
function Test-Confirmation {
    param(
        [string]$Prompt,
        [switch]$DefaultYes,
        [switch]$Force,
        [switch]$NonInteractive
    )
    
    if ($Force -or $NonInteractive) {
        return $true
    }
    
    $suffix = if ($DefaultYes) { " (Y/n)" } else { " (y/N)" }
    $answer = Read-Host "$Prompt$suffix"
    
    if ($DefaultYes) {
        return ($answer -ne "n" -and $answer -ne "N")
    } else {
        return ($answer -eq "y" -or $answer -eq "Y")
    }
}

# Docker utilities
function Test-DockerAvailable {
    try {
        $null = docker info 2>$null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

function Get-DockerImageExists {
    param([string]$ImageName)
    $images = docker images --format "{{.Repository}}:{{.Tag}}" 2>$null
    return $images -contains $ImageName
}

function Get-ContainerStatus {
    param([string]$ContainerName)
    $status = docker ps -a --filter "name=$ContainerName" --format "{{.Status}}" 2>$null
    if ($status) {
        if ($status -match "Up ") { return "running" }
        if ($status -match "Exited ") { return "stopped" }
        return "other"
    }
    return "not_found"
}

# Port utilities
function Test-PortInUse {
    param([int]$Port)
    return Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
           Where-Object { $_.State -eq "Listen" }
}

function Wait-PortReady {
    param(
        [int]$Port,
        [int]$TimeoutSec = 60,
        [string]$ServiceName = "Service"
    )
    
    $start = Get-Date
    $attempt = 0
    
    while (((Get-Date) - $start).TotalSeconds -lt $TimeoutSec) {
        $attempt++
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:$Port/" -TimeoutSec 3 -UseBasicParsing -ErrorAction SilentlyContinue
            if ($response.StatusCode -eq 200) {
                return $true
            }
        } catch {}
        
        if ($attempt % 5 -eq 0) {
            $elapsedSeconds = [Math]::Floor(((Get-Date) - $start).TotalSeconds)
            Write-Info "Waiting for $ServiceName... (${elapsedSeconds}s)"
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

# Environment utilities
function Import-EnvironmentFile {
    param(
        [string]$EnvFile = ".env",
        [switch]$Required
    )
    
    $envPath = Join-Path $PSScriptRoot $EnvFile
    
    if (-not (Test-Path $envPath)) {
        if ($Required) {
            throw "Environment file required but not found: $envPath"
        }
        Write-Warning "No .env file found at $envPath"
        return
    }
    
    Get-Content $envPath |
        Where-Object { $_ -notmatch '^\s*#' -and $_ -notmatch '^\s*$' -and $_ -match '=' } |
        ForEach-Object {
            $key, $val = $_ -split '=', 2
            [System.Environment]::SetEnvironmentVariable($key.Trim(), $val.Trim(), 'Process')
        }
    
    Write-Info "Loaded environment from $EnvFile"
}

# Git utilities
function Test-GitRepository {
    try {
        $null = git rev-parse --is-inside-work-tree 2>$null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

function Get-GitCurrentBranch {
    if (-not (Test-GitRepository)) { return $null }
    $branch = git rev-parse --abbrev-ref HEAD 2>$null
    if ($LASTEXITCODE -eq 0) { return $branch }
    return $null
}

# Result object creator
function New-ScriptResult {
    param([string]$ScriptName)
    return [ordered]@{
        script = $ScriptName
        timestamp = (Get-Date).ToString("s")
        success = $false
        error = $null
        warnings = @()
    }
}

# Export result
function Export-ScriptResult {
    param(
        [object]$Result,
        [switch]$OutputJson
    )
    
    if ($OutputJson) {
        $Result | ConvertTo-Json -Depth 8
    }
}

# Error handling wrapper
function Invoke-WithErrorHandling {
    param(
        [scriptblock]$ScriptBlock,
        [object]$Result,
        [string]$ErrorMessage,
        [switch]$OutputJson
    )
    
    try {
        & $ScriptBlock
        $Result.success = $true
    } catch {
        $Result.error = $_.Exception.Message
        Write-Fail $ErrorMessage
        Write-Fail $Result.error
        
        if ($OutputJson) {
            Export-ScriptResult -Result $Result -OutputJson
        }
        exit 1
    }
}

# Path resolver
function Resolve-ScriptPath {
    param([string]$RelativePath)
    return Join-Path $PSScriptRoot $RelativePath
}

# Version helper
function Test-PrerequisiteVersion {
    param(
        [string]$Command,
        [string]$MinimumVersion,
        [string]$VersionSwitch = "--version"
    )
    
    try {
        $versionOutput = & $Command $VersionSwitch 2>$null
        if ($LASTEXITCODE -eq 0) {
            # Extract version number (basic implementation)
            if ($versionOutput -match '(\d+\.(\d+\.)*\d+)') {
                $version = $Matches[1]
                Write-Info "$Command version: $version"
                return $true
            }
        }
    } catch {
        Write-Warning "Could not verify $Command version"
    }
    return $false
}

# Initialize common script variables
$script:OutputJson = $false
