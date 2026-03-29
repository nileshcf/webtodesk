[CmdletBinding()]
param(
    [ValidateSet("interactive", "status", "branches", "switch", "new-branch", "pull", "push", "force-pull", "force-push", "merge", "commit", "history")]
    [string]$Action = "interactive",
    [string]$Branch,
    [string]$TargetBranch,
    [string]$CommitMessage,
    [int]$HistoryCount = 20,
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

    $answer = Read-Host "$Prompt (y/N)"
    return ($answer -eq "y" -or $answer -eq "Y")
}

function Invoke-GitCommand {
    param(
        [string[]]$Arguments,
        [switch]$AllowFailure
    )

    $output = (& git @Arguments 2>&1)
    $exit = $LASTEXITCODE
    if (-not $AllowFailure -and $exit -ne 0) {
        throw "git $($Arguments -join ' ') failed: $($output -join [Environment]::NewLine)"
    }

    return [ordered]@{
        args = $Arguments
        exitCode = $exit
        output = @($output)
    }
}

function Test-GitRepository {
    $check = Invoke-GitCommand -Arguments @("rev-parse", "--is-inside-work-tree") -AllowFailure
    return ($check.exitCode -eq 0 -and $check.output -contains "true")
}

function Get-CurrentBranch {
    $result = Invoke-GitCommand -Arguments @("rev-parse", "--abbrev-ref", "HEAD")
    return ($result.output | Select-Object -First 1)
}

function Get-TrackingBranch {
    $result = Invoke-GitCommand -Arguments @("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}") -AllowFailure
    if ($result.exitCode -ne 0) {
        return $null
    }
    return ($result.output | Select-Object -First 1)
}

function Get-GitBranches {
    param([switch]$IncludeRemote)

    $local = Invoke-GitCommand -Arguments @("branch", "--format=%(refname:short)")
    $branches = @($local.output)

    if ($IncludeRemote) {
        $remote = Invoke-GitCommand -Arguments @("branch", "-r", "--format=%(refname:short)")
        $branches += @($remote.output | Where-Object { $_ -and $_ -notlike "*->*" })
    }

    return $branches | Where-Object { $_ } | Sort-Object -Unique
}

function Get-GitStatusLines {
    $status = Invoke-GitCommand -Arguments @("status", "--porcelain")
    return @($status.output)
}

function Get-GitSummary {
    $current = Get-CurrentBranch
    $tracking = Get-TrackingBranch
    $status = Get-GitStatusLines

    $ahead = 0
    $behind = 0

    if ($tracking) {
        $ab = Invoke-GitCommand -Arguments @("rev-list", "--count", "--left-right", "@{u}...HEAD") -AllowFailure
        if ($ab.exitCode -eq 0 -and $ab.output.Count -gt 0) {
            $parts = ($ab.output[0] -split '\s+')
            if ($parts.Count -ge 2) {
                $behind = [int]$parts[0]
                $ahead = [int]$parts[1]
            }
        }
    }

    return [ordered]@{
        branch = $current
        trackingBranch = $tracking
        ahead = $ahead
        behind = $behind
        clean = ($status.Count -eq 0)
        changes = $status
    }
}

function Set-GitBranch {
    param([string]$Name)
    if (-not $Name) { throw "Branch name is required" }
    Invoke-GitCommand -Arguments @("checkout", $Name) | Out-Null
    Write-Ok "Switched to branch '$Name'"
}

function New-GitBranch {
    param([string]$Name)
    if (-not $Name) { throw "Branch name is required" }
    Invoke-GitCommand -Arguments @("checkout", "-b", $Name) | Out-Null
    Write-Ok "Created and switched to '$Name'"
}

function Invoke-GitPull {
    Invoke-GitCommand -Arguments @("pull") | Out-Null
    Write-Ok "Pull completed"
}

function Invoke-GitForcePull {
    if (-not (Confirm-Action "Force pull will reset local changes. Continue?")) {
        throw "Force pull canceled"
    }
    Invoke-GitCommand -Arguments @("fetch", "--all") | Out-Null
    Invoke-GitCommand -Arguments @("reset", "--hard", "@{u}") | Out-Null
    Write-Ok "Force pull completed"
}

function Invoke-GitPush {
    Invoke-GitCommand -Arguments @("push") | Out-Null
    Write-Ok "Push completed"
}

function Invoke-GitForcePush {
    if (-not (Confirm-Action "Force push overwrites remote history. Continue?")) {
        throw "Force push canceled"
    }
    Invoke-GitCommand -Arguments @("push", "--force-with-lease") | Out-Null
    Write-Ok "Force push completed"
}

function Invoke-GitMerge {
    param([string]$Name)
    if (-not $Name) { throw "Target branch is required for merge" }
    Invoke-GitCommand -Arguments @("merge", $Name) | Out-Null
    Write-Ok "Merged '$Name' into current branch"
}

