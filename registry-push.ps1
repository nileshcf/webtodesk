[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$RegistryRepo,
    [string]$HubRepo,
    [string]$GitHubRepo,
    [string]$Registry = "ghcr.io",
    [string]$EnvFile = ".env",
    [string]$PatEnvVarName = "PAT_DOCKER_GIT",
    [string]$RegistryUsername,
    [string]$SourceImage = "webtodesk:latest",
    [string]$Tag = "latest",
    [string[]]$ExtraTags = @(),
    [switch]$BuildFirst,
    [switch]$NoCache,
    [switch]$RunLogin,
    [string]$NodeMajor = "20",
    [string]$ElectronVersion = "38.2.2",
    [string]$ElectronBuilderVersion = "26.0.12",
    [switch]$Force,
    [switch]$NonInteractive,
    [switch]$OutputJson
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

function Resolve-TargetRepo {
    if ($RegistryRepo) {
        return $RegistryRepo.Trim()
    }

    if ($GitHubRepo) {
        return "$Registry/$($GitHubRepo.Trim())"
    }

    if ($HubRepo) {
        return $HubRepo.Trim()
    }

    return $null
}

function Resolve-RegistryHost {
    param([string]$Repo)

    if (-not $Repo) { return "docker.io" }
    $first = ($Repo -split "/")[0]
    if ($first -match "[\.:]" -or $first -eq "localhost") {
        return $first
    }
    return "docker.io"
}

function Get-EnvValueFromDotEnv {
    param(
        [string]$FilePath,
        [string]$Key
    )

    if (-not (Test-Path $FilePath) -or -not $Key) {
        return $null
    }

    foreach ($line in (Get-Content -Path $FilePath -ErrorAction SilentlyContinue)) {
        if (-not $line) { continue }
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) { continue }

        if ($trimmed -match "^([A-Za-z_][A-Za-z0-9_]*)=(.*)$") {
            $name = $Matches[1]
            $value = $Matches[2]
            if ($name -eq $Key) {
                $clean = $value.Trim()
                if (($clean.StartsWith('"') -and $clean.EndsWith('"')) -or ($clean.StartsWith("'") -and $clean.EndsWith("'"))) {
                    $clean = $clean.Substring(1, $clean.Length - 2)
                }
                return $clean
            }
        }
    }

    return $null
}

function Resolve-RegistryUsername {
    param(
        [string]$Repo,
        [string]$RegistryHost,
        [string]$ExplicitUsername
    )

    if ($ExplicitUsername) {
        return $ExplicitUsername.Trim()
    }

    if ($env:GITHUB_USERNAME) {
        return $env:GITHUB_USERNAME.Trim()
    }

    if ($RegistryHost -eq "ghcr.io" -and $Repo -match "^ghcr\.io/([^/]+)/") {
        return $Matches[1]
    }

    return $null
}

$result = [ordered]@{
    script = "registry-push.ps1"
    sourceImage = $SourceImage
    registryRepo = $null
    baseTag = $Tag
    extraTags = $ExtraTags
    buildFirst = [bool]$BuildFirst
    success = $false
    pushedImages = @()
    loginMode = $null
}

try {
    $targetRepo = Resolve-TargetRepo
    if (-not $targetRepo) {
        throw "Repo is required. Use -RegistryRepo ghcr.io/<user-or-org>/webtodesk or -GitHubRepo <user-or-org>/webtodesk"
    }
    $result.registryRepo = $targetRepo

    $registryHost = Resolve-RegistryHost -Repo $targetRepo

    Write-Info "Checking Docker availability"
    $null = docker info 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker is not running or not accessible"
    }
    Write-Ok "Docker is available"

    $env:DOCKER_BUILDKIT = "1"
    $env:COMPOSE_DOCKER_CLI_BUILD = "1"

    if ($BuildFirst) {
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
        Write-Ok "Build completed"
    }

    $sourceExists = docker images --format "{{.Repository}}:{{.Tag}}" 2>$null |
        Where-Object { $_ -eq $SourceImage }
    if (-not $sourceExists) {
        throw "Source image '$SourceImage' not found. Build first or pass correct -SourceImage"
    }

    if ($RunLogin) {
        if (-not (Confirm-Action "Run docker login for '$registryHost' now?")) {
            throw "docker login canceled"
        }

        $token = [Environment]::GetEnvironmentVariable($PatEnvVarName)
        if (-not $token) {
            $envPath = Join-Path $PSScriptRoot $EnvFile
            $token = Get-EnvValueFromDotEnv -FilePath $envPath -Key $PatEnvVarName
        }

        $loginUser = Resolve-RegistryUsername -Repo $targetRepo -RegistryHost $registryHost -ExplicitUsername $RegistryUsername

        if ($token -and $loginUser) {
            Write-Info "Using token from '$PatEnvVarName' for registry login"
            $token | docker login $registryHost -u $loginUser --password-stdin
            if ($LASTEXITCODE -ne 0) {
                throw "docker login failed using token from '$PatEnvVarName'"
            }
            $result.loginMode = "token:$PatEnvVarName"
        } else {
            if (-not $token) {
                Write-Info "No '$PatEnvVarName' found in process env or '$EnvFile'; using interactive login"
            } elseif (-not $loginUser) {
                Write-Info "Could not infer registry username; using interactive login"
            }

            & docker login $registryHost
            if ($LASTEXITCODE -ne 0) {
                throw "docker login failed"
            }
            $result.loginMode = "interactive"
        }
        Write-Ok "docker login completed for '$registryHost'"
    }

    $allTags = @($Tag) + @($ExtraTags | Where-Object { $_ -and $_.Trim() } | Select-Object -Unique)

    foreach ($itemTag in $allTags) {
        $targetImage = "${targetRepo}:$itemTag"

        Write-Info "Tagging '$SourceImage' as '$targetImage'"
        & docker tag $SourceImage $targetImage
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to tag image '$targetImage'"
        }

        if (-not (Confirm-Action "Push image '$targetImage' to registry?")) {
            Write-Info "Skipped push for '$targetImage'"
            continue
        }

        if ($PSCmdlet.ShouldProcess($targetImage, "docker push")) {
            Write-Info "Pushing '$targetImage'"
            & docker push $targetImage
            if ($LASTEXITCODE -ne 0) {
                throw "Failed to push '$targetImage'"
            }
            $result.pushedImages += $targetImage
            Write-Ok "Pushed '$targetImage'"
        }
    }

    if ($result.pushedImages.Count -eq 0) {
        throw "No images were pushed"
    }

    $result.success = $true

    if ($OutputJson) {
        $result | ConvertTo-Json -Depth 6
    } else {
        Write-Host "" 
        Write-Host "Pull/run script for testers:" -ForegroundColor Cyan
        Write-Host "  .\registry-pull-run.ps1 -Image $($result.pushedImages[0]) -StopExisting" -ForegroundColor White
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
