[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$Image,
    [string]$RegistryRepo,
    [string]$GitHubRepo,
    [string]$Registry = "ghcr.io",
    [string]$Tag = "latest",
    [string]$ContainerName = "webtodesk-app",
    [int]$HostPort = 7860,
    [int]$ContainerPort = 7860,
    [string]$EnvFile = ".env",
    [switch]$StopExisting,
    [switch]$KillPortProcess,
    [switch]$PullAlways,
    [switch]$Force,
    [switch]$NonInteractive,
    [switch]$OutputJson,
    [int]$HealthTimeoutSec = 90
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

function Confirm-Action {
    param([string]$Prompt)
    if ($Force -or $NonInteractive) {
        return $true
    }
    return $PSCmdlet.ShouldContinue($Prompt, "Confirm")
}

function Resolve-ImageRef {
    if ($Image) {
        return $Image.Trim()
    }
    if ($RegistryRepo) {
        return "$($RegistryRepo.Trim()):$Tag"
    }
    if ($GitHubRepo) {
        return "$Registry/$($GitHubRepo.Trim()):$Tag"
    }
    return $null
}

$result = [ordered]@{
    script = "registry-pull-run.ps1"
    image = $null
    container = $ContainerName
    hostPort = $HostPort
    success = $false
    healthy = $false
    status = $null
    envFileUsed = $null
}

try {
    $imageRef = Resolve-ImageRef
    if (-not $imageRef) {
        throw "Image is required. Use -Image ghcr.io/<user-or-org>/webtodesk:latest or -GitHubRepo <user-or-org>/webtodesk [-Tag latest]"
    }
    $result.image = $imageRef

    Write-Info "Checking Docker availability"
    $null = docker info 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker is not running or not accessible"
    }
    Write-Ok "Docker is available"

    if ($PullAlways -or (Confirm-Action "Pull latest image '$imageRef' now?")) {
        Write-Info "Pulling image '$imageRef'"
        & docker pull $imageRef
        if ($LASTEXITCODE -ne 0) {
            throw "docker pull failed for '$imageRef'"
        }
        Write-Ok "Image pulled"
    }

    if ($StopExisting) {
        Write-Info "Checking existing container '$ContainerName'"
        $existing = docker ps -a --filter "name=$ContainerName" --format "{{.Names}}" 2>$null
        if ($existing) {
            if ($PSCmdlet.ShouldProcess($ContainerName, "docker stop/rm")) {
                & docker stop $ContainerName 2>$null | Out-Null
                & docker rm $ContainerName 2>$null | Out-Null
                Write-Ok "Removed existing container '$ContainerName'"
            }
        }
    }

    $portInUse = Get-NetTCPConnection -LocalPort $HostPort -ErrorAction SilentlyContinue |
        Where-Object { $_.State -eq "Listen" }
    if ($portInUse) {
        if ($KillPortProcess) {
            foreach ($p in $portInUse) {
                if (Confirm-Action "Kill process PID $($p.OwningProcess) on port $HostPort?") {
                    Stop-Process -Id $p.OwningProcess -Force -ErrorAction SilentlyContinue
                }
            }
        } elseif (-not (Confirm-Action "Host port $HostPort is in use. Continue anyway?")) {
            throw "Host port $HostPort is already in use"
        }
    }

    $envPath = Join-Path $PSScriptRoot $EnvFile
    $runArgs = @(
        "run", "-d",
        "--name", $ContainerName,
        "-p", "${HostPort}:${ContainerPort}",
        "--restart", "unless-stopped",
        "--shm-size", "512m",
        "--ulimit", "nofile=65536:65536",
        "--tmpfs", "/tmp/webtodesk-builds:mode=1777,size=1500m,exec"
    )

    if (Test-Path $envPath) {
        $runArgs += @("--env-file", $EnvFile)
        $result.envFileUsed = $EnvFile
        Write-Info "Using env file '$EnvFile'"
    } else {
        Write-Info "No env file found at '$EnvFile' (continuing without --env-file)"
    }

    $runArgs += $imageRef

    Write-Info "Running: docker $($runArgs -join ' ')"
    $containerId = (& docker @runArgs 2>$null).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $containerId) {
        throw "Failed to start container from image '$imageRef'"
    }

    $elapsed = 0
    $healthy = $false
    Write-Info "Waiting for health endpoint (up to ${HealthTimeoutSec}s)..."
    while ($elapsed -lt $HealthTimeoutSec) {
        Start-Sleep -Seconds 3
        $elapsed += 3
        try {
            $resp = Invoke-WebRequest -Uri "http://localhost:$HostPort/" -TimeoutSec 3 -ErrorAction SilentlyContinue
            if ($resp -and $resp.StatusCode -eq 200) {
                $healthy = $true
                break
            }
        } catch {}

        if ($elapsed % 15 -eq 0) {
            Write-Info "  Still waiting... (${elapsed}s)"
        }
    }

    $status = docker ps --filter "id=$containerId" --format "{{.Status}}" 2>$null

    $result.success = $true
    $result.healthy = $healthy
    $result.status = $status
    $result.url = "http://localhost:$HostPort"

    if ($OutputJson) {
        $result | ConvertTo-Json -Depth 6
    } else {
        if ($healthy) {
            Write-Ok "Container is healthy ($status)"
        } else {
            Write-Info "Container is running but health endpoint is not ready yet"
        }
        Write-Host "URL:  http://localhost:$HostPort" -ForegroundColor Cyan
        Write-Host "Logs: docker logs -f $ContainerName" -ForegroundColor White
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