function Invoke-GitCommit {
    param([string]$Message)
    if (-not $Message) { throw "Commit message is required" }
    Invoke-GitCommand -Arguments @("add", ".") | Out-Null
    Invoke-GitCommand -Arguments @("commit", "-m", $Message) | Out-Null
    Write-Ok "Commit completed"
}

function Get-GitHistory {
    param([int]$Count = 20)
    $history = Invoke-GitCommand -Arguments @("log", "--oneline", "-$Count")
    return @($history.output)
}

function Read-MenuChoice {
    param([string[]]$Options, [string]$Title)

    Write-Host "" -ForegroundColor White
    Write-Host $Title -ForegroundColor Cyan
    for ($i = 0; $i -lt $Options.Count; $i++) {
        Write-Host "[$($i + 1)] $($Options[$i])" -ForegroundColor White
    }
    Write-Host "[0] Exit" -ForegroundColor Gray

    while ($true) {
        $raw = Read-Host "Choice"
        if ($raw -match '^\d+$') {
            $value = [int]$raw
            if ($value -ge 0 -and $value -le $Options.Count) {
                return $value
            }
        }
        Write-Info "Invalid choice"
    }
}

$result = [ordered]@{
    script = "git-operations.ps1"
    action = $Action
    success = $false
    details = $null
}

try {
    if (-not (Test-GitRepository)) {
        throw "Current directory is not a git repository"
    }

    if ($Action -eq "interactive") {
        do {
            $summary = Get-GitSummary
            if (-not $OutputJson) {
                Write-Host "" 
                Write-Host "Branch: $($summary.branch)" -ForegroundColor Cyan
                if ($summary.trackingBranch) {
                    Write-Host "Tracking: $($summary.trackingBranch) | Ahead: $($summary.ahead) | Behind: $($summary.behind)" -ForegroundColor White
                }
                if ($summary.clean) {
                    Write-Host "Working tree: clean" -ForegroundColor Green
                } else {
                    Write-Host "Working tree: dirty" -ForegroundColor Yellow
                }
            }

            $menu = @(
                "Status",
                "List Branches",
                "Switch Branch",
                "Create Branch",
                "Pull",
                "Force Pull",
                "Push",
                "Force Push",
                "Merge Branch",
                "Commit",
                "History"
            )

            $choice = Read-MenuChoice -Options $menu -Title "Git Operations"
            switch ($choice) {
                0 { break }
                1 { $result.details = Get-GitSummary }
                2 { $result.details = @{ branches = Get-GitBranches -IncludeRemote } }
                3 {
                    $branches = Get-GitBranches
                    $pick = Read-MenuChoice -Options $branches -Title "Switch to branch"
                    if ($pick -gt 0) { Set-GitBranch -Name $branches[$pick - 1] }
                }
                4 {
                    $name = Read-Host "New branch name"
                    New-GitBranch -Name $name
                }
                5 { Invoke-GitPull }
                6 { Invoke-GitForcePull }
                7 { Invoke-GitPush }
                8 { Invoke-GitForcePush }
                9 {
                    $branches = Get-GitBranches
                    $pick = Read-MenuChoice -Options $branches -Title "Merge branch into current"
                    if ($pick -gt 0) { Invoke-GitMerge -Name $branches[$pick - 1] }
                }
                10 {
                    $msg = Read-Host "Commit message"
                    Invoke-GitCommit -Message $msg
                }
                11 { $result.details = @{ history = Get-GitHistory -Count $HistoryCount } }
            }
        } while ($true)

        $result.success = $true
    } else {
        switch ($Action) {
            "status" { $result.details = Get-GitSummary }
            "branches" { $result.details = @{ branches = Get-GitBranches -IncludeRemote } }
            "switch" { Set-GitBranch -Name $Branch; $result.details = @{ branch = $Branch } }
            "new-branch" { New-GitBranch -Name $Branch; $result.details = @{ branch = $Branch } }
            "pull" { Invoke-GitPull }
            "force-pull" { Invoke-GitForcePull }
            "push" { Invoke-GitPush }
            "force-push" { Invoke-GitForcePush }
            "merge" {
                $branchToMerge = if ($TargetBranch) { $TargetBranch } else { $Branch }
                Invoke-GitMerge -Name $branchToMerge
                $result.details = @{ merged = $branchToMerge }
            }
            "commit" {
                Invoke-GitCommit -Message $CommitMessage
                $result.details = @{ commitMessage = $CommitMessage }
            }
            "history" { $result.details = @{ history = Get-GitHistory -Count $HistoryCount } }
        }

        $result.success = $true
    }

    if ($OutputJson) {
        $result | ConvertTo-Json -Depth 8
    }
    exit 0
}
catch {
    $result.error = $_.Exception.Message
    Write-Fail $result.error
    if ($OutputJson) {
        $result | ConvertTo-Json -Depth 8
    }
    exit 1
}
