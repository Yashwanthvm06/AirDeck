# TouchDeck Architecture & Technical Deep-Dive

TouchDeck is a real-time, ultra-low-latency wireless controller and input pipeline that bridges an Android device with a Windows PC over local Wi-Fi / LAN.

```mermaid
graph TD
    subgraph Android["Android Smartphone (Client)"]
        UI["Jetpack Compose UI (Trackpad, Keyboard, Gamepad, Remote)"]
        VM["TouchDeckViewModel & App State"]
        Haptics["HapticEngine (Waveform vibration)"]
        Client["TouchDeckClient (WebSocket + HMAC Auth)"]
        
        UI --> VM
        VM --> Haptics
        VM --> Client
    end

    subgraph Transport["Local Wi-Fi Network"]
        WS["JSON Protocol over WebSocket (TCP Port 9876)"]
        Client <==>{{"Bi-directional Low-Latency Stream"}} WS
    end

    subgraph Windows["Windows 10/11 (Companion)"]
        WSServer["WebSocket Server (HttpListener WebSocket)"]
        Security["SecurityManager (PIN, HMAC-SHA256, DPAPI)"]
        Dispatcher["InputDispatcher & InputSimulator (Win32 SendInput)"]
        UI_Win["WPF Modern UI (Fluent Dark Theme, Tray, Pairing Dialog)"]

        WS <==> WSServer
        WSServer --> Security
        Security --> Dispatcher
        WSServer --> UI_Win
    end
```

---

## 1. Network Protocol & Framing

Communication is carried over a persistent WebSocket connection:
- **Framing**: Lightweight JSON envelopes with `type` and `payload`.
- **Ping/Pong**: Keepalive heartbeats every 5 seconds for RTT latency reporting to the millisecond.
- **Message batching**: Direct unbuffered dispatch ensures minimum latency (< 5 ms on typical 5 GHz local networks).
- **Fragment assembly**: WebSocket fragments are reassembled before JSON parsing. Oversized messages (> 4 KB) are rejected.

### Protocol Messages

| Message Type | Direction | Description |
|---|---|---|
| `PAIR_REQ` | Android → PC | Device registration with device name and UUID |
| `PAIR_CHALLENGE` | PC → Android | Empty challenge — 6-digit PIN shown *only on PC screen* |
| `PAIR_CONFIRM` | Android → PC | User-entered PIN for verification |
| `PAIR_RESULT` | PC → Android | Status and persistent session token (256-bit) |
| `AUTH` | Android → PC | HMAC-SHA256 challenge-response (nonce + signed token) |
| `AUTH_RESULT` | PC → Android | Authentication acceptance or revocation |
| `MOUSE_MOVE` | Android → PC | Delta `dx`, `dy` floating-point coordinates |
| `MOUSE_BTN_DOWN/UP` | Android → PC | Button press/release (`left`, `right`, `middle`) |
| `MOUSE_SCROLL` | Android → PC | Wheel delta scroll events |
| `KEY_DOWN/UP` | Android → PC | Virtual keycode press/release |
| `TEXT_INPUT` | Android → PC | Unicode string injection (`KEYEVENTF_UNICODE`) |
| `MEDIA_ACTION` | Android → PC | Play/pause, track navigation, volume adjustments |
| `CTRL_BTN_DOWN/UP` | Android → PC | Virtual gamepad button press/release |
| `CTRL_STICK` | Android → PC | Analog joystick normalized vector (-1.0 to +1.0) |
| `PING` / `PONG` | Both | Latency telemetry |

---

## 2. Windows Input Simulation

The Windows companion uses Win32 `SendInput` (`user32.dll`) to simulate hardware events:
- **Mouse Relative Movements**: Delta increments injected directly into the Windows input queue.
- **Unicode Text Input**: Employs `KEYEVENTF_UNICODE` (`wScan = char`) allowing arbitrary languages, emojis, and symbols without regional keyboard layout mismatch.
- **Gamepad Translation**: Controller inputs are mapped into high-priority Windows keyboard shortcuts, virtual keys, or native key sequences.
- **Media Controls**: Directly targets Windows virtual keys `VK_MEDIA_PLAY_PAUSE`, `VK_VOLUME_UP`, `VK_VOLUME_DOWN`, `VK_VOLUME_MUTE`, `VK_MEDIA_NEXT_TRACK`, and `VK_MEDIA_PREV_TRACK`.

---

## 3. Security Model

### Pairing (First Connection)

1. **Explicit Pairing Required**: Unpaired devices cannot trigger inputs — they can only send `pair_request`.
2. **Cryptographic 6-Digit PIN**: A `RandomNumberGenerator`-derived PIN is displayed *only* on the PC screen. The Android app shows a blank challenge; the user must read the PIN from the PC.
3. **Brute-Force Protection**: Max 5 failed PIN attempts per device → 60-second lockout. Rate-limited to 10 pairing requests/minute globally. Repeated requests from the same device reuse the existing PIN (prevents PIN-reset attacks).
4. **Token Issuance**: On correct PIN, the server issues a 256-bit cryptographically random session token and sends it to the client.

### Authentication (Returning Connections)

1. **Single-Use Nonce**: The server generates a 32-byte random `connectionNonce` for each new WebSocket connection.
2. **HMAC-SHA256 Response**: The client computes `HMAC-SHA256(token, nonce)` and sends it with the nonce.
3. **Server Verification**: The server verifies the client's nonce matches its `connectionNonce` using constant-time comparison (`CryptographicOperations.FixedTimeEquals`), then immediately invalidates the nonce (`connectionNonce = ""`).
4. **Replay Impossible**: Because the nonce is zeroed after first use, replaying a captured authentication exchange is rejected.

### Token Storage

| Platform | Mechanism | Failure Mode |
|---|---|---|
| **Android** | `EncryptedSharedPreferences` (AES-256-GCM, hardware Android Keystore) | Falls back to **in-memory only** — never plaintext |
| **Windows** | DPAPI (`ProtectedData.Protect`) | Treats failed decryption as **corrupted** — never plaintext fallback |

### Connection Limits

- Max **5 authenticated** clients simultaneously (enforced atomically with `Interlocked.Increment`).
- Max **10 unauthenticated** connections with a 15-second handshake timeout and 30-second authentication timeout.
- Per-session input tracking: disconnecting one client releases only *that* client's held keys/buttons.

### Device Revocation

- Companion UI "Forget Device" → immediately disconnects active sessions and erases the stored token.
- Android "Forget Paired PC" → wipes stored credentials and terminates any active WebSocket connection.

---

## 4. Cleartext Transport Notice

TouchDeck communicates over `ws://` (unencrypted WebSocket). This is a deliberate trade-off:
- The application is designed for **local, trusted home/office networks only**.
- The HMAC challenge-response prevents token replay even over unencrypted channels.
- The initial pairing token is transmitted once during pairing (protected by the PIN-based challenge), then never sent in cleartext again.
- **Mitigation**: `wss://` (TLS) support is planned for a future release to protect against passive eavesdropping of input data on shared networks.
