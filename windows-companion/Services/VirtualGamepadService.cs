using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;
using TouchDeckCompanion.Models;

namespace TouchDeckCompanion.Services;

/// <summary>
/// Handles virtual gamepad emulation.
/// When ViGEmBus driver is installed on Windows, creates a genuine virtual Xbox 360 controller
/// that games and emulators detect natively.
/// When ViGEmBus is not installed, falls back to enhanced gaming keyboard/mouse emulation (WASD + mouse look).
/// </summary>
public class VirtualGamepadService : IDisposable
{
    private readonly InputSimulator _inputSimulator;
    private ViGEmClient? _vigemClient;
    private IXbox360Controller? _controller;
    private readonly object _lock = new();

    // Held WASD keys for fallback mode
    private bool _holdingW;
    private bool _holdingA;
    private bool _holdingS;
    private bool _holdingD;

    // Track state
    public bool IsViGEmActive { get; private set; }
    public string StatusMessage { get; private set; } = "Initializing Gamepad...";

    public event Action<bool, string>? OnGamepadStatusChanged;
    public event Action<string>? OnLog;
    public event Action<byte, byte>? OnVibrationReceived;  // largeMotor, smallMotor

    public VirtualGamepadService(InputSimulator inputSimulator)
    {
        _inputSimulator = inputSimulator;
        InitializeVirtualGamepad();
    }

    public void InitializeVirtualGamepad()
    {
        lock (_lock)
        {
            try
            {
                _vigemClient = new ViGEmClient();
                _controller = _vigemClient.CreateXbox360Controller();
                _controller.Connect();

                // Subscribe to game rumble/force feedback from ViGEmBus
                _controller.FeedbackReceived += (sender, args) =>
                {
                    try
                    {
                        OnVibrationReceived?.Invoke(args.LargeMotor, args.SmallMotor);
                    }
                    catch (Exception feedbackEx)
                    {
                        OnLog?.Invoke($"[Gamepad] Vibration forward error: {feedbackEx.Message}");
                    }
                };

                IsViGEmActive = true;
                StatusMessage = "Xbox 360 Controller (Native XInput Active)";
                OnLog?.Invoke("[Gamepad] ViGEmBus detected! Virtual Xbox 360 controller connected.");
            }
            catch (Exception ex)
            {
                IsViGEmActive = false;
                _controller = null;
                _vigemClient = null;
                StatusMessage = "Gaming Keyboard & Mouse Fallback (ViGEmBus driver not installed)";
                OnLog?.Invoke($"[Gamepad] ViGEm driver not found ({ex.Message}). Using enhanced PC Gaming (WASD) mode.");
            }

            OnGamepadStatusChanged?.Invoke(IsViGEmActive, StatusMessage);
        }
    }

    public void HandleButton(string button, bool isDown)
    {
        lock (_lock)
        {
            if (IsViGEmActive && _controller != null)
            {
                HandleXboxButton(button, isDown);
            }
            else
            {
                HandleFallbackButton(button, isDown);
            }
        }
    }

