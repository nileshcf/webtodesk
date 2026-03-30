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

# Import common utilities
$utilitiesPath = Join-Path $PSScriptRoot ".windsurf/scripts/Common-Utilities.ps1"
if (Test-Path $utilitiesPath) {
    . $utilitiesPath
    $script:OutputJson = $OutputJson
} else {
    throw "Required utilities module not found at $utilitiesPath"
}

$result = New-ScriptResult -ScriptName "ai-doc-sync.ps1"
$result.promptFile = $null
$result.source = if ($OnlyWorkingTree) { "working-tree-only" } else { "since-ref-and-working-tree" }
$result.changedFiles = @()
$result.generatedAt = (Get-Date).ToString("s")

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

function Get-NameStatusLine {
    param([string]$Line)

    if ($Line -notmatch '^(\w)\s+(.+)$') { return $null }
    $status = $Matches[1]
    $path = $Matches[2]
    
    $statusMap = @{
        'A' = 'Added'
        'C' = 'Copied'
        'D' = 'Deleted'
        'M' = 'Modified'
        'R' = 'Renamed'
        'T' = 'Type changed'
        'U' = 'Unmerged'
        'X' = 'Unknown'
    }
    
    return [ordered]@{
        Status = $statusMap[$status]
        Path = $path
        Source = 'committed'
    }
}

function Get-PorcelainLine {
    param([string]$Line)

    if ($Line -notmatch '^(..)(\s+)(.+)$') { return $null }
    $index = $Matches[1]
    $work = $Matches[1][1]
    $path = $Matches[3]
    
    $statusMap = @{
        'A' = 'Added'
        'C' = 'Copied'
        'D' = 'Deleted'
        'M' = 'Modified'
        'R' = 'Renamed'
        'T' = 'Type changed'
        'U' = 'Unmerged'
        '?' = 'Untracked'
        '!' = 'Ignored'
    }
    
    $status = if ($work -ne ' ') { $statusMap[$work] } else { $statusMap[$index] }
    
    return if ($status) {
        [ordered]@{
            Status = $status
            Path = $path
            Source = 'working-tree'
        }
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

Invoke-WithErrorHandling -ScriptBlock {
    Write-Step "Verifying Git repository" -Step 1 -Total 5
    
    if (-not (Test-GitRepository)) {
        throw "Current directory is not a git repository"
    }
    Write-Success "Git repository verified"
    
    # Collect changes
    Write-Step "Collecting changes" -Step 2 -Total 5
    
    $items = New-Object System.Collections.Generic.List[object]
    
    if (-not $OnlyWorkingTree) {
        Write-Info "Checking committed changes from $SinceRef..HEAD"
        $diff = Invoke-Git -GitArgs @("diff", "--name-status", "$SinceRef..HEAD") -AllowFailure
        if ($diff.exitCode -eq 0) {
            foreach ($line in $diff.output) {
                $parsed = Get-NameStatusLine -Line $line
                if ($null -ne $parsed) {
                    $items.Add($parsed)
                }
            }
            Write-Info "Found $($diff.output.Count) committed changes"
        } else {
            Write-Warning "Could not diff against '$SinceRef'; continuing with working tree only"
        }
    }
    
    Write-Info "Checking working tree changes"
    $status = Invoke-Git -GitArgs @("status", "--porcelain")
    foreach ($line in $status.output) {
        $parsed = Get-PorcelainLine -Line $line
        if ($null -ne $parsed) {
            $items.Add($parsed)
        }
    }
    Write-Info "Found $($status.output.Count) working tree changes"
    
    # Process unique changes
    Write-Step "Processing changes" -Step 3 -Total 5
    
    $unique = $items |
        Group-Object Path |
        ForEach-Object { $_.Group | Select-Object -First 1 } |
        Sort-Object Path
    
    if (-not $unique -or $unique.Count -eq 0) {
        throw "No changes found to prepare documentation sync"
    }
    
    $result.changedFiles = $unique | ForEach-Object { $_.Path }
    Write-Success "Processing $($unique.Count) unique changes"
    
    # Group by area
    $byArea = $unique |
        Group-Object { Get-Area -Path $_.Path } |
        Sort-Object Name
    
    Write-Info "Changes grouped by area: $($byArea.Count) areas affected"
    
    # Generate prompt
    Write-Step "Generating update brief" -Step 4 -Total 5
    
    $promptPath = Resolve-ScriptPath -RelativePath $PromptFile
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
    Write-Success "Generated update brief: $promptPath"
    
    # Run AI agent if requested
    if ($RunAgent) {
        Write-Step "Executing AI agent" -Step 5 -Total 5
        
        if (-not $AgentCommand) {
            throw "-RunAgent requires -AgentCommand"
        }
        
        $resolved = if ($AgentCommand -match "\{PROMPT_FILE\}") {
            $AgentCommand.Replace("{PROMPT_FILE}", ('"' + $promptPath + '"'))
        } else {
            "$AgentCommand `"$promptPath`""
        }
        
        $msg = "Execute AI command now?`n$resolved"
        if (-not (Test-Confirmation -Prompt $msg -Force:$Force -NonInteractive:$NonInteractive)) {
            throw "AI command canceled"
        }
        
        Write-Info "Executing AI command..."
        $agentTime = Measure-Command {
            & cmd /c $resolved
            if ($LASTEXITCODE -ne 0) {
                throw "AI command failed with exit code $LASTEXITCODE"
            }
        }
        
        Write-Success "AI command completed in $($agentTime.ToString('mm\:ss'))"
        $result.agentCommand = $resolved
    } else {
        Write-Step "Complete" -Step 5 -Total 5
        Write-Info "Ready to run AI agent manually with generated brief"
    }
    
    # Show summary
    if (-not $OutputJson) {
        Write-Host ""
        Write-Host "Summary:" -ForegroundColor Cyan
        Write-Host "  Changes found: $($unique.Count) files" -ForegroundColor White
        Write-Host "  Areas affected: $($byArea.Count)" -ForegroundColor White
        Write-Host "  Brief saved:  $promptPath" -ForegroundColor White
        if ($RunAgent) {
            Write-Host "  Agent executed: Yes" -ForegroundColor White
        }
        Write-Host ""
        Write-Host "Next steps:" -ForegroundColor Cyan
        Write-Host "  1. Review the generated brief" -ForegroundColor Gray
        if (-not $RunAgent) {
            Write-Host "  2. Run your AI agent with: -RunAgent -AgentCommand '<your-command>'" -ForegroundColor Gray
        }
        Write-Host "  3. Commit the updated documentation" -ForegroundColor Gray
        Write-Host ""
    }
    
} -Result $Result -ErrorMessage "Documentation sync failed" -OutputJson:$OutputJson

Export-ScriptResult -Result $Result -OutputJson:$OutputJson
exit 0
