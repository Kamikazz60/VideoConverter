param(
    [switch]$SansTests,
    [switch]$SansRelease,
    [string]$ToolsRoot = (Join-Path $env:USERPROFILE 'Documents\Codex\AndroidDev')
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Activate-Android.ps1') -ToolsRoot $ToolsRoot | Out-Null
$project = Split-Path $PSScriptRoot -Parent
Push-Location $project
try {
    $tasks = @(':app:assembleDebug')
    if (-not $SansRelease) { $tasks += ':app:assembleRelease' }
    if (-not $SansTests) { $tasks = @(':app:testDebugUnitTest') + $tasks }
    $ErrorActionPreference = 'Continue'   # Gradle écrit des avertissements sur stderr
    & .\gradlew.bat @tasks --console=plain
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0) { throw "Échec Gradle ($LASTEXITCODE)." }
    $apkDir = Join-Path $project 'apk'
    New-Item -ItemType Directory -Force $apkDir | Out-Null
    $version = (Select-String -Path 'app\build.gradle.kts' -Pattern 'versionName = "([^"]+)"').Matches[0].Groups[1].Value
    $debug = Join-Path $apkDir "VideoConverter-$version-debug.apk"
    Copy-Item 'app\build\outputs\apk\debug\app-debug.apk' $debug -Force
    Write-Host "APK debug   : $debug"
    if (-not $SansRelease) {
        $rel = Join-Path $apkDir "VideoConverter-$version-release.apk"
        Copy-Item 'app\build\outputs\apk\release\app-release.apk' $rel -Force
        Write-Host "APK release : $rel"
    }
} finally { Pop-Location }
