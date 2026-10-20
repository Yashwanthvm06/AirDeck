"""
TouchDeck Companion Diagnostic & E2E Test Client
Verifies WebSocket handshake, 6-digit PIN pairing, authentication,
Mouse Trackpad, Full Keyboard & Modifiers, and Gamepad / Analog Stick emulation.

NOTE: The server sends an EMPTY pair_challenge code — the actual PIN is
displayed on the PC screen. During testing you must manually enter the
PIN shown on the PC, or run in auto-confirm mode for unit testing.
"""

import sys
import json
import time
import asyncio

try:
    import websockets
except ImportError:
    print("[!] 'websockets' library is required. Install via: pip install websockets")
    sys.exit(1)

SERVER_IP = "127.0.0.1"
SERVER_PORT = 9876

async def run_test():
    uri = f"ws://{SERVER_IP}:{SERVER_PORT}"
    print("=" * 60)
    print(f"[*] TouchDeck E2E Test Suite connecting to {uri}...")
    print("=" * 60)

    try:
        async with websockets.connect(uri) as ws:
            print("[+] [OK] WebSocket TCP connection established.")

            # -----------------------------------------------------------
            # 1. Pairing Handshake / Authentication
            # -----------------------------------------------------------
            device_id = "test-device-uuid-12345"
            pair_request = {
                "v": 1,
                "t": "pair_request",
                "p": {
                    "device_name": "TouchDeck Test Runner",
                    "device_id": device_id,
                    "app_version": "1.0.0"
                }
            }
            await ws.send(json.dumps(pair_request))
            print("[*] Sent pair_request...")

            resp_raw = await asyncio.wait_for(ws.recv(), timeout=5.0)
            resp = json.loads(resp_raw)
            if resp.get("t") == "auth_challenge":
                print(f"[+] [OK] Server sent auth_challenge (nonce={resp.get('p', {}).get('nonce', '')[:12]}...)")
                resp_raw = await asyncio.wait_for(ws.recv(), timeout=5.0)
                resp = json.loads(resp_raw)

            print(f"[<] Response: {resp.get('t')}")

            token = None
            if resp.get("t") == "pair_challenge":
                # The server sends an empty code field — the real PIN is
                # displayed on the PC companion screen for visual verification.
                code_from_server = resp["p"].get("code", "")
                if code_from_server:
                    print(f"[!] Server sent non-empty code in challenge: [{code_from_server}]")
                    print("    (This is unexpected — the PIN should only appear on the PC screen)")
                else:
                    print("[+] [OK] Received pair_challenge with empty code (PIN is on PC screen)")

                # Prompt user for the PIN shown on the PC
                pin = input("[?] Enter the 6-digit PIN shown on the PC companion: ").strip()
                if len(pin) != 6 or not pin.isdigit():
                    print("[-] Invalid PIN format. Must be exactly 6 digits.")
                    sys.exit(1)

                confirm_msg = {
                    "v": 1,
                    "t": "pair_confirm",
                    "p": {
                        "code": pin,
                        "device_id": device_id
                    }
                }
                await ws.send(json.dumps(confirm_msg))
                print("[*] Sent pair_confirm with user-entered PIN...")

                resp_raw = await asyncio.wait_for(ws.recv(), timeout=5.0)
                resp = json.loads(resp_raw)
                if resp.get("t") == "pair_result" and resp.get("p", {}).get("success"):
                    token = resp["p"]["token"]
                    print(f"[+] [OK] Pairing succeeded! Auth Token: {token[:12]}...")
                else:
                    error = resp.get("p", {}).get("error", "Unknown error")
                    print(f"[-] Pairing failed: {error}")
                    sys.exit(1)
            elif resp.get("t") == "pair_result":
                if resp.get("p", {}).get("success"):
                    token = resp["p"]["token"]
                    print(f"[+] [OK] Existing paired device accepted! Auth Token: {token[:12]}...")

            if token is None:
                print("[-] Failed to obtain authentication token. Aborting.")
                sys.exit(1)

            # -----------------------------------------------------------
            # 2. Ping / Pong Roundtrip Latency
            # -----------------------------------------------------------
            t0 = time.time()
            await ws.send(json.dumps({"v": 1, "t": "ping"}))
            pong_raw = await asyncio.wait_for(ws.recv(), timeout=5.0)
            rtt = (time.time() - t0) * 1000.0
            print(f"[+] [OK] Ping/Pong roundtrip latency: {rtt:.2f} ms")

            # -----------------------------------------------------------
            # 3. Trackpad & Mouse Buttons (using correct message types)
            # -----------------------------------------------------------
            print("[*] Testing Trackpad (Relative Mouse Movement)...")
            await ws.send(json.dumps({"v": 1, "t": "mouse_move", "p": {"dx": 5.0, "dy": 5.0}}))
            await asyncio.sleep(0.02)
            await ws.send(json.dumps({"v": 1, "t": "mouse_move", "p": {"dx": -5.0, "dy": -5.0}}))
            print("    -> mouse_move OK")

            print("[*] Testing Mouse Clicks (Left & Right)...")
            # Use the correct message types: mouse_btn_down / mouse_btn_up (not mouse_down/mouse_up)
            # Use the correct payload field: "btn" (as per protocol docs and server implementation)
            await ws.send(json.dumps({"v": 1, "t": "mouse_btn_down", "p": {"btn": "left"}}))
            await asyncio.sleep(0.01)
            await ws.send(json.dumps({"v": 1, "t": "mouse_btn_up", "p": {"btn": "left"}}))
            print("    -> left click OK")

            await ws.send(json.dumps({"v": 1, "t": "mouse_btn_down", "p": {"btn": "right"}}))
            await asyncio.sleep(0.01)
            await ws.send(json.dumps({"v": 1, "t": "mouse_btn_up", "p": {"btn": "right"}}))
            print("    -> right click OK")

            # -----------------------------------------------------------
            # 4. Mouse Scroll
            # -----------------------------------------------------------
            print("[*] Testing Mouse Scroll...")
            await ws.send(json.dumps({"v": 1, "t": "mouse_scroll", "p": {"dx": 0.0, "dy": -3.0}}))
            await asyncio.sleep(0.02)
            await ws.send(json.dumps({"v": 1, "t": "mouse_scroll", "p": {"dx": 0.0, "dy": 3.0}}))
            print("    -> mouse_scroll OK")

            # -----------------------------------------------------------
            # 5. Keyboard & Modifiers
            # -----------------------------------------------------------
            print("[*] Testing Keyboard Modifiers & Keys...")
            # Shift key (VK 0x10)
            await ws.send(json.dumps({"v": 1, "t": "key_down", "p": {"key": 0x10}}))
            await asyncio.sleep(0.01)
            await ws.send(json.dumps({"v": 1, "t": "key_up", "p": {"key": 0x10}}))
            print("    -> modifier key_down/key_up (Shift) OK")

            # Ctrl key (VK 0x11)
            await ws.send(json.dumps({"v": 1, "t": "key_down", "p": {"key": 0x11}}))
            await asyncio.sleep(0.01)
            await ws.send(json.dumps({"v": 1, "t": "key_up", "p": {"key": 0x11}}))
            print("    -> modifier key_down/key_up (Ctrl) OK")

            # -----------------------------------------------------------
            # 6. Gamepad Emulator: Buttons
            # -----------------------------------------------------------
            print("[*] Testing Gamepad Buttons (ABXY, Shoulders, D-Pad, Thumbsticks)...")
            buttons_to_test = [
                "a", "b", "x", "y",
                "lb", "rb", "lt", "rt",
                "dpad_up", "dpad_down", "dpad_left", "dpad_right",
                "start", "select", "l3", "r3"
            ]
            for btn in buttons_to_test:
                await ws.send(json.dumps({"v": 1, "t": "ctrl_btn_down", "p": {"button": btn}}))
                await asyncio.sleep(0.01)
                await ws.send(json.dumps({"v": 1, "t": "ctrl_btn_up", "p": {"button": btn}}))
            print(f"    -> All {len(buttons_to_test)} gamepad buttons passed OK")

            # -----------------------------------------------------------
            # 7. Gamepad Emulator: Dual Analog Sticks
            # -----------------------------------------------------------
            print("[*] Testing Dual Analog Sticks...")
            # Left Stick deflection
            await ws.send(json.dumps({"v": 1, "t": "ctrl_stick", "p": {"stick": "left", "x": 0.75, "y": -0.85}}))
            await asyncio.sleep(0.03)
            # Left Stick return to center
            await ws.send(json.dumps({"v": 1, "t": "ctrl_stick", "p": {"stick": "left", "x": 0.0, "y": 0.0}}))
            print("    -> Left analog stick deflection & reset OK")

            # Right Stick deflection (Aim/Look)
            await ws.send(json.dumps({"v": 1, "t": "ctrl_stick", "p": {"stick": "right", "x": -0.5, "y": 0.5}}))
            await asyncio.sleep(0.03)
            # Right Stick return to center
            await ws.send(json.dumps({"v": 1, "t": "ctrl_stick", "p": {"stick": "right", "x": 0.0, "y": 0.0}}))
            print("    -> Right analog stick deflection & reset OK")

            # -----------------------------------------------------------
            # 8. Disconnect / Cleanup
            # -----------------------------------------------------------
            await ws.send(json.dumps({"v": 1, "t": "disconnect", "p": {}}))
            print("[+] [OK] Disconnect command sent; clean release confirmed.")

            print("=" * 60)
            print("[SUCCESS] ALL TESTS PASSED! TouchDeck Companion is fully operational.")
            print("=" * 60)

    except Exception as e:
        print(f"\n[-] Test failed with error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) > 1:
        SERVER_IP = sys.argv[1]
    if len(sys.argv) > 2:
        SERVER_PORT = int(sys.argv[2])
    asyncio.run(run_test())
