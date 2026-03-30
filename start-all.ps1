[CmdletBinding()]
param(
    [string]$JavaHome = "C:\Program Files\Java\jdk-17",
    [switch]$ForceKillPorts,
    [switch]$NonInteractive,
    [switch]$NoBrowserPrompt,
    [switch]$OutputJson,
    [switch]$HiddenWindows,
    [switch]$OpenBrowser,
    [int]$ReadyTimeoutSec = 60,
    [switch]$SkipEnvLoad
)

# Import common utilities
$utilitiesPath = Join-Path $PSScriptRoot ".windsurf/scripts/Common-Utilities.ps1"
if (Test-Path $utilitiesPath) {
    . $utilitiesPath
    $script:OutputJson = $OutputJson
} else {
    throw "Required utilities module not found at $utilitiesPath"
}

$root = $PSScriptRoot
$env:JAVA_HOME = $JavaHome
$windowStyle = if ($HiddenWindows) { "Hidden" } else { "Normal" }

$result = New-ScriptResult -ScriptName "start-all.ps1"
$result.root = $root
$result.javaHome = $env:JAVA_HOME
$result.services = @()

Invoke-WithErrorHandling -ScriptBlock {
    Write-Step "Verifying Java installation" -Step 1 -Total 5
    
    if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        throw "Java not found at $env:JAVA_HOME"
    }
    
    $javaVersion = & "$env:JAVA_HOME\bin\java.exe" -version 2>&1
    Write-Success "Java found at $env:JAVA_HOME"
    Write-Info "Version: $($javaVersion[0])"
    
    # Load environment
    Write-Step "Loading environment" -Step 2 -Total 5
    
    if (-not $SkipEnvLoad) {
        Import-EnvironmentFile -EnvFile ".env" -Required:$false
    } else {
        Write-Info "Skipping environment file load"
    }
    
    # Define services
    Write-Step "Preparing services" -Step 3 -Total 5
    
    $services = @(
        [ordered]@{ Key = "Discovery"; Name = "Eureka Discovery Service"; Port = 8761; Path = "discovery-service"; Type = "maven" },
        [ordered]@{ Key = "User"; Name = "User Service"; Port = 8081; Path = "user-service"; Type = "maven" },
        [ordered]@{ Key = "Conversion"; Name = "Conversion Service"; Port = 8082; Path = "conversion-service"; Type = "maven" },
        [ordered]@{ Key = "Gateway"; Name = "API Gateway"; Port = 8080; Path = "api-gateway"; Type = "maven" },
        [ordered]@{ Key = "Frontend"; Name = "React Frontend"; Port = 7860; Path = "frontend"; Type = "npm" }
    )
    
    Write-Info "Starting $($services.Count) services"
    
    # Start services
    Write-Step "Starting services" -Step 4 -Total 5
    
    foreach ($svc in $services) {
        Write-Host "" 
        Write-Info "Starting $($svc.Name) on port $($svc.Port)"
        
        # Check port availability
        $portInUse = Test-PortInUse -Port $svc.Port
        if ($portInUse) {
            $portProcess = $portInUse | Select-Object -First 1
            $msg = "Port $($svc.Port) is in use. Kill PID $($portProcess.OwningProcess)?"
            if (Test-Confirmation -Prompt $msg -Force:$ForceKillPorts -NonInteractive:$NonInteractive) {
                Stop-Process -Id $portProcess.OwningProcess -Force -ErrorAction SilentlyContinue
                Start-Sleep -Seconds 2
                Write-Success "Process killed"
            } else {
                Write-Warning "Skipping $($svc.Name) - port in use"
                continue
            }
        }
        
        # Build command
        if ($svc.Type -eq "npm") {
            $command = '/c cd /d "{0}\{1}" && npm run dev' -f $root, $svc.Path
        } else {
            # Pass MONGODB_URI (and any other -D overrides) so Spring Boot picks up cloud credentials
            $mongoArg = if ($env:MONGODB_URI) { ' "-DMONGODB_URI={0}"' -f $env:MONGODB_URI } else { "" }
            $command = '/c cd /d "{0}\{1}" && ..\mvnw.cmd spring-boot:run{2}' -f $root, $svc.Path, $mongoArg
        }
        
        # Start process
        $process = Start-Process -FilePath "cmd" -ArgumentList $command -WindowStyle $windowStyle -PassThru
        
        # Wait for service to be ready
        $ready = Wait-PortReady -Port $svc.Port -TimeoutSec $ReadyTimeoutSec -ServiceName $svc.Name
        
        $serviceResult = [ordered]@{
            key = $svc.Key
            name = $svc.Name
            port = $svc.Port
            pid = $process.Id
            ready = $ready
            status = if ($ready) { "Running" } else { "Starting" }
            url = "http://localhost:$($svc.Port)"
        }
        
        $result.services += $serviceResult
        
        if ($ready) {
            Write-Success "$($svc.Name) is ready"
        } else {
            Write-Warning "$($svc.Name) is still starting (PID: $($process.Id))"
        }
    }
    
    # Final status
    Write-Step "Final status" -Step 5 -Total 5
    
    $runningCount = ($result.services | Where-Object { $_.ready }).Count
    $result.runningCount = $runningCount
    $result.totalCount = $result.services.Count
    $result.success = ($runningCount -ge 4)
    
    # Show results
    if (-not $OutputJson) {
        Write-Host ""
        Write-Host "Services Summary:" -ForegroundColor Cyan
        Write-Host "  Running: $runningCount/$($result.services.Count)" -ForegroundColor White
        Write-Host ""
        
        foreach ($svc in $result.services) {
            $icon = if ($svc.ready) { "✓" } else { "⏳" }
            Write-Host "$icon $($svc.name):" -ForegroundColor $(if ($svc.ready) { "Green" } else { "Yellow" })
            Write-Host "  URL:  $($svc.url)" -ForegroundColor Gray
            Write-Host "  PID:  $($svc.pid)" -ForegroundColor Gray
            Write-Host ""
        }
        
        # Show access URLs
        Write-Host "Access URLs:" -ForegroundColor Cyan
        Write-Host "  Frontend:     http://localhost:7860" -ForegroundColor White
        Write-Host "  API Gateway:  http://localhost:8080" -ForegroundColor White
        Write-Host "  User Service: http://localhost:8081" -ForegroundColor White
        Write-Host "  Conversion:   http://localhost:8082" -ForegroundColor White
        Write-Host "  Discovery:    http://localhost:8761" -ForegroundColor White
        Write-Host ""
        
        # Browser prompt
        if ($OpenBrowser -and ($result.services | Where-Object { $_.key -eq "Frontend" -and $_.ready })) {
            Write-Info "Opening frontend in browser..."
            Start-Process "http://localhost:7860"
        } elseif (-not $NoBrowserPrompt -and -not $NonInteractive -and ($result.services | Where-Object { $_.key -eq "Frontend" -and $_.ready })) {
            $openBrowser = Read-Host "Open frontend in browser? (y/N)"
            if ($openBrowser -eq "y" -or $openBrowser -eq "Y") {
                Start-Process "http://localhost:7860"
            }
        }
        
        # Show helpful commands
        Write-Host "Useful Commands:" -ForegroundColor Cyan
        Write-Host "  Stop all:     Get-Process cmd | Where-Object { $_.MainWindowTitle -like '*mvnw*' -or $_.MainWindowTitle -like '*npm*' } | Stop-Process" -ForegroundColor Gray
        Write-Host "  View logs:    Check each service window" -ForegroundColor Gray
        Write-Host "  Restart:      .\start-all.ps1 -ForceKillPorts" -ForegroundColor Gray
        Write-Host ""
    }
    
} -Result $Result -ErrorMessage "Failed to start services" -OutputJson:$OutputJson

Export-ScriptResult -Result $Result -OutputJson:$OutputJson

if ($result.success) {
    exit 0
}

exit 1
