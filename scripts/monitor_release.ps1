[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$Tag,

    [Parameter(Mandatory = $false)]
    [string]$Repo = "theamazingaustin/CalCloner",

    [Parameter(Mandatory = $false)]
    [int]$TimeoutMinutes = 15,

    [Parameter(Mandatory = $false)]
    [int]$PollIntervalSeconds = 15
)

$ErrorActionPreference = "Continue"

if (-not $Tag) {
    $Tag = (git describe --tags --abbrev=0 2>$null)
    if (-not $Tag) {
        Write-Error "No git tag specified and could not detect latest git tag."
        exit 1
    }
}

Write-Host "Monitoring GitHub Actions build for tag: $Tag on repo: $Repo"
$startTime = Get-Date
$timeoutTime = $startTime.AddMinutes($TimeoutMinutes)

$headers = @{
    "User-Agent" = "CalCloner-Release-Monitor"
    "Accept"     = "application/vnd.github.v3+json"
}

# 1. Locate the workflow run for this tag
$run = $null
while ((Get-Date) -lt $timeoutTime) {
    try {
        $runsUrl = "https://api.github.com/repos/$Repo/actions/runs?per_page=10"
        $runsResp = Invoke-RestMethod -Uri $runsUrl -Headers $headers -Method Get
        $run = $runsResp.workflow_runs | Where-Object { $_.head_branch -eq $Tag -or $_.display_title -eq $Tag } | Select-Object -First 1

        if ($run) {
            Write-Host "Found workflow run ID: $($run.id) (Status: $($run.status))"
            break
        }
    } catch {
        Write-Warning "Failed to query workflow runs: $_"
    }
    Start-Sleep -Seconds 5
}

if (-not $run) {
    Write-Error "Could not locate a workflow run for tag '$Tag' within initial search."
    exit 1
}

# 2. Poll until workflow run completes
$runId = $run.id
while ((Get-Date) -lt $timeoutTime) {
    try {
        $runDetailUrl = "https://api.github.com/repos/$Repo/actions/runs/$runId"
        $runDetail = Invoke-RestMethod -Uri $runDetailUrl -Headers $headers -Method Get

        $status = $runDetail.status
        $conclusion = $runDetail.conclusion

        Write-Host "Workflow run $runId status: $status, conclusion: $conclusion"

        if ($status -eq "completed") {
            if ($conclusion -ne "success") {
                Write-Error "Workflow run $runId failed with conclusion: $conclusion. URL: $($runDetail.html_url)"
                exit 1
            }
            Write-Host "Workflow completed successfully! Fetching release assets..."
            break
        }
    } catch {
        Write-Warning "Error checking run detail: $_"
    }

    Start-Sleep -Seconds $PollIntervalSeconds
}

# 3. Poll release endpoint for uploaded APK asset
$apkAsset = $null
$releaseUrl = "https://api.github.com/repos/$Repo/releases/tags/$Tag"
$assetTimeout = (Get-Date).AddMinutes(3)

while ((Get-Date) -lt $assetTimeout) {
    try {
        $release = Invoke-RestMethod -Uri $releaseUrl -Headers $headers -Method Get
        if ($release -and $release.assets -and $release.assets.Count -gt 0) {
            $apkAsset = $release.assets | Where-Object { $_.name -like "*.apk" } | Select-Object -First 1
            if ($apkAsset) {
                break
            }
        }
    } catch {
        # Release might not be published yet
    }
    Start-Sleep -Seconds 5
}

if ($apkAsset) {
    $sizeMb = [math]::Round($apkAsset.size / 1MB, 2)
    Write-Host "`n================ RELEASE APK READY ================"
    Write-Host "TAG: $Tag"
    Write-Host "FILE: $($apkAsset.name)"
    Write-Host "SIZE: ${sizeMb} MB"
    Write-Host "DOWNLOAD_URL: $($apkAsset.browser_download_url)"
    Write-Host "RELEASE_URL: $($release.html_url)"
    Write-Host "====================================================`n"

    $result = [PSCustomObject]@{
        Tag         = $Tag
        FileName    = $apkAsset.name
        SizeMb      = $sizeMb
        DownloadUrl = $apkAsset.browser_download_url
        ReleaseUrl  = $release.html_url
    }
    $result | ConvertTo-Json
    exit 0
} else {
    Write-Error "Release was created but no .apk asset was found for tag '$Tag'."
    exit 1
}
