[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$Image,
    [string]$RegistryRepo,
    [string]$GitHubRepo,
    [string]$Registry = "ghcr.io",
    [string]$Tag = "latest",
    [string]$VersionTag,
    [switch]$SelectTag,
    [switch]$ListAvailableTags,
    [string]$TagIndexFile = ".registry-image-tags.json",
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

function Get-TagHistory {
    param([string]$IndexPath)

    $default = [ordered]@{
        schemaVersion = 1
        images = @()
    }

    if (-not (Test-Path $IndexPath)) {
        return $default
    }

    try {
        $raw = Get-Content -Path $IndexPath -Raw -ErrorAction Stop
        if (-not $raw.Trim()) {
            return $default
        }

        $parsed = $raw | ConvertFrom-Json -ErrorAction Stop
        if (-not $parsed) {
            return $default
        }

        return [ordered]@{
            schemaVersion = if ($parsed.schemaVersion) { [int]$parsed.schemaVersion } else { 1 }
            images = @($parsed.images)
        }
    }
    catch {
        throw "Failed to parse tag index file '$IndexPath': $($_.Exception.Message)"
    }
}

function Resolve-TargetRepo {
    if ($RegistryRepo) {
        return $RegistryRepo.Trim()
    }
    if ($GitHubRepo) {
        return "$Registry/$($GitHubRepo.Trim())"
    }
    return $null
}

function Get-RepoHistory {
    param(
        [object[]]$Entries,
        [string]$Repo
    )

    $filtered = @($Entries | Where-Object { $_ -and ([string]$_.repo -eq $Repo) })
    return @($filtered | Sort-Object { [DateTime]$_.createdAtUtc } -Descending)
}

function Show-TagTable {
    param([object[]]$Entries)

    if (-not $Entries -or $Entries.Count -eq 0) {
        Write-Host "No indexed versions available." -ForegroundColor DarkYellow
        return
    }

    Write-Host "Available versions:" -ForegroundColor Cyan
    $i = 1
    foreach ($entry in $Entries) {
        $created = ""
        if ($entry.createdAtUtc) {
            try {
                $created = ([DateTime]$entry.createdAtUtc).ToLocalTime().ToString("yyyy-MM-dd HH:mm")
            }
            catch {
                $created = [string]$entry.createdAtUtc
            }
        }

        $digestShort = ""
        if ($entry.digest -and ([string]$entry.digest).Length -gt 19) {
            $digestShort = ([string]$entry.digest).Substring(0, 19)
        } elseif ($entry.digest) {
            $digestShort = [string]$entry.digest
        }

        Write-Host ("[{0}] {1,-8} {2,-18} {3,-20} {4}" -f $i, [string]$entry.versionTag, $created, $digestShort, [string]$entry.image) -ForegroundColor White
        if ($entry.notes) {
            Write-Host ("      notes: {0}" -f [string]$entry.notes) -ForegroundColor DarkGray
        }
        $i++
    }
}

function Resolve-ImageRef {
    param(
        [string]$TargetRepo,
        [object[]]$RepoHistory
    )

    if ($Image) {
        return $Image.Trim()
    }

    if ($VersionTag) {
        if (-not $TargetRepo) {
            throw "-VersionTag requires -RegistryRepo or -GitHubRepo unless -Image is provided"
        }
        return "${TargetRepo}:$VersionTag"
    }

    if ($SelectTag) {
        if (-not $TargetRepo) {
            throw "-SelectTag requires -RegistryRepo or -GitHubRepo unless -Image is provided"
        }
        if (-not $RepoHistory -or $RepoHistory.Count -eq 0) {
            throw "No version history found for '$TargetRepo' in tag index"
        }

        if ($NonInteractive) {
            throw "-SelectTag cannot be used with -NonInteractive. Use -VersionTag instead."
        }

        Show-TagTable -Entries $RepoHistory
        $selectionRaw = Read-Host "Select version number"
        if (-not ($selectionRaw -as [int])) {
            throw "Invalid selection '$selectionRaw'"
        }
        $selection = [int]$selectionRaw
        if ($selection -lt 1 -or $selection -gt $RepoHistory.Count) {
            throw "Selection out of range. Choose 1..$($RepoHistory.Count)"
        }

        return [string]$RepoHistory[$selection - 1].image
    }

    if ($TargetRepo) {
        return "${TargetRepo}:$Tag"
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
    tagIndexFile = $TagIndexFile
}

try {
    $targetRepo = Resolve-TargetRepo
    $indexPath = Join-Path $PSScriptRoot $TagIndexFile
    $tagHistory = Get-TagHistory -IndexPath $indexPath
    $repoHistory = if ($targetRepo) { Get-RepoHistory -Entries $tagHistory.images -Repo $targetRepo } else { @() }

    if ($ListAvailableTags) {
        if (-not $targetRepo) {
            throw "-ListAvailableTags requires -RegistryRepo or -GitHubRepo"
        }

        Show-TagTable -Entries $repoHistory
        $result.success = $true
        $result.image = if ($repoHistory.Count -gt 0) { [string]$repoHistory[0].image } else { $null }
        $result.availableTags = @($repoHistory | ForEach-Object { [string]$_.versionTag })
        $result.tagIndexFile = $indexPath

        if ($OutputJson) {
            $result | ConvertTo-Json -Depth 6
        }
        exit 0
    }

    $imageRef = Resolve-ImageRef -TargetRepo $targetRepo -RepoHistory $repoHistory
    if (-not $imageRef) {
        throw "Image is required. Use -Image ghcr.io/<user-or-org>/webtodesk:latest or -GitHubRepo <user-or-org>/webtodesk [-Tag latest|-VersionTag vN|-SelectTag]"
    }
    $result.image = $imageRef
    $result.tagIndexFile = $indexPath

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
