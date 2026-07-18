@echo off
setlocal

set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "PACKAGE=nl.psalmbladmuziek.app"

if not exist "%ADB%" (
  echo adb.exe niet gevonden: %ADB%
  exit /b 1
)

"%ADB%" shell am force-stop %PACKAGE%
"%ADB%" shell am start -n %PACKAGE%/com.example.psalmenapp.MainActivity
if errorlevel 1 exit /b %errorlevel%

endlocal