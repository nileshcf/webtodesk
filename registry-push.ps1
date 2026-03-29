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
    [string]$VersionTag,
    [string]$TagIndexFile = ".registry-image-tags.json",
    [string]$Notes,
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

function Save-TagHistory {
    param(
        [string]$IndexPath,
        [object]$History
    )

    $parent = Split-Path -Parent $IndexPath
    if ($parent -and -not (Test-Path $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }

    $History | ConvertTo-Json -Depth 8 | Set-Content -Path $IndexPath -Encoding UTF8
}

function Get-NextVersionTag {
    param(
        [object[]]$Entries,
        [string]$Repo
    )

    $max = 0
    foreach ($entry in @($Entries)) {
        if (-not $entry) { continue }
        if ([string]$entry.repo -ne $Repo) { continue }
        $candidate = [string]$entry.versionTag
        if ($candidate -match '^v(\d+)$') {
            $num = [int]$Matches[1]
            if ($num -gt $max) {
                $max = $num
            }
        }
    }

    return "v$($max + 1)"
}

function Get-RepoDigest {
    param([string]$ImageRef)

    $digestRef = (& docker image inspect --format "{{index .RepoDigests 0}}" $ImageRef 2>$null)
    if ($LASTEXITCODE -ne 0 -or -not $digestRef) {
        return $null
    }

    $digestRef = $digestRef.Trim()
    if (-not $digestRef) {
        return $null
    }

    if ($digestRef -match '@(sha256:[a-fA-F0-9]{64})$') {
        return $Matches[1]
    }

    return $digestRef
}

$result = [ordered]@{
    script = "registry-push.ps1"
    sourceImage = $SourceImage
    registryRepo = $null
    baseTag = $Tag
    extraTags = $ExtraTags
    versionTag = $null
    tagIndexFile = $TagIndexFile
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

    $indexPath = Join-Path $PSScriptRoot $TagIndexFile
    $tagHistory = Get-TagHistory -IndexPath $indexPath
    $resolvedVersionTag = if ($VersionTag) { $VersionTag.Trim() } else { Get-NextVersionTag -Entries $tagHistory.images -Repo $targetRepo }
    if (-not $resolvedVersionTag -or $resolvedVersionTag -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$') {
        throw "Invalid version tag '$resolvedVersionTag'. Use letters, numbers, dot, underscore, and hyphen."
    }
    $result.versionTag = $resolvedVersionTag
    $result.tagIndexFile = $indexPath

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

    $aliasTags = @($Tag) + @($ExtraTags | Where-Object { $_ -and $_.Trim() })
    $allTags = @($resolvedVersionTag) + @($aliasTags) | Where-Object { $_ -and $_.Trim() } | Select-Object -Unique

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

    $versionImage = "${targetRepo}:$resolvedVersionTag"
    $digest = Get-RepoDigest -ImageRef $versionImage
    $historyEntry = [ordered]@{
        repo = $targetRepo
        versionTag = $resolvedVersionTag
        image = $versionImage
        sourceImage = $SourceImage
        pushedTags = @($allTags)
        digest = $digest
        createdAtUtc = (Get-Date).ToUniversalTime().ToString("o")
        notes = $Notes
    }
    $tagHistory.images = @($tagHistory.images) + @($historyEntry)
    Save-TagHistory -IndexPath $indexPath -History $tagHistory
    $result.historyEntry = $historyEntry

    $result.success = $true

    if ($OutputJson) {
        $result | ConvertTo-Json -Depth 6
    } else {
        Write-Host "" 
        Write-Host "Published version: $resolvedVersionTag" -ForegroundColor Cyan
        if ($digest) {
            Write-Host "Digest: $digest" -ForegroundColor White
        }
        Write-Host "Tag index: $indexPath" -ForegroundColor White
        Write-Host "Pull/run script for testers:" -ForegroundColor Cyan
        Write-Host "  .\registry-pull-run.ps1 -GitHubRepo $($targetRepo -replace '^ghcr\.io/', '') -VersionTag $resolvedVersionTag -StopExisting" -ForegroundColor White
        Write-Host "  .\registry-pull-run.ps1 -GitHubRepo $($targetRepo -replace '^ghcr\.io/', '') -ListAvailableTags" -ForegroundColor White
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
