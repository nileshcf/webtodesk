[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [int]$HostPort = 7860,
    [switch]$StopExisting,
    [switch]$KillPortProcess,
    [switch]$Force,
    [switch]$NonInteractive,
    [switch]$OutputJson,
    [switch]$OpenBrowser,
    [string]$ContainerName = "webtodesk-app",
    [int]$HealthTimeoutSec = 90,
    [string]$ImageName = "webtodesk-webtodesk:latest"
)

# Import common utilities
$utilitiesPath = Join-Path $PSScriptRoot ".windsurf/scripts/Common-Utilities.ps1"
if (Test-Path $utilitiesPath) {
    . $utilitiesPath
    $script:OutputJson = $OutputJson
} else {
    throw "Required utilities module not found at $utilitiesPath"
}

$result = New-ScriptResult -ScriptName "docker-start.ps1"
$result.container = $ContainerName
$result.hostPort = $HostPort
$result.imageName = $ImageName
$result.healthy = $false
$result.status = $null

Invoke-WithErrorHandling -ScriptBlock {
    Write-Step "Verifying Docker" -Step 1 -Total 5
    
    if (-not (Test-DockerAvailable)) {
        throw "Docker is not running or not accessible"
    }
    Write-Success "Docker is available"
    
    # Enable BuildKit (consistent with docker-rebuild.ps1)
    $env:DOCKER_BUILDKIT = "1"
    $env:COMPOSE_DOCKER_CLI_BUILD = "1"
    
    # Verify image exists
    Write-Step "Checking Docker image" -Step 2 -Total 5
    
    if (-not (Get-DockerImageExists -ImageName $ImageName)) {
        throw "Image '$ImageName' not found. Run .\docker-rebuild.ps1 first."
    }
    Write-Success "Image found: $ImageName"
    
    # Handle existing container
    Write-Step "Managing existing container" -Step 3 -Total 5
    
    if ($StopExisting) {
        $containerStatus = Get-ContainerStatus -ContainerName $ContainerName
        if ($containerStatus -ne "not_found") {
            Write-Info "Stopping existing container..."
            docker compose down --remove-orphans 2>$null | Out-Null
            Write-Success "Container stopped"
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
    
    # Start the container
    Write-Step "Starting container" -Step 4 -Total 5
    
    Write-Info "Starting stack with docker compose up..."
    & docker compose up -d --force-recreate --remove-orphans
    if ($LASTEXITCODE -ne 0) {
        throw "Docker compose up failed. Check: docker compose logs $ContainerName"
    }
    
    # Wait for health endpoint
    Write-Step "Waiting for health endpoint" -Step 5 -Total 5
    
    $healthy = Wait-PortReady -Port $HostPort -TimeoutSec $HealthTimeoutSec -ServiceName "WebToDesk"
    
    # Get final status
    $status = docker compose ps --format "{{.Status}}" webtodesk 2>$null
    
    $result.healthy = $healthy
    $result.status = $status
    $result.url = "http://localhost:$HostPort"
    
    if ($healthy) {
        Write-Success "Stack is healthy and ready"
    } else {
        Write-Warning "Stack is running but health check failed"
        Write-Info "Services may still be starting. Check logs for details."
    }
    
    # Show helpful information
    if (-not $OutputJson) {
        Write-Host ""
        Write-Host "Container Information:" -ForegroundColor Cyan
        Write-Host "  URL:       http://localhost:$HostPort" -ForegroundColor White
        Write-Host "  Status:    $status" -ForegroundColor White
        Write-Host "  Container: $ContainerName" -ForegroundColor White
        Write-Host ""
        Write-Host "Useful Commands:" -ForegroundColor Cyan
        Write-Host "  View logs: docker compose logs -f $ContainerName" -ForegroundColor Gray
        Write-Host "  Stop:      docker compose down" -ForegroundColor Gray
        Write-Host "  Restart:   docker compose restart" -ForegroundColor Gray
        Write-Host ""
        
        # Auto-open browser if requested
        if ($OpenBrowser -and $healthy) {
            Write-Info "Opening browser..."
            Start-Process "http://localhost:$HostPort"
        }
    }
    
} -Result $Result -ErrorMessage "Failed to start Docker container" -OutputJson:$OutputJson

Export-ScriptResult -Result $Result -OutputJson:$OutputJson
exit 0
