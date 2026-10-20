# TouchDeck Communication Protocol v1.0

## Overview
TouchDeck uses a WebSocket-based protocol for real-time communication between the Android controller and Windows companion. All messages are JSON-encoded with a consistent schema.

## Connection Flow
1. **Discovery**: Android app discovers PC via mDNS or manual IP entry
2. **WebSocket Connect**: Client connects to `ws://<ip>:<port>` (root path)
3. **Pairing Request**: Client sends pairing request with device info
4. **Pairing Challenge**: Server sends an empty `pair_challenge` — the 6-digit PIN is displayed **only on the PC companion screen**. The user must read it from the PC and enter it on the Android app.
5. **Pairing Confirm**: Client sends `pair_confirm` with the user-entered PIN
6. **Authentication**: Returning devices send stored session token via `auth`
7. **Session Established**: Bidirectional input commands begin

## Message Schema
```json
{
  "v": 1,                    // Protocol version
  "t": "mouse_move",         // Message type
  "seq": 12345,              // Optional sequence number
  "ts": 1697500000000,       // Timestamp (ms)
  "p": {}                    // Payload (type-specific)
}
```

## Message Types

### Connection & Pairing
| Type | Direction | Description |
|------|-----------|-------------|
| `pair_request` | C→S | Request pairing with device info |
| `pair_challenge` | S→C | Server sends empty challenge (PIN shown on PC screen only) |
| `pair_confirm` | C→S | Client sends user-entered PIN for verification |
| `pair_result` | S→C | Pairing success/failure + session token |
| `auth` | C→S | Authenticate with session token |
| `auth_result` | S→C | Authentication result |

### Mouse
| Type | Direction | Description |
|------|-----------|-------------|
| `mouse_move` | C→S | Relative mouse movement |
| `mouse_btn_down` | C→S | Mouse button pressed |
| `mouse_btn_up` | C→S | Mouse button released |
| `mouse_scroll` | C→S | Mouse wheel scroll |

### Keyboard
| Type | Direction | Description |
|------|-----------|-------------|
| `key_down` | C→S | Key pressed |
| `key_up` | C→S | Key released |
| `text_input` | C→S | Unicode text insertion |

### Media
| Type | Direction | Description |
|------|-----------|-------------|
| `media_action` | C→S | Media key action |

### Controller
| Type | Direction | Description |
|------|-----------|-------------|
| `ctrl_btn_down` | C→S | Controller button pressed |
| `ctrl_btn_up` | C→S | Controller button released |
| `ctrl_stick` | C→S | Analog stick position |
| `vibrate` | S→C | Game rumble/vibration feedback |

### System
| Type | Direction | Description |
|------|-----------|-------------|
| `ping` | C↔S | Latency check |
| `pong` | C↔S | Latency response |
| `status` | S→C | Server status update |
| `disconnect` | C↔S | Clean disconnect |

## Payload Definitions

### pair_request
```json
{ "device_name": "Pixel 8", "device_id": "uuid", "app_version": "1.0.0" }
```

### pair_challenge
```json
{ "code": "" }
```
> **Note:** The `code` field is intentionally empty. The actual 6-digit PIN is displayed on the PC companion UI. The user must visually read it from the PC and type it into the Android app.

### pair_confirm
```json
{ "code": "123456", "device_id": "uuid" }
```
> The `code` is the 6-digit PIN the user read from the PC screen.

### mouse_move
```json
{ "dx": 5.2, "dy": -3.1 }
```

### mouse_btn_down / mouse_btn_up
```json
{ "btn": "left" }  // "left", "right", "middle"
```

### mouse_scroll
```json
{ "dx": 0.0, "dy": -3.0 }
```

### key_down / key_up
```json
{ "key": 65, "code": "KeyA" }  // Windows virtual key code
```

### text_input
```json
{ "text": "Hello World" }
```

### media_action
```json
{ "action": "play_pause" }  // play_pause, next, prev, vol_up, vol_down, mute
```

### ctrl_btn_down / ctrl_btn_up
```json
{ "button": "a" }  // a, b, x, y, lb, rb, lt, rt, start, select, dpad_up, dpad_down, dpad_left, dpad_right
```

### ctrl_stick
```json
{ "stick": "left", "x": 0.5, "y": -0.3 }
```

### vibrate
```json
{ "large_motor": 128, "small_motor": 64 }  // 0-255 intensity, forwarded from Xbox controller rumble
```

## Security
- **Pairing PIN verification**: 6-digit PIN is displayed ONLY on the PC screen. The Android user must enter it manually. The challenge sent over WebSocket is intentionally blank.
- **PIN brute-force protection**: Max 5 failed PIN attempts per device, then a 60-second lockout. Pairing requests are rate-limited to 10 per minute globally.
- **PIN replay prevention**: Repeated pairing requests from the same device reuse the existing PIN instead of generating a new one (prevents PIN-reset spam).
- **Session tokens**: All paired devices receive a cryptographically random 256-bit session token.
- **Token storage**: Android stores tokens in EncryptedSharedPreferences (AES-256-GCM backed by Android Keystore). Windows stores paired-device data with DPAPI encryption.
- **Device revocation**: Revoking a device immediately disconnects its active sessions server-side.
- **Connection limits**: Max 5 authenticated clients. Max 10 unauthenticated connections with a 15-second handshake timeout and 30-second authentication timeout.
- **Input validation**: Mouse deltas, scroll values, and stick positions are clamped to safe ranges. Non-finite values are rejected.
- **Per-session input tracking**: Disconnecting one client releases only that client's held keys/buttons — not all clients'.
- **Message assembly**: WebSocket fragments are properly assembled before parsing. Oversized messages (>4KB) are rejected.
- **Network policy**: Android app denies cleartext traffic by default; cleartext is permitted only for private LAN IP ranges (10.x, 192.168.x, 172.16.x, localhost).
- **DPAPI fallback removed**: If DPAPI decryption fails, the data is treated as corrupted rather than falling back to plaintext.
- Unpaired devices can only send `pair_request`
- Invalid messages are silently dropped
- Server binds to local network interfaces

## Versioning
- Protocol version is included in every message
- Server rejects messages with unsupported versions
- Future versions maintain backward compatibility where possible
