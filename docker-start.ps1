[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [int]$HostPort = 7860,
    [switch]$StopExisting,
    [switch]$KillPortProcess,
    [switch]$Force,
    [switch]$NonInteractive,
    [switch]$OutputJson,
    [string]$ContainerName = "webtodesk-app",
    [int]$HealthTimeoutSec = 90
    # ImageName and EnvFile are defined in docker-compose.yml
    # shm_size, ulimits, tmpfs are also applied via docker-compose.yml
)

function Write-Info {
    param([string]$Message)
    if (-not $OutputJson) {
        Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] $Message" -ForegroundColor Yellow
    }
}

function Write-Ok {
    param([string]$Message)
    if (-not $OutputJson) {
        Write-Host "[OK] $Message" -ForegroundColor Green
    }
}

function Write-Fail {
    param([string]$Message)
    if (-not $OutputJson) {
        Write-Host "[ERROR] $Message" -ForegroundColor Red
    }
}

function Test-Yes {
    param([string]$Prompt)

    if ($Force -or $NonInteractive) {
        return $true
    }
    return $PSCmdlet.ShouldContinue($Prompt, "Confirm")
}

$result = [ordered]@{
    script  = "docker-start.ps1"
    container = $ContainerName
    hostPort  = $HostPort
    success   = $false
    healthy   = $false
    status    = $null
}

try {
    Write-Info "Checking Docker availability"
    $null = docker info 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker is not running or not accessible"
    }
    Write-Ok "Docker is available"

    # Enable BuildKit (consistent with docker-rebuild.ps1)
    $env:DOCKER_BUILDKIT         = "1"
    $env:COMPOSE_DOCKER_CLI_BUILD = "1"

    # Verify an image exists for the compose service before trying to start
    $builtImage = docker compose images -q 2>$null
    if (-not $builtImage) {
        throw "No image found for compose service. Run .\docker-rebuild.ps1 first."
    }

    if ($StopExisting) {
        Write-Info "Taking down existing stack..."
        docker compose down --remove-orphans 2>$null | Out-Null
        Write-Ok "Existing stack removed"
    }

    $portInUse = Get-NetTCPConnection -LocalPort $HostPort -ErrorAction SilentlyContinue |
        Where-Object { $_.State -eq "Listen" }
    if ($portInUse) {
        if ($KillPortProcess) {
            foreach ($p in $portInUse) {
                if (Test-Yes -Prompt "Kill process PID $($p.OwningProcess) on port $HostPort?") {
                    Stop-Process -Id $p.OwningProcess -Force -ErrorAction SilentlyContinue
                }
            }
        } elseif (-not (Test-Yes -Prompt "Port $HostPort is in use. Continue anyway?")) {
            throw "Host port $HostPort is already in use"
        }
    }

    # docker compose up applies shm_size, ulimits, tmpfs, env_file, restart from docker-compose.yml
    Write-Info "Starting stack with docker compose up..."
    & docker compose up -d --force-recreate --remove-orphans
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed. Check: docker compose logs $ContainerName"
    }

    # Wait for the health endpoint with retry (services need up to 90s to start)
    $elapsed = 0
    $healthy = $false
    Write-Info "Waiting for health endpoint (up to ${HealthTimeoutSec}s)..."
    while ($elapsed -lt $HealthTimeoutSec) {
        Start-Sleep -Seconds 3
        $elapsed += 3
        try {
            $r = Invoke-WebRequest -Uri "http://localhost:$HostPort/conversion/conversions/health" `
                -TimeoutSec 3 -ErrorAction SilentlyContinue
            if ($r -and $r.StatusCode -eq 200) { $healthy = $true; break }
        } catch {}
        if ($elapsed % 15 -eq 0) { Write-Info "  Still waiting... (${elapsed}s)" }
    }

    $status = docker compose ps --format "{{.Status}}" $ContainerName 2>$null

    $result.success = $true
    $result.healthy = $healthy
    $result.status  = $status
    $result.url     = "http://localhost:$HostPort"

    if ($OutputJson) {
        $result | ConvertTo-Json -Depth 6
    } else {
        if ($healthy) {
            Write-Ok "Stack is healthy ($status)"
        } else {
            Write-Info "Stack is running but health endpoint not ready yet — services may still be starting"
            Write-Host "  Check: docker compose logs -f $ContainerName" -ForegroundColor White
        }
        Write-Host "URL:  http://localhost:$HostPort" -ForegroundColor Cyan
        Write-Host "Logs: docker compose logs -f $ContainerName" -ForegroundColor White
    }

    exit 0
}
catch {
    $result.error = $_.Exception.Message
    Write-Fail $result.error
    if ($OutputJson) {
        $result | ConvertTo-Json -Depth 6
    }
    exit 1
}
