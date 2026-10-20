@echo off
setlocal
set ADB="C:\Users\Yashw\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if not exist %ADB% (
    set ADB=adb
)

echo ==============================================
echo        AirDesk APK Installer v1.0
echo ==============================================
echo.
echo Checking ADB connection...
%ADB% devices
echo.

REM Always prefer the freshly-built release APK, then debug APK from build output.
REM Never prefer a bundled stale APK over a freshly compiled one.
set APK_FILE=
if exist "%~dp0release-builds\AirDesk-v1.0.1-release-signed.apk" (
    set APK_FILE="%~dp0release-builds\AirDesk-v1.0.1-release-signed.apk"
    echo [*] Using latest v1.0.1 signed RELEASE build APK.
) else if exist "%~dp0release-builds\AirDesk-v1.0.0-release-signed.apk" (
    set APK_FILE="%~dp0release-builds\AirDesk-v1.0.0-release-signed.apk"
    echo [*] Using v1.0.0 signed RELEASE build APK.
) else if exist "%~dp0android-app\app\build\outputs\apk\release\app-release.apk" (
    set APK_FILE="%~dp0android-app\app\build\outputs\apk\release\app-release.apk"
    echo [*] Using RELEASE build APK.
) else if exist "%~dp0release-builds\AirDesk-v1.0.0-debug.apk" (
    set APK_FILE="%~dp0release-builds\AirDesk-v1.0.0-debug.apk"
    echo [*] Using release-builds AirDesk-v1.0.0-debug.apk.
) else if exist "%~dp0android-app\app\build\outputs\apk\debug\app-debug.apk" (
    set APK_FILE="%~dp0android-app\app\build\outputs\apk\debug\app-debug.apk"
    echo [*] Using freshly compiled DEBUG build APK.
) else if exist "%~dp0release-builds\TouchDeck-v1.0.0-release-signed.apk" (
    set APK_FILE="%~dp0release-builds\TouchDeck-v1.0.0-release-signed.apk"
    echo [*] Using legacy signed RELEASE build APK.
) else (
    echo [!] ERROR: No APK found! Build the app first:
    echo     cd android-app ^&^& gradlew assembleRelease
    goto :end
)

echo Installing latest AirDesk APK onto your Android device...
echo APK: %APK_FILE%
%ADB% install -r %APK_FILE%
if %ERRORLEVEL% equ 0 (
    echo.
    echo [SUCCESS] AirDesk installed successfully on your phone!
    echo You can now open AirDesk from your app drawer.
    echo.
    echo ============================================
    echo   NEXT STEP: Install the PC Companion App
    echo ============================================
    echo.
    echo To use AirDesk, you also need the Windows companion
    echo app running on your PC or laptop.
    echo.
    echo Download the AirDesk Windows Companion from:
    echo   https://github.com/Yashwanthvm06/AirDesk/releases/latest
    echo.
    echo   (Open the above URL in your browser to download)
    echo.
) else (
    echo.
    echo [!] Could not install to device.
    echo Troubleshooting tips:
    echo  1. Connect your phone using a USB cable.
    echo  2. Enable "Developer Options" and turn ON "USB Debugging".
    echo  3. If your phone asks "Allow USB debugging?", tap "Allow".
    echo  4. If using Wireless Debugging, run: adb connect ^<phone-ip^>:^<port^>
)
:end
echo.
pause
