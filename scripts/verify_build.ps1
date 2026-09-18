[CmdletBinding()]
param(
    [switch]$Assemble
)

$ErrorActionPreference = "Continue"

# 1. Resolve JAVA_HOME if not already set
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $potentialJavaHomes = @(
        "C:\Program Files\Android\Android Studio\jbr",
        "C:\Program Files\Java\jdk-17",
        "C:\Program Files\Eclipse Adoptium\jdk-17*"
    )
    foreach ($path in $potentialJavaHomes) {
        $resolved = Resolve-Path $path -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($resolved -and (Test-Path "$($resolved.Path)\bin\java.exe")) {
            $env:JAVA_HOME = $resolved.Path
            break
        }
    }
}

if (-not $env:JAVA_HOME) {
    Write-Error "Could not locate a valid JAVA_HOME. Please ensure Android Studio JBR or JDK 17 is installed."
    exit 1
}

$gradleCmd = "testDebugUnitTest"
if ($Assemble) {
    $gradleCmd = "assembleDebug"
}

$start = Get-Date
$rawOutput = & .\gradlew $gradleCmd 2>&1
$exitCode = $LASTEXITCODE
$elapsed = [math]::Round(((Get-Date) - $start).TotalSeconds, 1)

if ($exitCode -eq 0) {
    Write-Host "`n[SUCCESS] BUILD AND TESTS PASSED in ${elapsed}s`n" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`n[FAILED] BUILD FAILED in ${elapsed}s`n" -ForegroundColor Red
    
    # Filter output to ONLY extract compile errors and test failures
    $errorLines = $rawOutput | Where-Object {
        $_ -match '^(e: file:///.*)' -or
        $_ -match '(Compilation error.*)' -or
        $_ -match '(FAILURE:.*)' -or
        $_ -match '(.*FAILED.*)' -or
        $_ -match '(\s+at\s+com\.stripedlens\..*)'
    }

    if ($errorLines) {
        $errorLines | ForEach-Object { Write-Host $_ -ForegroundColor Yellow }
    } else {
        # Fallback: print last 20 lines if no specific pattern matched
        $rawOutput | Select-Object -Last 20 | ForEach-Object { Write-Host $_ }
    }

    exit $exitCode
}