    private void HandleXboxButton(string button, bool isDown)
    {
        if (_controller == null) return;

        try
        {
            switch (button.ToLowerInvariant())
            {
                case "a":
                case "cross":
                case "ps_cross":
                    _controller.SetButtonState(Xbox360Button.A, isDown);
                    break;
                case "b":
                case "circle":
                case "ps_circle":
                    _controller.SetButtonState(Xbox360Button.B, isDown);
                    break;
                case "x":
                case "square":
                case "ps_square":
                    _controller.SetButtonState(Xbox360Button.X, isDown);
                    break;
                case "y":
                case "triangle":
                case "ps_triangle":
                    _controller.SetButtonState(Xbox360Button.Y, isDown);
                    break;
                case "lb":
                case "l1":
                case "left_shoulder":
                    _controller.SetButtonState(Xbox360Button.LeftShoulder, isDown);
                    break;
                case "rb":
                case "r1":
                case "right_shoulder":
                    _controller.SetButtonState(Xbox360Button.RightShoulder, isDown);
                    break;
                case "lt":
                case "l2":
                case "left_trigger":
                    _controller.SetSliderValue(Xbox360Slider.LeftTrigger, isDown ? (byte)255 : (byte)0);
                    break;
                case "rt":
                case "r2":
                case "right_trigger":
                    _controller.SetSliderValue(Xbox360Slider.RightTrigger, isDown ? (byte)255 : (byte)0);
                    break;
                case "start":
                case "options":
                    _controller.SetButtonState(Xbox360Button.Start, isDown);
                    break;
                case "select":
                case "back":
                case "share":
                    _controller.SetButtonState(Xbox360Button.Back, isDown);
                    break;
                case "guide":
                case "home":
                case "ps":
                    _controller.SetButtonState(Xbox360Button.Guide, isDown);
                    break;
                case "dpad_up":
                case "up":
                    _controller.SetButtonState(Xbox360Button.Up, isDown);
                    break;
                case "dpad_down":
                case "down":
                    _controller.SetButtonState(Xbox360Button.Down, isDown);
                    break;
                case "dpad_left":
                case "left":
                    _controller.SetButtonState(Xbox360Button.Left, isDown);
                    break;
                case "dpad_right":
                case "right":
                    _controller.SetButtonState(Xbox360Button.Right, isDown);
                    break;
                case "l3":
                case "thumb_left":
                case "left_stick":
                    _controller.SetButtonState(Xbox360Button.LeftThumb, isDown);
                    break;
                case "r3":
                case "thumb_right":
                case "right_stick":
                    _controller.SetButtonState(Xbox360Button.RightThumb, isDown);
                    break;
            }

            _controller.SubmitReport();
        }
        catch (Exception ex)
        {
            OnLog?.Invoke($"[Gamepad] Error updating Xbox button: {ex.Message}");
        }
    }

    public void HandleStick(string stick, double x, double y)
    {
        lock (_lock)
        {
            if (IsViGEmActive && _controller != null)
            {
                HandleXboxStick(stick, x, y);
            }
            else
            {
                HandleFallbackStick(stick, x, y);
            }
        }
    }

    private void HandleXboxStick(string stick, double x, double y)
    {
        if (_controller == null) return;

        try
        {
            // Clamped between -1.0 and 1.0
            double clampedX = Math.Clamp(x, -1.0, 1.0);
            double clampedY = Math.Clamp(y, -1.0, 1.0);

            // In XInput: Y is positive UP, negative DOWN
            // On touch screens, y is often inverted (+1 down, -1 up), so if positive is down, invert it:
            short axisX = (short)Math.Round(clampedX * 32767.0);
            short axisY = (short)Math.Round(clampedY * 32767.0);

            if (stick.Equals("right", StringComparison.OrdinalIgnoreCase))
            {
                _controller.SetAxisValue(Xbox360Axis.RightThumbX, axisX);
                _controller.SetAxisValue(Xbox360Axis.RightThumbY, axisY);
            }
            else
            {
                _controller.SetAxisValue(Xbox360Axis.LeftThumbX, axisX);
                _controller.SetAxisValue(Xbox360Axis.LeftThumbY, axisY);
            }

            _controller.SubmitReport();
        }
        catch (Exception ex)
        {
            OnLog?.Invoke($"[Gamepad] Error updating Xbox stick: {ex.Message}");
        }
    }

    #region Enhanced Fallback Keyboard & Mouse Emulation

    // Standard PC gaming bindings when ViGEm is not installed
    private static readonly Dictionary<string, ushort> FallbackButtonKeyCodes = new()
    {
        ["a"] = 0x20,           // Space (Jump)
        ["cross"] = 0x20,
        ["ps_cross"] = 0x20,
        ["b"] = 0x43,           // C (Crouch / Cancel)
        ["circle"] = 0x43,
        ["ps_circle"] = 0x43,
        ["x"] = 0x45,           // E (Interact)
        ["square"] = 0x45,
        ["ps_square"] = 0x45,
        ["y"] = 0x52,           // R (Reload)
        ["triangle"] = 0x52,
        ["ps_triangle"] = 0x52,
        ["lb"] = 0x51,          // Q (Ability / Switch)
        ["l1"] = 0x51,
        ["left_shoulder"] = 0x51,
        ["rb"] = 0x46,          // F (Melee)
        ["r1"] = 0x46,
        ["right_shoulder"] = 0x46,
        ["start"] = 0x1B,       // Escape (Menu)
        ["options"] = 0x1B,
        ["select"] = 0x09,      // Tab (Scoreboard / Inventory)
        ["back"] = 0x09,
        ["share"] = 0x09,
        ["dpad_up"] = 0x31,     // 1 (Weapon 1)
        ["up"] = 0x31,
        ["dpad_right"] = 0x32,  // 2 (Weapon 2)
        ["right"] = 0x32,
        ["dpad_down"] = 0x33,   // 3 (Weapon 3)
        ["down"] = 0x33,
        ["dpad_left"] = 0x34,   // 4 (Weapon 4)
        ["left"] = 0x34,
        ["l3"] = 0x10,          // Left Shift (Sprint)
        ["thumb_left"] = 0x10,
        ["left_stick"] = 0x10,
        ["r3"] = 0x56,          // V (Melee)
        ["thumb_right"] = 0x56,
        ["right_stick"] = 0x56,
    };

