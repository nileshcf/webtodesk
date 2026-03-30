[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [switch]$NoCache,
    [switch]$RemoveOldImages,
    [switch]$PruneDangling,
    [switch]$PruneAll,
    [switch]$Force,
    [switch]$NonInteractive,
    [switch]$OutputJson,
    [switch]$SkipHealthCheck,
    [string]$ImageName = "webtodesk-webtodesk:latest",
    [string]$ContainerName = "webtodesk-app",
    [string]$NodeMajor = "20",
    [string]$ElectronVersion = "38.2.2",
    [string]$ElectronBuilderVersion = "26.0.12",
    [string]$BuildTarget = "auto"
)

# Import common utilities
$utilitiesPath = Join-Path $PSScriptRoot ".windsurf/scripts/Common-Utilities.ps1"
if (Test-Path $utilitiesPath) {
    . $utilitiesPath
    $script:OutputJson = $OutputJson
} else {
    throw "Required utilities module not found at $utilitiesPath"
}

$result = New-ScriptResult -ScriptName "docker-rebuild.ps1"
$result.image = $ImageName
$result.container = $ContainerName
$result.noCache = [bool]$NoCache
$result.removeOldImages = [bool]$RemoveOldImages
$result.pruneDangling = [bool]$PruneDangling
$result.pruneAll = [bool]$PruneAll
$result.removedImages = @()
$result.removedContainer = $false

Invoke-WithErrorHandling -ScriptBlock {
    Write-Step "Verifying Docker" -Step 1 -Total 6
    
    if (-not (Test-DockerAvailable)) {
        throw "Docker is not running or not accessible"
    }
    Write-Success "Docker is available"
    
    # Enable BuildKit for improved caching
    $env:DOCKER_BUILDKIT = "1"
    $env:COMPOSE_DOCKER_CLI_BUILD = "1"
    Write-Info "BuildKit enabled"
    
    # Check for existing container
    Write-Step "Checking existing container" -Step 2 -Total 6
    $containerStatus = Get-ContainerStatus -ContainerName $ContainerName
    
    if ($containerStatus -ne "not_found") {
        $msg = "Container '$ContainerName' exists ($containerStatus). Remove it?"
        if (Test-Confirmation -Prompt $msg -Force:$Force -NonInteractive:$NonInteractive) {
            Write-Info "Removing container..."
            docker compose down --remove-orphans 2>$null | Out-Null
            $result.removedContainer = $true
            Write-Success "Container removed"
        } else {
            Write-Warning "Skipping container removal"
        }
    }
    
    # Remove old images if requested
    if ($RemoveOldImages) {
        Write-Step "Removing old images" -Step 3 -Total 6
        $repo = $ImageName.Split(":")[0]
        Write-Info "Finding old images for repository '$repo'"
        
        $images = docker images $repo --format "{{.Repository}}:{{.Tag}}" 2>$null |
            Where-Object { $_ -and $_ -ne $ImageName }
        
        if ($images) {
            foreach ($img in $images) {
                $msg = "Remove image '$img'?"
                if (Test-Confirmation -Prompt $msg -Force:$Force -NonInteractive:$NonInteractive) {
                    docker rmi $img 2>$null | Out-Null
                    if ($LASTEXITCODE -eq 0) {
                        $result.removedImages += $img
                        Write-Success "Removed: $img"
                    } else {
                        Write-Warning "Failed to remove: $img"
                    }
                }
            }
        } else {
            Write-Info "No old images found"
        }
    }
    
    # Prune dangling images
    if ($PruneDangling -or $PruneAll) {
        Write-Step "Pruning dangling images" -Step 4 -Total 6
        docker image prune -f 2>$null | Out-Null
        Write-Success "Dangling images pruned"
    }
    
    # Build the image
    Write-Step "Building Docker image" -Step 5 -Total 6
    
    $buildArgs = @("compose", "build")
    if ($NoCache) { 
        $buildArgs += "--no-cache"
        Write-Info "No-cache mode enabled"
    }
    
    $buildArgs += @(
        "--build-arg", "NODE_MAJOR=$NodeMajor",
        "--build-arg", "ELECTRON_VERSION=$ElectronVersion",
        "--build-arg", "ELECTRON_BUILDER_VERSION=$ElectronBuilderVersion",
        "--build-arg", "WEBTODESK_BUILD_TARGET_PLATFORM=$BuildTarget"
    )
    
    Write-Info "Build command: docker $($buildArgs -join ' ')"
    
    $buildTime = Measure-Command {
        & docker @buildArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Docker build failed. Check logs with: docker compose logs $ContainerName"
        }
    }
    
    Write-Success "Build completed in $($buildTime.ToString('mm\:ss'))"
    
    # Final cleanup
    Write-Step "Final cleanup" -Step 6 -Total 6
    
    if ($PruneAll) {
        Write-Info "Pruning all unused resources..."
        docker system prune -f 2>$null | Out-Null
        Write-Success "System prune completed"
    } else {
        # Always prune dangling images after build
        docker image prune -f 2>$null | Out-Null
        Write-Info "Cleaned up dangling images"
    }
    
    # Verify image was created
    if (-not (Get-DockerImageExists -ImageName $ImageName)) {
        throw "Build completed but image '$ImageName' not found"
    }
    
    Write-Success "Image '$ImageName' is ready"
    
    # Show next steps
    if (-not $OutputJson) {
        Write-Host ""
        Write-Host "Next steps:" -ForegroundColor Cyan
        Write-Host "  1. Start the container:" -ForegroundColor White
        Write-Host "     .\docker-start.ps1 -StopExisting" -ForegroundColor Gray
        Write-Host "  2. Or run locally:" -ForegroundColor White
        Write-Host "     .\start-all.ps1" -ForegroundColor Gray
        Write-Host ""
    }
    
} -Result $Result -ErrorMessage "Docker rebuild failed" -OutputJson:$OutputJson

Export-ScriptResult -Result $Result -OutputJson:$OutputJson
exit 0
