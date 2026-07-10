param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs = @("compileJava")
)

$ErrorActionPreference = "Stop"

$RequiredGradleVersion = "8.14.3"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$ProjectGradleUserHome = Join-Path $RepoRoot ".gradle"

function Get-GradleVersion([string] $GradleExe) {
    $versionOutput = & $GradleExe --version 2>$null
    if ($LASTEXITCODE -ne 0) {
        return $null
    }

    foreach ($line in $versionOutput) {
        if ($line -match "^Gradle\s+(.+)$") {
            return $Matches[1].Trim()
        }
    }

    return $null
}

function Resolve-GradleExe {
    if ($env:CCL_GRADLE_HOME) {
        $candidate = Join-Path $env:CCL_GRADLE_HOME "bin\gradle.bat"
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }

        throw "CCL_GRADLE_HOME is set, but '$candidate' does not exist."
    }

    $cachedRoot = Join-Path $env:USERPROFILE ".gradle\wrapper\dists\gradle-$RequiredGradleVersion-bin"
    if (Test-Path -LiteralPath $cachedRoot) {
        $cachedGradle = Get-ChildItem -LiteralPath $cachedRoot -Recurse -Filter "gradle.bat" |
            Where-Object { $_.FullName -like "*\gradle-$RequiredGradleVersion\bin\gradle.bat" } |
            Select-Object -First 1

        if ($cachedGradle) {
            return $cachedGradle.FullName
        }
    }

    $pathGradle = Get-Command "gradle.bat" -ErrorAction SilentlyContinue
    if ($pathGradle) {
        $pathVersion = Get-GradleVersion $pathGradle.Source
        if ($pathVersion -eq $RequiredGradleVersion) {
            return $pathGradle.Source
        }
    }

    throw @"
Gradle $RequiredGradleVersion was not found without using the Gradle wrapper.

Codex sandbox validation must not run .\gradlew.bat because that can attempt a wrapper distribution download.
Install Gradle $RequiredGradleVersion locally, set CCL_GRADLE_HOME to that installation, or pre-seed:
  $cachedRoot
"@
}

$gradleExe = Resolve-GradleExe
$gradleVersion = Get-GradleVersion $gradleExe
if ($gradleVersion -ne $RequiredGradleVersion) {
    throw "Resolved '$gradleExe', but it is Gradle $gradleVersion. Expected Gradle $RequiredGradleVersion."
}

Write-Host "Using Gradle $gradleVersion at $gradleExe"
Write-Host "Using project Gradle user home at $ProjectGradleUserHome"

Push-Location $RepoRoot
try {
    & $gradleExe --offline --gradle-user-home $ProjectGradleUserHome @GradleArgs
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
