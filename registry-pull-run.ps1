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
    [switch]$OpenBrowser,
    [int]$HealthTimeoutSec = 90
)

# Import common utilities
$utilitiesPath = Join-Path $PSScriptRoot ".windsurf/scripts/Common-Utilities.ps1"
if (Test-Path $utilitiesPath) {
    . $utilitiesPath
    $script:OutputJson = $OutputJson
} else {
    throw "Required utilities module not found at $utilitiesPath"
}

$result = New-ScriptResult -ScriptName "registry-pull-run.ps1"
$result.image = $null
$result.container = $ContainerName
$result.hostPort = $HostPort
$result.healthy = $false
$result.status = $null
$result.envFileUsed = $null
$result.tagIndexFile = $TagIndexFile

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

Invoke-WithErrorHandling -ScriptBlock {
    Write-Step "Resolving image reference" -Step 1 -Total 6
    
    $targetRepo = Resolve-TargetRepo
    $indexPath = Resolve-ScriptPath -RelativePath $TagIndexFile
    $tagHistory = Get-TagHistory -IndexPath $indexPath
    $repoHistory = if ($targetRepo) { Get-RepoHistory -Entries $tagHistory.images -Repo $targetRepo } else { @() }
    
    if ($ListAvailableTags) {
        if (-not $targetRepo) {
            throw "-ListAvailableTags requires -RegistryRepo or -GitHubRepo"
        }
        
        Write-Host ""
        Write-Host "Available versions for ${targetRepo}:" -ForegroundColor Cyan
        Show-TagTable -Entries $repoHistory
        
        $result.success = $true
        $result.image = if ($repoHistory.Count -gt 0) { [string]$repoHistory[0].image } else { $null }
        $result.availableTags = @($repoHistory | ForEach-Object { [string]$_.versionTag })
        $result.tagIndexFile = $indexPath
        
        Export-ScriptResult -Result $Result -OutputJson:$OutputJson
        exit 0
    }
    
    $imageRef = Resolve-ImageRef -TargetRepo $targetRepo -RepoHistory $repoHistory
    if (-not $imageRef) {
        throw "Image is required. Use -Image ghcr.io/<user-or-org>/webtodesk:latest or -GitHubRepo <user-or-org>/webtodesk [-Tag latest|-VersionTag vN|-SelectTag]"
    }
    
    $result.image = $imageRef
    $result.tagIndexFile = $indexPath
    Write-Info "Image: $imageRef"
    
    # Docker verification
    Write-Step "Verifying Docker" -Step 2 -Total 6
    
    if (-not (Test-DockerAvailable)) {
        throw "Docker is not running or not accessible"
    }
    Write-Success "Docker is available"
    
    # Pull image
    Write-Step "Pulling image" -Step 3 -Total 6
    
    if ($PullAlways -or (Test-Confirmation -Prompt "Pull image '$imageRef' now?" -DefaultYes -Force:$Force -NonInteractive:$NonInteractive)) {
        Write-Info "Pulling '$imageRef'..."
        $pullTime = Measure-Command {
            & docker pull $imageRef
            if ($LASTEXITCODE -ne 0) {
                throw "Failed to pull '$imageRef'"
            }
        }
        Write-Success "Image pulled in $($pullTime.ToString('mm\:ss'))"
    } else {
        Write-Info "Skipping pull (using local image if available)"
    }
    
    # Handle existing container
    Write-Step "Managing existing container" -Step 4 -Total 6
    
    if ($StopExisting) {
        $containerStatus = Get-ContainerStatus -ContainerName $ContainerName
        if ($containerStatus -ne "not_found") {
            Write-Info "Removing existing container..."
            & docker stop $ContainerName 2>$null | Out-Null
            & docker rm $ContainerName 2>$null | Out-Null
            Write-Success "Container removed"
        }
    }
    
    # Check port availability
    $portInUse = Test-PortInUse -Port $HostPort
    if ($portInUse) {
        if ($KillPortProcess) {
            foreach ($p in $portInUse) {
                $msg = "Kill process PID $($p.OwningProcess) on port $HostPort?"
                if (Test-Confirmation -Prompt $msg -Force:$Force -NonInteractive:$NonInteractive) {
                    Stop-Process -Id $p.OwningProcess -Force -ErrorAction SilentlyContinue
                    Write-Success "Killed process $($p.OwningProcess)"
                }
            }
        } else {
            $msg = "Port $HostPort is in use. Continue anyway?"
            if (-not (Test-Confirmation -Prompt $msg -DefaultYes -Force:$Force -NonInteractive:$NonInteractive)) {
                throw "Host port $HostPort is already in use"
            }
            Write-Warning "Port $HostPort is in use - continuing anyway"
        }
    }
    
    # Run container
    Write-Step "Starting container" -Step 5 -Total 6
    
    $envPath = Resolve-ScriptPath -RelativePath $EnvFile
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
        Write-Info "Using env file: $EnvFile"
    } else {
        Write-Warning "No env file found at '$EnvFile'"
    }
    
    $runArgs += $imageRef
    
    Write-Info "Run command: docker $($runArgs -join ' ')"
    $containerId = (& docker @runArgs 2>$null).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $containerId) {
        throw "Failed to start container from image '$imageRef'"
    }
    
    Write-Success "Container started: $containerId"
    
    # Wait for health endpoint
    Write-Step "Waiting for health endpoint" -Step 6 -Total 6
    
    $healthy = Wait-PortReady -Port $HostPort -TimeoutSec $HealthTimeoutSec -ServiceName "WebToDesk"
    
    # Get final status
    $status = docker ps --filter "id=$containerId" --format "{{.Status}}" 2>$null
    
    $result.healthy = $healthy
    $result.status = $status
    $result.url = "http://localhost:$HostPort"
    
    # Show results
    if (-not $OutputJson) {
        Write-Host ""
        Write-Host "Container Information:" -ForegroundColor Cyan
        Write-Host "  URL:       http://localhost:$HostPort" -ForegroundColor White
        Write-Host "  Image:     $imageRef" -ForegroundColor White
        Write-Host "  Container: $ContainerName ($containerId)" -ForegroundColor White
        Write-Host "  Status:    $status" -ForegroundColor White
        Write-Host ""
        Write-Host "Useful Commands:" -ForegroundColor Cyan
        Write-Host "  View logs: docker logs -f $ContainerName" -ForegroundColor Gray
        Write-Host "  Stop:      docker stop $ContainerName" -ForegroundColor Gray
        Write-Host "  Remove:    docker rm $ContainerName" -ForegroundColor Gray
        Write-Host ""
        
        if ($healthy) {
            Write-Success "Application is ready!"
            if ($OpenBrowser) {
                Write-Info "Opening browser..."
                Start-Process "http://localhost:$HostPort"
            }
        } else {
            Write-Warning "Application not yet ready - check logs"
        }
    }
    
} -Result $Result -ErrorMessage "Failed to pull and run container" -OutputJson:$OutputJson

Export-ScriptResult -Result $Result -OutputJson:$OutputJson
exit 0
