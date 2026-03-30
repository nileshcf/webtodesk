[CmdletBinding()]
param(
    [string]$JavaHome = "C:\Program Files\Java\jdk-17",
    [switch]$ForceKillPorts,
    [switch]$NonInteractive,
    [switch]$NoBrowserPrompt,
    [switch]$OutputJson,
    [switch]$HiddenWindows,
    [int]$ReadyTimeoutSec = 60
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

function Test-Port {
    param([int]$Port)
    try {
        $connection = New-Object System.Net.Sockets.TcpClient
        $connection.Connect("localhost", $Port)
        $connection.Close()
        return $true
    } catch {
        return $false
    }
}

function Test-Yes {
    param([string]$Prompt)

    if ($ForceKillPorts -or $NonInteractive) {
        return $true
    }

    $answer = Read-Host "$Prompt (y/N)"
    return ($answer -eq "y" -or $answer -eq "Y")
}

function Wait-PortReady {
    param([int]$Port, [int]$TimeoutSec)

    $start = Get-Date
    while (((Get-Date) - $start).TotalSeconds -lt $TimeoutSec) {
        if (Test-Port -Port $Port) {
            return $true
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

$root = $PSScriptRoot
$env:JAVA_HOME = $JavaHome
$windowStyle = if ($HiddenWindows) { "Hidden" } else { "Normal" }

# Load .env into the current process so Spring Boot services get MONGODB_URI,
# R2_* credentials, DEVELOPMENT_BUILD, and any other secrets at runtime.
$envFile = Join-Path $root ".env"
if (Test-Path $envFile) {
    Get-Content $envFile |
        Where-Object { $_ -notmatch '^\s*#' -and $_ -notmatch '^\s*$' -and $_ -match '=' } |
        ForEach-Object {
            $key, $val = $_ -split '=', 2
            [System.Environment]::SetEnvironmentVariable($key.Trim(), $val.Trim(), 'Process')
        }
    Write-Info "Loaded environment from .env"
} else {
    Write-Info "No .env file found at $envFile - services may fail if cloud credentials are required"
}

$result = [ordered]@{
    script = "start-all.ps1"
    root = $root
    javaHome = $env:JAVA_HOME
    success = $false
    services = @()
}

if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $result.error = "Java not found at $env:JAVA_HOME"
    Write-Fail $result.error
    if ($OutputJson) {
        $result | ConvertTo-Json -Depth 6
    }
    exit 1
}

$services = @(
    [ordered]@{ Key = "Discovery"; Name = "Eureka Discovery Service"; Port = 8761; Path = "discovery-service"; Type = "maven" },
    [ordered]@{ Key = "User"; Name = "User Service"; Port = 8081; Path = "user-service"; Type = "maven" },
    [ordered]@{ Key = "Conversion"; Name = "Conversion Service"; Port = 8082; Path = "conversion-service"; Type = "maven" },
    [ordered]@{ Key = "Gateway"; Name = "API Gateway"; Port = 8080; Path = "api-gateway"; Type = "maven" },
    [ordered]@{ Key = "Frontend"; Name = "React Frontend"; Port = 7860; Path = "frontend"; Type = "npm" }
)

foreach ($svc in $services) {
    Write-Info "Starting $($svc.Name) on port $($svc.Port)"

    if (Test-Port -Port $svc.Port) {
        $portProcess = Get-NetTCPConnection -LocalPort $svc.Port -ErrorAction SilentlyContinue |
            Where-Object { $_.State -eq "Listen" } |
            Select-Object -First 1

        if ($portProcess) {
            if (Test-Yes -Prompt "Port $($svc.Port) is in use. Kill PID $($portProcess.OwningProcess)?") {
                Stop-Process -Id $portProcess.OwningProcess -Force -ErrorAction SilentlyContinue
                Start-Sleep -Seconds 2
            }
        }
    }

    if ($svc.Type -eq "npm") {
        $command = '/c cd /d "{0}\{1}" && npm run dev' -f $root, $svc.Path
    } else {
        # Pass MONGODB_URI (and any other -D overrides) so Spring Boot picks up cloud credentials
        $mongoArg = if ($env:MONGODB_URI) { ' "-DMONGODB_URI={0}"' -f $env:MONGODB_URI } else { "" }
        $command = '/c cd /d "{0}\{1}" && ..\mvnw.cmd spring-boot:run{2}' -f $root, $svc.Path, $mongoArg
    }

    $process = Start-Process -FilePath "cmd" -ArgumentList $command -WindowStyle $windowStyle -PassThru
    $ready = Wait-PortReady -Port $svc.Port -TimeoutSec $ReadyTimeoutSec

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
        Write-Ok "$($svc.Name) is reachable on port $($svc.Port)"
    } else {
        Write-Info "$($svc.Name) is still starting"
    }
}

$runningCount = ($result.services | Where-Object { $_.ready }).Count
$result.runningCount = $runningCount
$result.totalCount = $result.services.Count
$result.success = ($runningCount -ge 4)

if ($OutputJson) {
    $result | ConvertTo-Json -Depth 8
} else {
    Write-Host ""
    Write-Host "Services Running: $runningCount/$($result.services.Count)" -ForegroundColor Cyan
    foreach ($svc in $result.services) {
        Write-Host "- $($svc.name): $($svc.status) | PID=$($svc.pid) | $($svc.url)" -ForegroundColor White
    }

    if (-not $NoBrowserPrompt -and -not $NonInteractive) {
        $openBrowser = Read-Host "Open frontend in browser? (y/N)"
        if ($openBrowser -eq "y" -or $openBrowser -eq "Y") {
            Start-Process "http://localhost:7860"
        }
    }
}

if ($result.success) {
    exit 0
}

exit 1
