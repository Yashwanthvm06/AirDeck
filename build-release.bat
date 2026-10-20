@echo off
setlocal enabledelayedexpansion

echo =======================================================
echo          AirDesk Unified Release Build Pipeline
echo =======================================================
echo.

set ROOT_DIR=%~dp0
cd /d "%ROOT_DIR%"

if not exist "release-builds" mkdir "release-builds"

echo [*] Step 1/3: Compiling Android Signed Release APK...
cd /d "%ROOT_DIR%android-app"
call gradlew.bat assembleRelease
if %ERRORLEVEL% neq 0 (
    echo [!] ERROR: Android release build failed.
    exit /b %ERRORLEVEL%
)

if exist "%ROOT_DIR%android-app\app\build\outputs\apk\release\app-release.apk" (
    copy /Y "%ROOT_DIR%android-app\app\build\outputs\apk\release\app-release.apk" "%ROOT_DIR%release-builds\AirDesk-v1.0.1-release-signed.apk" >nul
    echo [*] Android APK successfully built and copied to release-builds\
) else (
    echo [!] ERROR: app-release.apk not found.
    exit /b 1
)

echo.
echo [*] Step 2/3: Publishing Windows Companion Release...
cd /d "%ROOT_DIR%windows-companion"
dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o "%ROOT_DIR%release-builds\windows"
if %ERRORLEVEL% neq 0 (
    echo [!] ERROR: Windows companion publish failed.
    exit /b %ERRORLEVEL%
)

echo.
echo [*] Step 3/3: Packaging Release Zip and Computing SHA256 Checksums...
cd /d "%ROOT_DIR%"
python -c "
import zipfile, os, hashlib

zip_path = 'release-builds/AirDesk-v1.0.1-windows-x64.zip'
win_dir = 'release-builds/windows'

with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk(win_dir):
        for f in files:
            full_path = os.path.join(root, f)
            arcname = os.path.relpath(full_path, win_dir)
            z.write(full_path, arcname)

checksum_lines = []
for fname in sorted(os.listdir('release-builds')):
    if fname.endswith(('.apk', '.zip')):
        fpath = os.path.join('release-builds', fname)
        h = hashlib.sha256()
        with open(fpath, 'rb') as f:
            while chunk := f.read(65536):
                h.update(chunk)
        checksum_lines.append(f'{h.hexdigest()}  {fname}')

with open('release-builds/SHA256SUMS.txt', 'w') as out:
    out.write('\n'.join(checksum_lines) + '\n')
"

echo.
echo =======================================================
echo          BUILD COMPLETED SUCCESSFULLY!
echo =======================================================
echo Artifacts ready in: release-builds\
echo.
type "%ROOT_DIR%release-builds\SHA256SUMS.txt"
echo.
pause
