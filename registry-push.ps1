[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$RegistryRepo,
    [string]$HubRepo,
    [string]$GitHubRepo = "nileshcf/webtodesk",
    [string]$Registry = "ghcr.io",
    [string]$EnvFile = ".env",
    [string]$PatEnvVarName = "PAT_DOCKER_GIT",
    [string]$RegistryUsername,
    [string]$SourceImage = "webtodesk-webtodesk:latest",
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

# Import common utilities
$utilitiesPath = Join-Path $PSScriptRoot ".windsurf/scripts/Common-Utilities.ps1"
if (Test-Path $utilitiesPath) {
    . $utilitiesPath
    $script:OutputJson = $OutputJson
} else {
    throw "Required utilities module not found at $utilitiesPath"
}

$result = New-ScriptResult -ScriptName "registry-push.ps1"
$result.sourceImage = $SourceImage
$result.registryRepo = $null
$result.baseTag = $Tag
$result.extraTags = $ExtraTags
$result.versionTag = $null
$result.tagIndexFile = $TagIndexFile
$result.buildFirst = [bool]$BuildFirst
$result.pushedImages = @()
$result.loginMode = $null

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

Invoke-WithErrorHandling -ScriptBlock {
    Write-Step "Resolving target repository" -Step 1 -Total 7
    
    $targetRepo = Resolve-TargetRepo
    if (-not $targetRepo) {
        throw "Repo is required. Use -RegistryRepo ghcr.io/<user-or-org>/webtodesk or -GitHubRepo <user-or-org>/webtodesk"
    }
    $result.registryRepo = $targetRepo
    Write-Info "Target repository: $targetRepo"
    
    # Setup version tagging
    Write-Step "Configuring version tags" -Step 2 -Total 7
    
    $indexPath = Resolve-ScriptPath -RelativePath $TagIndexFile
    $tagHistory = Get-TagHistory -IndexPath $indexPath
    $resolvedVersionTag = if ($VersionTag) { $VersionTag.Trim() } else { Get-NextVersionTag -Entries $tagHistory.images -Repo $targetRepo }
    
    if (-not $resolvedVersionTag -or $resolvedVersionTag -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$') {
        throw "Invalid version tag '$resolvedVersionTag'. Use letters, numbers, dot, underscore, and hyphen."
    }
    
    $result.versionTag = $resolvedVersionTag
    $result.tagIndexFile = $indexPath
    Write-Info "Version tag: $resolvedVersionTag"
    
    # Docker verification
    Write-Step "Verifying Docker" -Step 3 -Total 7
    
    if (-not (Test-DockerAvailable)) {
        throw "Docker is not running or not accessible"
    }
    Write-Success "Docker is available"
    
    # Enable BuildKit
    $env:DOCKER_BUILDKIT = "1"
    $env:COMPOSE_DOCKER_CLI_BUILD = "1"
    
    # Build if requested
    if ($BuildFirst) {
        Write-Step "Building Docker image" -Step 4 -Total 7
        
        $buildArgs = @("compose", "build")
        if ($NoCache) { $buildArgs += "--no-cache" }
        $buildArgs += @(
            "--build-arg", "NODE_MAJOR=$NodeMajor",
            "--build-arg", "ELECTRON_VERSION=$ElectronVersion",
            "--build-arg", "ELECTRON_BUILDER_VERSION=$ElectronBuilderVersion"
        )
        
        Write-Info "Build command: docker $($buildArgs -join ' ')"
        & docker @buildArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Docker build failed"
        }
        Write-Success "Build completed"
    }
    
    # Verify source image exists
    Write-Step "Verifying source image" -Step 5 -Total 7
    
    if (-not (Get-DockerImageExists -ImageName $SourceImage)) {
        throw "Source image '$SourceImage' not found. Build first or specify correct -SourceImage"
    }
    Write-Success "Source image found: $SourceImage"
    
    # Registry login if needed
    $registryHost = Resolve-RegistryHost -Repo $targetRepo
    if ($RunLogin) {
        Write-Step "Registry authentication" -Step 6 -Total 7
        
        if (-not (Test-Confirmation -Prompt "Run docker login for '$registryHost' now?" -Force:$Force -NonInteractive:$NonInteractive)) {
            throw "Docker login canceled"
        }
        
        # Try to get token from environment or .env file
        $token = [Environment]::GetEnvironmentVariable($PatEnvVarName)
        if (-not $token) {
            $envPath = Resolve-ScriptPath -RelativePath $EnvFile
            $token = Get-EnvValueFromDotEnv -FilePath $envPath -Key $PatEnvVarName
        }
        
        $loginUser = Resolve-RegistryUsername -Repo $targetRepo -RegistryHost $registryHost -ExplicitUsername $RegistryUsername
        
        if ($token -and $loginUser) {
            Write-Info "Using token from '$PatEnvVarName'"
            $token | docker login $registryHost -u $loginUser --password-stdin
            if ($LASTEXITCODE -eq 0) {
                $result.loginMode = "token:$PatEnvVarName"
                Write-Success "Login successful"
            } else {
                throw "Docker login failed using token"
            }
        } else {
            Write-Info "No token found, using interactive login"
            & docker login $registryHost
            if ($LASTEXITCODE -eq 0) {
                $result.loginMode = "interactive"
                Write-Success "Login successful"
            } else {
                throw "Docker login failed"
            }
        }
    }
    
    # Tag and push images
    Write-Step "Tagging and pushing images" -Step 7 -Total 7
    
    $aliasTags = @($Tag) + @($ExtraTags | Where-Object { $_ -and $_.Trim() })
    $allTags = @($resolvedVersionTag) + @($aliasTags) | Where-Object { $_ -and $_.Trim() } | Select-Object -Unique
    
    Write-Info "Tags to push: $($allTags -join ', ')"
    
    foreach ($itemTag in $allTags) {
        $targetImage = "${targetRepo}:$itemTag"
        
        # Tag the image
        Write-Info "Tagging as '$targetImage'"
        & docker tag $SourceImage $targetImage
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to tag image '$targetImage'"
        }
        
        # Push the image
        $msg = "Push '$targetImage' to registry?"
        if (Test-Confirmation -Prompt $msg -Force:$Force -NonInteractive:$NonInteractive) {
            Write-Info "Pushing '$targetImage'..."
            & docker push $targetImage
            if ($LASTEXITCODE -ne 0) {
                throw "Failed to push '$targetImage'"
            }
            $result.pushedImages += $targetImage
            Write-Success "Pushed: $targetImage"
        } else {
            Write-Warning "Skipped: $targetImage"
        }
    }
    
    if ($result.pushedImages.Count -eq 0) {
        throw "No images were pushed"
    }
    
    # Save to tag history
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
    
    # Show results
    if (-not $OutputJson) {
        Write-Host ""
        Write-Host "Publish Summary:" -ForegroundColor Cyan
        Write-Host "  Version:    $resolvedVersionTag" -ForegroundColor White
        Write-Host "  Repository: $targetRepo" -ForegroundColor White
        Write-Host "  Images:     $($result.pushedImages.Count) pushed" -ForegroundColor White
        if ($digest) {
            Write-Host "  Digest:     $digest" -ForegroundColor Gray
        }
        Write-Host ""
        Write-Host "Pull Commands:" -ForegroundColor Cyan
        Write-Host "  .\registry-pull-run.ps1 -GitHubRepo $($targetRepo -replace '^ghcr\.io/', '') -VersionTag $resolvedVersionTag" -ForegroundColor Gray
        Write-Host "  .\registry-pull-run.ps1 -GitHubRepo $($targetRepo -replace '^ghcr\.io/', '') -ListAvailableTags" -ForegroundColor Gray
        Write-Host ""
    }
    
} -Result $Result -ErrorMessage "Registry push failed" -OutputJson:$OutputJson

Export-ScriptResult -Result $Result -OutputJson:$OutputJson
exit 0