    private void HandleFallbackButton(string button, bool isDown)
    {
        var lower = button.ToLowerInvariant();

        // Mouse triggers
        if (lower is "rt" or "r2" or "right_trigger")
        {
            if (isDown) _inputSimulator.MouseButtonDown("left");
            else _inputSimulator.MouseButtonUp("left");
            return;
        }

        if (lower is "lt" or "l2" or "left_trigger")
        {
            if (isDown) _inputSimulator.MouseButtonDown("right");
            else _inputSimulator.MouseButtonUp("right");
            return;
        }

        if (FallbackButtonKeyCodes.TryGetValue(lower, out var vk))
        {
            if (isDown) _inputSimulator.KeyDown(vk);
            else _inputSimulator.KeyUp(vk);
        }
    }

    private void HandleFallbackStick(string stick, double x, double y)
    {
        if (stick.Equals("right", StringComparison.OrdinalIgnoreCase))
        {
            // Right stick controls mouse look / aim in PC gaming
            const double deadZone = 0.10;
            const double sensitivity = 15.0;

            double dx = Math.Abs(x) < deadZone ? 0 : x * sensitivity;
            // For camera look, dragging up on stick looks up (negative dy)
            double dy = Math.Abs(y) < deadZone ? 0 : -y * sensitivity;

            if (Math.Abs(dx) > 0.01 || Math.Abs(dy) > 0.01)
            {
                _inputSimulator.MoveMouse(dx, dy);
            }
        }
        else
        {
            // Left stick controls WASD movement!
            const double threshold = 0.35;

            // In touch screen y is: UP = positive, DOWN = negative (or vice versa)
            // If y > threshold -> W (forward)
            // If y < -threshold -> S (backward)
            bool wantW = y > threshold;
            bool wantS = y < -threshold;
            bool wantD = x > threshold;
            bool wantA = x < -threshold;

            UpdateKeyHold(0x57, wantW, ref _holdingW); // W
            UpdateKeyHold(0x41, wantA, ref _holdingA); // A
            UpdateKeyHold(0x53, wantS, ref _holdingS); // S
            UpdateKeyHold(0x44, wantD, ref _holdingD); // D
        }
    }

    private void UpdateKeyHold(ushort vk, bool shouldHold, ref bool currentHold)
    {
        if (shouldHold && !currentHold)
        {
            _inputSimulator.KeyDown(vk);
            currentHold = true;
        }
        else if (!shouldHold && currentHold)
        {
            _inputSimulator.KeyUp(vk);
            currentHold = false;
        }
    }

    #endregion

    public void ReleaseAll()
    {
        lock (_lock)
        {
            if (IsViGEmActive && _controller != null)
            {
                try
                {
                    _controller.ResetReport();
                    _controller.SubmitReport();
                }
                catch { }
            }

            if (_holdingW) { _inputSimulator.KeyUp(0x57); _holdingW = false; }
            if (_holdingA) { _inputSimulator.KeyUp(0x41); _holdingA = false; }
            if (_holdingS) { _inputSimulator.KeyUp(0x53); _holdingS = false; }
            if (_holdingD) { _inputSimulator.KeyUp(0x44); _holdingD = false; }
        }
    }

    public void Dispose()
    {
        lock (_lock)
        {
            ReleaseAll();
            try
            {
                _controller?.Disconnect();
            }
            catch { }
            _controller = null;
            _vigemClient?.Dispose();
            _vigemClient = null;
        }
    }
}
