$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$defaultSdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"

function Convert-LocalPropertiesPath($value) {
    return $value.Replace("\:", ":").Replace("\\", "\")
}

function Get-ConfiguredSdkRoot {
    foreach ($environmentPath in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if (-not [string]::IsNullOrWhiteSpace($environmentPath)) {
            return $environmentPath
        }
    }

    $localProperties = Join-Path $root "local.properties"
    if (Test-Path $localProperties) {
        $sdkLine = Get-Content $localProperties | Where-Object { $_ -match "^sdk\.dir=" } | Select-Object -First 1
        if ($sdkLine) {
            return Convert-LocalPropertiesPath ($sdkLine -replace "^sdk\.dir=", "")
        }
    }

    return $defaultSdkRoot
}

function Find-SdkManager($sdkRoot) {
    $candidates = @(
        (Join-Path $sdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"),
        (Join-Path $sdkRoot "cmdline-tools\bin\sdkmanager.bat")
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    throw "sdkmanager.bat niet gevonden. Verwacht: $sdkRoot\cmdline-tools\bin\sdkmanager.bat of $sdkRoot\cmdline-tools\latest\bin\sdkmanager.bat"
}

$sdkRoot = Get-ConfiguredSdkRoot
$sdkManager = Find-SdkManager $sdkRoot

Write-Host "Android SDK root: $sdkRoot"
Write-Host "sdkmanager: $sdkManager"
Write-Host "Accepteer Android SDK-licenties..."

1..20 | ForEach-Object { "y" } | & $sdkManager "--sdk_root=$sdkRoot" --licenses
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Installeer minimale pakketten voor deze app..."

& $sdkManager "--sdk_root=$sdkRoot" "platform-tools" "platforms;android-36" "build-tools;36.0.0"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Android command-line setup gereed."