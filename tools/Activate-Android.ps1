param([string]$ToolsRoot = (Join-Path $env:USERPROFILE 'Documents\Codex\AndroidDev'))
$ErrorActionPreference = 'Stop'
$jdk = Get-ChildItem (Join-Path $ToolsRoot 'java') -Directory | Sort-Object Name -Descending | Select-Object -First 1
if (-not $jdk) { throw 'JDK introuvable. Précisez -ToolsRoot ou configurez JAVA_HOME et ANDROID_HOME.' }
$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = Join-Path $ToolsRoot 'android-sdk'
$env:ANDROID_USER_HOME = Join-Path $ToolsRoot 'android-user'
$env:GRADLE_USER_HOME = Join-Path $ToolsRoot 'gradle-cache'
$debugKey = Join-Path $ToolsRoot 'keys\pixelshare-debug.keystore'
if (Test-Path -LiteralPath $debugKey) { $env:PIXELSHARE_DEBUG_KEYSTORE = $debugKey }
$javaBin = Join-Path $env:JAVA_HOME 'bin'
$adbBin = Join-Path $env:ANDROID_HOME 'platform-tools'
$sdkBin = Join-Path $env:ANDROID_HOME 'cmdline-tools\latest\bin'
$additions = @($javaBin, $adbBin, $sdkBin) | Where-Object { $_ -notin ($env:PATH -split ';') }
$env:PATH = ($additions + @($env:PATH)) -join ';'
Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
Write-Host 'Environnement activé pour ce terminal. Aucune modification système.'
