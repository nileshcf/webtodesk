[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [switch]$NoCache,
    [switch]$RemoveOldImages,
    [switch]$PruneDangling,
    [switch]$Force,
    [switch]$NonInteractive,
    [switch]$OutputJson,
    [string]$ImageName = "webtodesk:latest",
    [string]$ContainerName = "webtodesk-app",
    [string]$NodeMajor = "20",
    [string]$ElectronVersion = "38.2.2",
    [string]$ElectronBuilderVersion = "26.0.12"
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
    script = "docker-rebuild.ps1"
    image = $ImageName
    container = $ContainerName
    noCache = [bool]$NoCache
    removeOldImages = [bool]$RemoveOldImages
    pruneDangling = [bool]$PruneDangling
    success = $false
    removedImages = @()
    removedContainer = $false
}

try {
    Write-Info "Checking Docker availability"
    $null = docker info 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker is not running or not accessible"
    }
    Write-Ok "Docker is available"

    # Enable BuildKit for --mount=type=cache (persistent cache mounts between builds)
    $env:DOCKER_BUILDKIT         = "1"
    $env:COMPOSE_DOCKER_CLI_BUILD = "1"

    Write-Info "Checking existing container '$ContainerName'"
    $existingContainer = docker ps -a --filter "name=$ContainerName" --format "{{.Names}}" 2>$null
    if ($existingContainer -and (Test-Yes -Prompt "Stop and remove existing stack '$ContainerName'?")) {
        if ($PSCmdlet.ShouldProcess($ContainerName, "docker compose down")) {
            docker compose down --remove-orphans 2>$null | Out-Null
            $result.removedContainer = $true
            Write-Ok "Removed existing stack"
        }
    }

    if ($RemoveOldImages) {
        $repo = $ImageName.Split(":")[0]
        Write-Info "Removing old images for repository '$repo'"
        $images = docker images $repo --format "{{.Repository}}:{{.Tag}}" 2>$null |
            Where-Object { $_ -and $_ -ne $ImageName }

        foreach ($img in $images) {
            if (Test-Yes -Prompt "Remove image '$img'?" ) {
                docker rmi $img 2>$null | Out-Null
                if ($LASTEXITCODE -eq 0) {
                    $result.removedImages += $img
                }
            }
        }
        Write-Ok "Removed $($result.removedImages.Count) old image(s)"
    }

    if ($PruneDangling) {
        Write-Info "Pruning dangling images"
        docker image prune -f 2>$null | Out-Null
        Write-Ok "Dangling images pruned"
    }

    # docker compose build inherits all context from docker-compose.yml and passes ARGs
    # to cache-mount layers (Node.js, Maven .m2, Wine, Electron prewarm)
    $buildArgs = @("compose", "build")
    if ($NoCache) { $buildArgs += "--no-cache" }
    $buildArgs += @(
        "--build-arg", "NODE_MAJOR=$NodeMajor",
        "--build-arg", "ELECTRON_VERSION=$ElectronVersion",
        "--build-arg", "ELECTRON_BUILDER_VERSION=$ElectronBuilderVersion"
    )

    Write-Info "Running: docker $($buildArgs -join ' ')"
    & docker @buildArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose build failed"
    }

    # Prune dangling images created by BuildKit intermediate layers
    Write-Info "Pruning dangling images..."
    docker image prune -f 2>$null | Out-Null
    Write-Ok "Dangling images pruned"

    $result.success = $true
    Write-Ok "Image build completed"

    if ($OutputJson) {
        $result | ConvertTo-Json -Depth 6
    } else {
        Write-Host ""
        Write-Host "Next:" -ForegroundColor Cyan
        Write-Host "  .\docker-start.ps1 -StopExisting -Force" -ForegroundColor White
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
