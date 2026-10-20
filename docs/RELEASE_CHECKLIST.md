# TouchDeck Release Checklist

Use this checklist before tagging a GitHub release.

---

## 1. Security Review

- [ ] **No secrets in repo**: Verify `release-keystore.jks`, `keystore.properties`, and any `.jks`/`.keystore` files are **not** tracked by Git.
  ```powershell
  git ls-files -- '*.jks' '*.keystore' 'keystore.properties'
  # Expected: no output
  ```
- [ ] **No hardcoded passwords**: Search for any hardcoded signing passwords in `build.gradle.kts`.
  ```powershell
  Select-String -Path android-app/app/build.gradle.kts -Pattern "touchdeck123|password"
  # Expected: no matches
  ```
- [ ] **HMAC nonce is single-use**: Confirm `connectionNonce` is cleared (`= ""`) immediately after verification in `WebSocketServer.cs`.
- [ ] **No plaintext token fallback**: Confirm `TouchDeckViewModel.kt` uses in-memory-only `SecurePrefs` when the hardware Keystore fails (no `SharedPreferences` fallback).
- [ ] **DPAPI-only on Windows**: Confirm `SecurityManager.cs` does not fall back to plaintext when DPAPI decryption fails.

---

## 2. Build Verification

### Android Client

- [ ] **SDK targets correct**: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26` in `app/build.gradle.kts`.
- [ ] **Debug build compiles**:
  ```powershell
  cd android-app && ./gradlew assembleDebug
  ```
- [ ] **Release build compiles** (requires keystore):
  ```powershell
  cd android-app && ./gradlew assembleRelease
  ```
- [ ] **APK installs and runs** on a physical device or emulator:
  ```powershell
  adb install -r app/build/outputs/apk/release/app-release.apk
  ```

### Windows Companion

- [ ] **Debug run**:
  ```powershell
  cd windows-companion && dotnet run
  ```
- [ ] **Release publish**:
  ```powershell
  cd windows-companion && dotnet publish -c Release -r win-x64 --self-contained true
  ```
- [ ] **Published binary runs** on a clean Windows 10/11 machine (no .NET SDK installed).

---

## 3. Functional Tests

- [ ] **Python test suite passes**:
  ```powershell
  cd tests && python test_companion_client.py 127.0.0.1 9876
  ```
- [ ] **Pairing flow**: Pair a new device, verify 6-digit PIN shown on PC, enter on phone, connection established.
- [ ] **Reconnect**: Close and reopen the Android app — it should reconnect automatically.
- [ ] **Forget PC**: Tap "Forget Paired PC" in settings → active session drops immediately, stored token is erased.
- [ ] **Forget Device (PC side)**: Click "Forget" on the companion → device is disconnected and token is revoked.
- [ ] **Input modes**: Verify trackpad, keyboard, gamepad, and media remote all function correctly.
- [ ] **Multi-client**: Connect 2+ devices simultaneously; verify independent input tracking.

---

## 4. Documentation

- [ ] **README.md** is up to date with current features, setup, security model, and known limitations.
- [ ] **PROTOCOL.md** reflects the current wire protocol (especially HMAC auth fields).
- [ ] **ARCHITECTURE.md** matches the current implementation.
- [ ] **PRIVACY_POLICY.md** accurately describes permissions and data handling.
- [ ] **CONTRIBUTING.md** exists with contributor guidelines.
- [ ] **LICENSE** file exists.

---

## 5. Release Artifacts

Prepare the following for the GitHub release:

| Artifact | Description |
|---|---|
| `TouchDeck-v{VERSION}.apk` | Signed release APK for sideloading |
| `TouchDeck-Companion-v{VERSION}-win-x64.zip` | Self-contained Windows companion (no .NET required) |
| Source code | Automatically attached by GitHub |

### Packaging the Windows Companion

```powershell
cd windows-companion
dotnet publish -c Release -r win-x64 --self-contained true -o ../release-builds/windows

# Zip the output
cd ../release-builds
Compress-Archive -Path windows/* -DestinationPath "TouchDeck-Companion-v1.0.0-win-x64.zip"
```

### Naming the APK

```powershell
Copy-Item android-app/app/build/outputs/apk/release/app-release.apk `
  release-builds/TouchDeck-v1.0.0.apk
```

---

## 6. Git & Tagging

- [ ] **All changes committed** (no uncommitted work).
- [ ] **`.gitignore` verified**: keystores, build output, and IDE files are excluded.
- [ ] **Tag the release**:
  ```bash
  git tag -a v1.0.0 -m "TouchDeck v1.0.0 — initial public release"
  git push origin v1.0.0
  ```
- [ ] **Create GitHub Release**: Go to **Releases → Draft a new release**, select the tag, write release notes, and attach the APK + Windows zip.

---

## 7. Post-Release

- [ ] **Verify download links work** on the GitHub release page.
- [ ] **Test the downloaded APK** on a fresh device.
- [ ] **Test the downloaded Windows zip** on a clean machine.
- [ ] **Update any external links** (website, social media) to point to the release.
