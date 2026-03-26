# ------------------------------------------------------
# WebToDesk - Start All Services (Local Development)
# Run: .\start-all.ps1
# ------------------------------------------------------

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  WebToDesk - Starting All Services"     -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$root = $PSScriptRoot

# 1. Discovery Service (Eureka - must start first)
Write-Host "[1/5] Starting Discovery Service (port 8761)..." -ForegroundColor Yellow
Start-Process -FilePath "cmd" -ArgumentList "/c cd /d $root\discovery-service && mvnw.cmd spring-boot:run" -WindowStyle Normal

# Wait for Eureka to be reachable before starting other services.
# This avoids races where dependent services try to register before the Eureka server is ready.
$eurekaHost = "localhost"
$eurekaPort = 8761
$eurekaUrl = "http://$eurekaHost`:$eurekaPort/eureka/apps"
$deadline = (Get-Date).AddSeconds(60)

while ((Get-Date) -lt $deadline) {
  $portReady = $false
  try {
    $portReady = Test-NetConnection -ComputerName $eurekaHost -Port $eurekaPort -InformationLevel Quiet
  } catch {
    $portReady = $false
  }

  if ($portReady) {
    $httpReady = $false
    try {
      # /eureka/apps is a lightweight endpoint that should respond once the server is up.
      $null = Invoke-WebRequest -Uri $eurekaUrl -Method GET -TimeoutSec 2 -UseBasicParsing
      $httpReady = $true
    } catch {
      $httpReady = $false
    }

    if ($httpReady) { break }
  }

  Start-Sleep -Seconds 2
}

if (-not (Test-NetConnection -ComputerName $eurekaHost -Port $eurekaPort -InformationLevel Quiet)) {
  Write-Host "Warning: Eureka did not become ready within timeout. Continuing anyway..." -ForegroundColor Red
}

# 2. User Service
Write-Host "[2/5] Starting User Service (port 8081)..." -ForegroundColor Yellow
Start-Process -FilePath "cmd" -ArgumentList "/c cd /d $root\user-service && mvnw.cmd spring-boot:run" -WindowStyle Normal
Start-Sleep -Seconds 5

# 3. Conversion Service
Write-Host "[3/5] Starting Conversion Service (port 8082)..." -ForegroundColor Yellow
Start-Process -FilePath "cmd" -ArgumentList "/c cd /d $root\conversion-service && mvnw.cmd spring-boot:run" -WindowStyle Normal
Start-Sleep -Seconds 5

# 4. API Gateway
Write-Host "[4/5] Starting API Gateway (port 8080)..." -ForegroundColor Yellow
Start-Process -FilePath "cmd" -ArgumentList "/c cd /d $root\api-gateway && mvnw.cmd spring-boot:run" -WindowStyle Normal
Start-Sleep -Seconds 5

# 5. React Frontend (Vite)
Write-Host "[5/5] Starting React Frontend (port 5173)..." -ForegroundColor Yellow
Start-Process -FilePath "cmd" -ArgumentList "/c cd /d $root\frontend && npm run dev" -WindowStyle Normal

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  All services starting!"               -ForegroundColor Green
Write-Host "  Eureka:    http://localhost:8761"      -ForegroundColor White
Write-Host "  Gateway:   http://localhost:8080"      -ForegroundColor White
Write-Host "  User:      http://localhost:8081"      -ForegroundColor White
Write-Host "  Converter: http://localhost:8082"      -ForegroundColor White
Write-Host "  Frontend:  http://localhost:5173"      -ForegroundColor White
Write-Host "========================================" -ForegroundColor Green
