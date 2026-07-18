$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$packageName = "nl.psalmbladmuziek.app"

function Convert-LocalPropertiesPath($value) {
    return $value.Replace("\:", ":").Replace("\\", "\")
}

function Add-ExistingSdkCandidate($candidates, $path) {
    if (-not [string]::IsNullOrWhiteSpace($path)) {
        [void]$candidates.Add($path)
    }
}

function Find-AndroidSdk {
    $candidates = New-Object System.Collections.Generic.List[string]

    Add-ExistingSdkCandidate $candidates $env:ANDROID_HOME
    Add-ExistingSdkCandidate $candidates $env:ANDROID_SDK_ROOT

    $localProperties = Join-Path $root "local.properties"
    if (Test-Path $localProperties) {
        $sdkLine = Get-Content $localProperties | Where-Object { $_ -match "^sdk\.dir=" } | Select-Object -First 1
        if ($sdkLine) {
            Add-ExistingSdkCandidate $candidates (Convert-LocalPropertiesPath ($sdkLine -replace "^sdk\.dir=", ""))
        }
    }

    Add-ExistingSdkCandidate $candidates (Join-Path $env:LOCALAPPDATA "Android\Sdk")

    foreach ($candidate in $candidates | Select-Object -Unique) {
        $adbPath = Join-Path $candidate "platform-tools\adb.exe"
        if (Test-Path $adbPath) {
            return @{ Sdk = $candidate; Adb = $adbPath }
        }
    }

    throw "Android platform-tools/adb.exe niet gevonden. Run eerst de VS Code-taak 'Android: setup minimal SDK'. Verwachte SDK-locatie: $env:LOCALAPPDATA\Android\Sdk"
}

Set-Location $root
$androidSdk = Find-AndroidSdk
$gradle = Join-Path $root "gradlew.bat"

Write-Host "Android SDK: $($androidSdk.Sdk)"
Write-Host "Build debug APK..."
& $gradle ":app:assembleDebug"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Controleer aangesloten devices/emulators..."
$devices = & $androidSdk.Adb devices | Where-Object { $_ -match "\tdevice$" }
if (-not $devices) {
    throw "Geen actieve Android device/emulator gevonden. Start een emulator of sluit een toestel met USB debugging aan."
}

Write-Host "Installeer debug APK..."
& $gradle ":app:installDebug"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Start app..."
& $androidSdk.Adb shell am force-stop $packageName
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $androidSdk.Adb shell monkey -p $packageName -c android.intent.category.LAUNCHER 1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }