@echo off
setlocal

set "ROOT=%~dp0.."
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "PACKAGE=nl.psalmbladmuziek.app"
set "APK=%TEMP%\PsalmenAppBuild\app\outputs\apk\debug\app-debug.apk"

cd /d "%ROOT%"

if not exist "%ADB%" (
  echo adb.exe niet gevonden: %ADB%
  exit /b 1
)

call gradlew.bat :app:assembleDebug --console=plain
if errorlevel 1 exit /b %errorlevel%

"%ADB%" get-state | findstr /C:"device" >nul
if errorlevel 1 (
  echo Geen geautoriseerd Android toestel gevonden.
  exit /b 1
)

if not exist "%APK%" (
  echo APK niet gevonden: %APK%
  exit /b 1
)

"%ADB%" install --no-streaming -r "%APK%"
if errorlevel 1 (
  echo Installeren met -r mislukt; probeer bestaande debug-app te verwijderen.
  "%ADB%" uninstall %PACKAGE%
  "%ADB%" install --no-streaming "%APK%"
  if errorlevel 1 exit /b %errorlevel%
)

"%ADB%" shell am force-stop %PACKAGE%
"%ADB%" shell am start -n %PACKAGE%/com.example.psalmenapp.MainActivity
if errorlevel 1 exit /b %errorlevel%

endlocal