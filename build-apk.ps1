# Rebuilds the debug APK and copies it to the repo root as DailyTracker.apk.
# All toolchain lives under C:\alaska\tools (installed locally, not system-wide).
$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "C:\alaska\tools\jdk\jdk-17.0.13+11"
$env:ANDROID_HOME = "C:\alaska\tools\android-sdk"
$env:ANDROID_SDK_ROOT = "C:\alaska\tools\android-sdk"
Set-Location $PSScriptRoot
& ".\gradlew.bat" :app:assembleDebug
Copy-Item "app\build\outputs\apk\debug\app-debug.apk" "DailyTracker.apk" -Force
Write-Host "`nDone -> $PSScriptRoot\DailyTracker.apk"
