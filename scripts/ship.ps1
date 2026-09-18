[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Message,

    [Parameter(Mandatory = $false)]
    [string]$Tag,

    [switch]$NoMonitor
)

$ErrorActionPreference = "Stop"

# 1. Verify working directory has changes
$status = git status --porcelain
if (-not $status) {
    Write-Host "Nothing to commit, working tree clean."
    exit 0
}

# 2. Auto-generate next release tag if not provided
if (-not $Tag) {
    $currentDate = (Get-Date).ToString("M.d") # e.g. 9.18
    # Read app build version or base version from BuildConfig/build.gradle
    $appBuildGradle = Get-Content "app/build.gradle.kts" -Raw
    $versionNameMatch = [regex]::Match($appBuildGradle, 'versionName\s*=\s*"([^"]+)"')
    $appVersion = if ($versionNameMatch.Success) { $versionNameMatch.Groups[1].Value } else { "2.2" }

    # Find highest build number for today
    $existingTags = git tag --list "v$appVersion-$currentDate-*"
    $maxBuildNum = 0
    foreach ($t in $existingTags) {
        if ($t -match "v$appVersion-$currentDate-(\d+)") {
            $num = [int]$matches[1]
            if ($num -gt $maxBuildNum) {
                $maxBuildNum = $num
            }
        }
    }
    $nextBuildNum = $maxBuildNum + 1
    $Tag = "v$appVersion-$currentDate-$nextBuildNum"
}

Write-Host "================ SHIPPING RELEASE ================" -ForegroundColor Cyan
Write-Host "TAG:     $Tag"
Write-Host "MESSAGE: $Message"
Write-Host "=================================================="

# 3. Stage, commit, tag, and push
git add -A
git commit -m "$Message"
git tag $Tag
git push origin main --tags

Write-Host "`n[SUCCESS] Pushed commit and tag '$Tag' to GitHub successfully!" -ForegroundColor Green

# 4. Monitor GitHub Actions build & fetch APK
if (-not $NoMonitor) {
    Write-Host "`nStarting release build monitor..." -ForegroundColor Cyan
    & "$PSScriptRoot\monitor_release.ps1" -Tag $Tag
}
