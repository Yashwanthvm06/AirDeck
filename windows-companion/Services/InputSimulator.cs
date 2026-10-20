using System.Runtime.InteropServices;

namespace TouchDeckCompanion.Services;

/// <summary>
/// Windows input simulation using the SendInput API.
/// Handles mouse movement, clicks, scrolling, and keyboard input.
/// </summary>
public class InputSimulator
{
    #region Native Interop

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    [DllImport("user32.dll")]
    private static extern int GetSystemMetrics(int nIndex);

    private const int SM_CXSCREEN = 0;
    private const int SM_CYSCREEN = 1;

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT
    {
        public uint Type;
        public INPUTUNION Union;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct INPUTUNION
    {
        [FieldOffset(0)] public MOUSEINPUT Mouse;
        [FieldOffset(0)] public KEYBDINPUT Keyboard;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT
    {
        public int dx;
        public int dy;
        public int mouseData;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    private const uint INPUT_MOUSE = 0;
    private const uint INPUT_KEYBOARD = 1;

    private const uint MOUSEEVENTF_MOVE = 0x0001;
    private const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    private const uint MOUSEEVENTF_LEFTUP = 0x0004;
    private const uint MOUSEEVENTF_RIGHTDOWN = 0x0008;
    private const uint MOUSEEVENTF_RIGHTUP = 0x0010;
    private const uint MOUSEEVENTF_MIDDLEDOWN = 0x0020;
    private const uint MOUSEEVENTF_MIDDLEUP = 0x0040;
    private const uint MOUSEEVENTF_WHEEL = 0x0800;
    private const uint MOUSEEVENTF_HWHEEL = 0x1000;

    private const uint KEYEVENTF_KEYDOWN = 0x0000;
    private const uint KEYEVENTF_KEYUP = 0x0002;
    private const uint KEYEVENTF_UNICODE = 0x0004;
    private const uint KEYEVENTF_SCANCODE = 0x0008;

    #endregion

    // Track held keys and buttons for cleanup on disconnect
    private readonly HashSet<ushort> _heldKeys = new();
    private readonly HashSet<string> _heldMouseButtons = new();
    private readonly object _lock = new();

    /// <summary>
    /// Move the mouse cursor by relative delta values.
    /// </summary>
    public void MoveMouse(double dx, double dy)
    {
        var input = new INPUT
        {
            Type = INPUT_MOUSE,
            Union = new INPUTUNION
            {
                Mouse = new MOUSEINPUT
                {
                    dx = (int)Math.Round(dx),
                    dy = (int)Math.Round(dy),
                    dwFlags = MOUSEEVENTF_MOVE
                }
            }
        };

        SendInput(1, [input], Marshal.SizeOf<INPUT>());
    }

    /// <summary>
    /// Press a mouse button.
    /// </summary>
    public void MouseButtonDown(string button)
    {
        uint flags = button switch
        {
            "left" => MOUSEEVENTF_LEFTDOWN,
            "right" => MOUSEEVENTF_RIGHTDOWN,
            "middle" => MOUSEEVENTF_MIDDLEDOWN,
            _ => MOUSEEVENTF_LEFTDOWN
        };

        var input = new INPUT
        {
            Type = INPUT_MOUSE,
            Union = new INPUTUNION
            {
                Mouse = new MOUSEINPUT { dwFlags = flags }
            }
        };

        SendInput(1, [input], Marshal.SizeOf<INPUT>());

        lock (_lock) { _heldMouseButtons.Add(button); }
    }

    /// <summary>
    /// Release a mouse button.
    /// </summary>
    public void MouseButtonUp(string button)
    {
        uint flags = button switch
        {
            "left" => MOUSEEVENTF_LEFTUP,
            "right" => MOUSEEVENTF_RIGHTUP,
            "middle" => MOUSEEVENTF_MIDDLEUP,
            _ => MOUSEEVENTF_LEFTUP
        };

        var input = new INPUT
        {
            Type = INPUT_MOUSE,
            Union = new INPUTUNION
            {
                Mouse = new MOUSEINPUT { dwFlags = flags }
            }
        };

        SendInput(1, [input], Marshal.SizeOf<INPUT>());

        lock (_lock) { _heldMouseButtons.Remove(button); }
    }

    /// <summary>
    /// Scroll the mouse wheel.
    /// </summary>
    public void MouseScroll(double dx, double dy)
    {
        var inputs = new List<INPUT>();

        if (Math.Abs(dy) > 0.01)
        {
            inputs.Add(new INPUT
            {
                Type = INPUT_MOUSE,
                Union = new INPUTUNION
                {
                    Mouse = new MOUSEINPUT
                    {
                        mouseData = (int)(dy * 120),
                        dwFlags = MOUSEEVENTF_WHEEL
                    }
                }
            });
        }

        if (Math.Abs(dx) > 0.01)
        {
            inputs.Add(new INPUT
            {
                Type = INPUT_MOUSE,
                Union = new INPUTUNION
                {
                    Mouse = new MOUSEINPUT
                    {
                        mouseData = (int)(dx * 120),
                        dwFlags = MOUSEEVENTF_HWHEEL
                    }
                }
            });
        }

        if (inputs.Count > 0)
        {
            SendInput((uint)inputs.Count, inputs.ToArray(), Marshal.SizeOf<INPUT>());
        }
    }

    /// <summary>
    /// Press a keyboard key by virtual key code.
    /// </summary>
    public void KeyDown(ushort vkCode)
    {
        var input = new INPUT
        {
            Type = INPUT_KEYBOARD,
            Union = new INPUTUNION
            {
                Keyboard = new KEYBDINPUT
                {
                    wVk = vkCode,
                    dwFlags = KEYEVENTF_KEYDOWN
                }
            }
        };

        SendInput(1, [input], Marshal.SizeOf<INPUT>());

        lock (_lock) { _heldKeys.Add(vkCode); }
    }

    /// <summary>
    /// Release a keyboard key by virtual key code.
    /// </summary>
    public void KeyUp(ushort vkCode)
    {
        var input = new INPUT
        {
            Type = INPUT_KEYBOARD,
            Union = new INPUTUNION
            {
                Keyboard = new KEYBDINPUT
                {
                    wVk = vkCode,
                    dwFlags = KEYEVENTF_KEYUP
                }
            }
        };

        SendInput(1, [input], Marshal.SizeOf<INPUT>());

        lock (_lock) { _heldKeys.Remove(vkCode); }
    }

    /// <summary>
    /// Type Unicode text by sending individual character events.
    /// </summary>
    public void TypeText(string text)
    {
        if (string.IsNullOrEmpty(text)) return;

        var inputs = new List<INPUT>();

        foreach (char c in text)
        {
            inputs.Add(new INPUT
            {
                Type = INPUT_KEYBOARD,
                Union = new INPUTUNION
                {
                    Keyboard = new KEYBDINPUT
                    {
                        wVk = 0,
                        wScan = c,
                        dwFlags = KEYEVENTF_UNICODE
                    }
                }
            });
            inputs.Add(new INPUT
            {
                Type = INPUT_KEYBOARD,
                Union = new INPUTUNION
                {
                    Keyboard = new KEYBDINPUT
                    {
                        wVk = 0,
                        wScan = c,
                        dwFlags = KEYEVENTF_UNICODE | KEYEVENTF_KEYUP
                    }
                }
            });
        }

        if (inputs.Count > 0)
        {
            SendInput((uint)inputs.Count, inputs.ToArray(), Marshal.SizeOf<INPUT>());
        }
    }

    /// <summary>
    /// Send a media key event (play/pause, volume, etc.).
    /// </summary>
    public void SendMediaKey(string action)
    {
        ushort vk = action switch
        {
            "play_pause" => 0xB3,  // VK_MEDIA_PLAY_PAUSE
            "next" => 0xB0,        // VK_MEDIA_NEXT_TRACK
            "prev" => 0xB1,        // VK_MEDIA_PREV_TRACK
            "stop" => 0xB2,        // VK_MEDIA_STOP
            "vol_up" => 0xAF,      // VK_VOLUME_UP
            "vol_down" => 0xAE,    // VK_VOLUME_DOWN
            "mute" => 0xAD,        // VK_VOLUME_MUTE
            _ => 0
        };

        if (vk == 0) return;

        var inputs = new INPUT[]
        {
            new()
            {
                Type = INPUT_KEYBOARD,
                Union = new INPUTUNION
                {
                    Keyboard = new KEYBDINPUT
                    {
                        wVk = vk,
                        dwFlags = KEYEVENTF_KEYDOWN
                    }
                }
            },
            new()
            {
                Type = INPUT_KEYBOARD,
                Union = new INPUTUNION
                {
                    Keyboard = new KEYBDINPUT
                    {
                        wVk = vk,
                        dwFlags = KEYEVENTF_KEYUP
                    }
                }
            }
        };

        SendInput(2, inputs, Marshal.SizeOf<INPUT>());
    }

    /// <summary>
    /// Release all held keys and mouse buttons.
    /// Called on disconnect to prevent stuck input.
    /// </summary>
    public void ReleaseAll()
    {
        lock (_lock)
        {
            foreach (var vk in _heldKeys.ToArray())
            {
                var input = new INPUT
                {
                    Type = INPUT_KEYBOARD,
                    Union = new INPUTUNION
                    {
                        Keyboard = new KEYBDINPUT
                        {
                            wVk = vk,
                            dwFlags = KEYEVENTF_KEYUP
                        }
                    }
                };
                SendInput(1, [input], Marshal.SizeOf<INPUT>());
            }
            _heldKeys.Clear();

            foreach (var btn in _heldMouseButtons.ToArray())
            {
                MouseButtonUp(btn);
            }
            _heldMouseButtons.Clear();
        }
    }
}
