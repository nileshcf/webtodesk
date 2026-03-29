[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$SinceRef = "HEAD~1",
    [switch]$OnlyWorkingTree,
    [string]$PromptFile = ".ai/app-doc-update-brief.md",
    [switch]$RunAgent,
    [string]$AgentCommand,
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

function Invoke-Git {
    param(
        [string[]]$GitArgs,
        [switch]$AllowFailure
    )

    $out = (& git @GitArgs 2>&1)
    $code = $LASTEXITCODE
    if (-not $AllowFailure -and $code -ne 0) {
        throw "git $($GitArgs -join ' ') failed: $($out -join [Environment]::NewLine)"
    }

    return [ordered]@{
        exitCode = $code
        output = @($out)
    }
}

function Parse-NameStatusLine {
    param([string]$Line)

    if (-not $Line) { return $null }
    $parts = $Line -split "`t"
    if ($parts.Count -lt 2) { return $null }

    $status = $parts[0]
    $path = $parts[-1]

    return [pscustomobject]@{
        Status = $status
        Path = $path
        Source = "commit"
    }
}

function Parse-PorcelainLine {
    param([string]$Line)

    if (-not $Line) { return $null }
    if ($Line.Length -lt 4) { return $null }

    $status = $Line.Substring(0, 2).Trim()
    $rawPath = $Line.Substring(3).Trim()

    if ($rawPath -like "* -> *") {
        $rawPath = ($rawPath -split " -> ")[-1]
    }

    return [pscustomobject]@{
        Status = $status
        Path = $rawPath
        Source = "working"
    }
}

function Get-Area {
    param([string]$Path)

    if ($Path -match '^docs/') { return 'docs' }
    if ($Path -match '^skills/') { return 'skills' }
    if ($Path -match '^frontend/') { return 'frontend' }
    if ($Path -match '^conversion-service/') { return 'conversion-service' }
    if ($Path -match '^user-service/') { return 'user-service' }
    if ($Path -match '^api-gateway/') { return 'api-gateway' }
    if ($Path -match '^common/') { return 'common' }
    if ($Path -match '^deploy/' -or $Path -match 'docker' -or $Path -match '\.ya?ml$') { return 'runtime/deploy' }
    return 'root/other'
}

$result = [ordered]@{
    script = "ai-doc-sync.ps1"
    success = $false
    promptFile = $null
    source = if ($OnlyWorkingTree) { "working-tree-only" } else { "since-ref-and-working-tree" }
    changedFiles = @()
    generatedAt = (Get-Date).ToString("s")
}

try {
    $null = & git rev-parse --is-inside-work-tree 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Current directory is not a git repository"
    }

    $items = New-Object System.Collections.Generic.List[object]

    if (-not $OnlyWorkingTree) {
        Write-Info "Collecting committed changes from $SinceRef..HEAD"
        $diff = Invoke-Git -GitArgs @("diff", "--name-status", "$SinceRef..HEAD") -AllowFailure
        if ($diff.exitCode -eq 0) {
            foreach ($line in $diff.output) {
                $parsed = Parse-NameStatusLine -Line $line
                if ($null -ne $parsed) {
                    $items.Add($parsed)
                }
            }
        } else {
            Write-Info "Could not diff against '$SinceRef'; continuing with working tree only"
        }
    }

    Write-Info "Collecting working tree changes"
    $status = Invoke-Git -GitArgs @("status", "--porcelain")
    foreach ($line in $status.output) {
        $parsed = Parse-PorcelainLine -Line $line
        if ($null -ne $parsed) {
            $items.Add($parsed)
        }
    }

    $unique = $items |
        Group-Object Path |
        ForEach-Object { $_.Group | Select-Object -First 1 } |
        Sort-Object Path

    if (-not $unique -or $unique.Count -eq 0) {
        throw "No changes found to prepare documentation sync"
    }

    $result.changedFiles = $unique | ForEach-Object { $_.Path }

    $byArea = $unique |
        Group-Object { Get-Area -Path $_.Path } |
        Sort-Object Name

    $promptPath = Join-Path $PSScriptRoot $PromptFile
    $promptDir = Split-Path -Parent $promptPath
    if (-not (Test-Path $promptDir)) {
        New-Item -ItemType Directory -Path $promptDir -Force | Out-Null
    }

    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("# App-Level Documentation & Upskilling Update Brief")
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
    [void]$sb.AppendLine("Scope: update project documentation from latest code/runtime changes.")
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("## Required output files")
    [void]$sb.AppendLine('- `README.md`')
    [void]$sb.AppendLine('- `CHANGELOG.md`')
    [void]$sb.AppendLine('- `docs/**` (only impacted docs)')
    [void]$sb.AppendLine('- `skills/**` (upskilling + agent protocol updates)')
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("## Source change inventory")
    foreach ($group in $byArea) {
        [void]$sb.AppendLine("")
        [void]$sb.AppendLine("### $($group.Name)")
        foreach ($entry in ($group.Group | Sort-Object Path)) {
            [void]$sb.AppendLine("- [$($entry.Status)] $($entry.Path) ($($entry.Source))")
        }
    }

    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("## Update rules")
    [void]$sb.AppendLine("1. Reflect implemented behavior only; mark roadmap/deferred items clearly.")
    [void]$sb.AppendLine("2. Keep payment/subscription notes explicitly deferred unless implementation changed.")
    [void]$sb.AppendLine('3. Keep Java baseline explicit: `JAVA_HOME=C:\Program Files\Java\jdk-17`.')
    [void]$sb.AppendLine("4. Ensure README, docs, skills, and changelog stay consistent with each other.")
    [void]$sb.AppendLine("5. Preserve existing functionality and avoid unrelated code changes.")

    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("## CHANGELOG entry format")
    [void]$sb.AppendLine("- Add a new top entry with date, summary, and grouped bullets:")
    [void]$sb.AppendLine("  - Added")
    [void]$sb.AppendLine("  - Changed")
    [void]$sb.AppendLine("  - Fixed")
    [void]$sb.AppendLine("  - Docs")

    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("## Validation checklist")
    [void]$sb.AppendLine("- README feature/status sections match current runtime behavior.")
    [void]$sb.AppendLine("- docs/API_REFERENCE.md endpoints match live controllers/routes.")
    [void]$sb.AppendLine("- docs/DEPLOYMENT.md and Docker docs match compose/scripts.")
    [void]$sb.AppendLine("- skills docs include latest agent-safe automation flows.")

    Set-Content -Path $promptPath -Value $sb.ToString() -Encoding UTF8

    $result.promptFile = $promptPath
    Write-Ok "Generated update brief: $promptPath"

    if ($RunAgent) {
        if (-not $AgentCommand) {
            throw "-RunAgent requires -AgentCommand"
        }

        $resolved = if ($AgentCommand -match "\{PROMPT_FILE\}") {
            $AgentCommand.Replace("{PROMPT_FILE}", ('"' + $promptPath + '"'))
        } else {
            "$AgentCommand `"$promptPath`""
        }

        if (-not (Confirm-Action "Execute AI command now?`n$resolved")) {
            throw "AI command canceled"
        }

        Write-Info "Executing AI command"
        & cmd /c $resolved
        if ($LASTEXITCODE -ne 0) {
            throw "AI command failed with exit code $LASTEXITCODE"
        }

        Write-Ok "AI command completed"
        $result.agentCommand = $resolved
    }

    $result.success = $true

    if ($OutputJson) {
        $result | ConvertTo-Json -Depth 8
    }

    exit 0
}
catch {
    $result.error = $_.Exception.Message
    $result.errorType = $_.Exception.GetType().FullName
    $result.errorAt = $_.InvocationInfo.PositionMessage
    $result.stack = $_.ScriptStackTrace
    Write-Fail $result.error
    if ($OutputJson) {
        $result | ConvertTo-Json -Depth 8
    }
    exit 1
}
