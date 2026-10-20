package com.touchdeck.app.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchdeck.app.TouchDeckViewModel
import com.touchdeck.app.ui.theme.*
import kotlin.math.roundToInt

enum class ControllerStyle {
    PLAYSTATION, // PSP / PPSSPP / PS2 (△ ○ ✕ □)
    XBOX         // Xbox 360 (Y B A X)
}

@Composable
fun ControllerScreen(
    viewModel: TouchDeckViewModel,
    onBack: () -> Unit
) {
    // Force Landscape orientation for handheld emulator feel, restore on exit
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    val latency by viewModel.client.latencyMs.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var currentStyle by remember { mutableStateOf(ControllerStyle.XBOX) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ==========================================
            // TOP BAR: SHOULDERS (LT/LB, RT/RB) & CENTER SYSTEM
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Shoulders: LT (Trigger), LB (Bumper)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DualLabelGamepadButton(
                        buttonId = "lt",
                        primaryLabel = "LT",
                        secondaryLabel = "L2",
                        color = Color(0xFF00B0FF),
                        viewModel = viewModel,
                        modifier = Modifier.width(70.dp).height(38.dp)
                    )
                    DualLabelGamepadButton(
                        buttonId = "lb",
                        primaryLabel = "LB",
                        secondaryLabel = "L1",
                        color = Color(0xFF00E5FF),
                        viewModel = viewModel,
                        modifier = Modifier.width(64.dp).height(38.dp)
                    )
                }

                // Center System Bar: Back, Select, Guide, Start, Latency, Style Toggle
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            viewModel.haptics.buttonClick()
                            onBack()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    MultiTouchGamepadButton(
                        buttonId = "select",
                        title = "SELECT",
                        color = Color(0xFF616175),
                        viewModel = viewModel,
                        modifier = Modifier.width(64.dp).height(30.dp),
                        fontSizeSp = 10
                    )

                    MultiTouchGamepadButton(
                        buttonId = "guide",
                        title = "⬡",
                        color = AccentCyan,
                        viewModel = viewModel,
                        modifier = Modifier.size(30.dp),
                        shape = CircleShape
                    )

                    MultiTouchGamepadButton(
                        buttonId = "start",
                        title = "START",
                        color = Color(0xFF616175),
                        viewModel = viewModel,
                        modifier = Modifier.width(64.dp).height(30.dp),
                        fontSizeSp = 10
                    )

                    // Layout Mode Switcher (Xbox ABXY vs PSP Symbols)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E1E2C),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333348)),
                        modifier = Modifier.clickable {
                            viewModel.haptics.buttonClick()
                            currentStyle = if (currentStyle == ControllerStyle.XBOX) {
                                ControllerStyle.PLAYSTATION
                            } else {
                                ControllerStyle.XBOX
                            }
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (currentStyle == ControllerStyle.XBOX) "ABXY" else "△○✕□",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentStyle == ControllerStyle.XBOX) Color(0xFF00E676) else Color(0xFF2979FF)
                            )
                        }
                    }

                    if (latency >= 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF161622),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262638)),
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Text(
                                text = "${latency}ms",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (latency < 25) SuccessGreen else WarningAmber,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Right Shoulders: RB (Bumper), RT (Trigger)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DualLabelGamepadButton(
                        buttonId = "rb",
                        primaryLabel = "RB",
                        secondaryLabel = "R1",
                        color = Color(0xFF00E5FF),
                        viewModel = viewModel,
                        modifier = Modifier.width(64.dp).height(38.dp)
                    )
                    DualLabelGamepadButton(
                        buttonId = "rt",
                        primaryLabel = "RT",
                        secondaryLabel = "R2",
                        color = Color(0xFF00B0FF),
                        viewModel = viewModel,
                        modifier = Modifier.width(70.dp).height(38.dp)
                    )
                }
            }

            // ==========================================
            // MAIN CONTROLLER BODY (Centrally Aligned & Shifted Right)
            // Left: Left Joystick + D-Pad
            // Right: Right Joystick + Face Buttons (ABXY)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ========== LEFT ZONE (Below LT/LB - Centered & Right Shifted) ==========
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 20.dp) // shift rightward towards center
                    ) {
                        // Left Analog Stick + L3
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            VirtualJoystick(
                                stickName = "left",
                                label = "L",
                                viewModel = viewModel,
                                sizeDp = 132
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            MultiTouchGamepadButton(
                                buttonId = "l3",
                                title = "L3",
                                color = Color(0xFF3B3B52),
                                viewModel = viewModel,
                                modifier = Modifier.width(68.dp).height(24.dp),
                                fontSizeSp = 10
                            )
                        }

                        // D-Pad Cross
                        DpadCluster(viewModel = viewModel)
                    }
                }

                // ========== RIGHT ZONE (Below RB/RT - Centered & Right Shifted) ==========
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp) // shift rightward under RB/RT
                    ) {
                        // Right Analog Stick + R3
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            VirtualJoystick(
                                stickName = "right",
                                label = "R",
                                viewModel = viewModel,
                                sizeDp = 132
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            MultiTouchGamepadButton(
                                buttonId = "r3",
                                title = "R3",
                                color = Color(0xFF3B3B52),
                                viewModel = viewModel,
                                modifier = Modifier.width(68.dp).height(24.dp),
                                fontSizeSp = 10
                            )
                        }

                        // Face Button Diamond (ABXY / △○✕□)
                        FaceButtonDiamondCluster(
                            viewModel = viewModel,
                            style = currentStyle,
                            scale = settings.faceButtonScale
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// FACE BUTTON DIAMOND CLUSTER (ABXY / PSP)
// ==========================================
@Composable
private fun FaceButtonDiamondCluster(
    viewModel: TouchDeckViewModel,
    style: ControllerStyle,
    scale: Float = 1.0f
) {
    Box(
        modifier = Modifier.size((148f * scale).dp),
        contentAlignment = Alignment.Center
    ) {
        // TOP BUTTON: Y (Amber) / △ -> sends "y"
        MultiTouchGamepadFaceButton(
            primaryLabel = if (style == ControllerStyle.XBOX) "Y" else "△",
            subLabel = if (style == ControllerStyle.XBOX) "△" else "Y",
            buttonId = "y",
            color = Color(0xFFFFB300), // Amber
            viewModel = viewModel,
            scale = scale,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // LEFT BUTTON: X (Cyan/Blue) / □ -> sends "x"
        MultiTouchGamepadFaceButton(
            primaryLabel = if (style == ControllerStyle.XBOX) "X" else "□",
            subLabel = if (style == ControllerStyle.XBOX) "□" else "X",
            buttonId = "x",
            color = Color(0xFF00B0FF), // Cyan/Blue
            viewModel = viewModel,
            scale = scale,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        // RIGHT BUTTON: B (Red) / ○ -> sends "b"
        MultiTouchGamepadFaceButton(
            primaryLabel = if (style == ControllerStyle.XBOX) "B" else "○",
            subLabel = if (style == ControllerStyle.XBOX) "○" else "B",
            buttonId = "b",
            color = Color(0xFFFF1744), // Vivid Red
            viewModel = viewModel,
            scale = scale,
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // BOTTOM BUTTON: A (Green) / ✕ -> sends "a"
        MultiTouchGamepadFaceButton(
            primaryLabel = if (style == ControllerStyle.XBOX) "A" else "✕",
            subLabel = if (style == ControllerStyle.XBOX) "✕" else "A",
            buttonId = "a",
            color = Color(0xFF00E676), // Green
            viewModel = viewModel,
            scale = scale,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun MultiTouchGamepadFaceButton(
    primaryLabel: String,
    subLabel: String,
    buttonId: String,
    color: Color,
    viewModel: TouchDeckViewModel,
    modifier: Modifier = Modifier,
    scale: Float = 1.0f
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size((46f * scale).dp)
            .clip(CircleShape)
            .background(if (isPressed) color else color.copy(alpha = 0.22f))
            .border(2.dp, if (isPressed) Color.White else color, CircleShape)
            .pointerInput(buttonId) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press -> {
                                if (!isPressed) {
                                    isPressed = true
                                    viewModel.haptics.controllerButton()
                                    viewModel.client.sendControllerButtonDown(buttonId)
                                }
                            }
                            PointerEventType.Release -> {
                                if (isPressed) {
                                    isPressed = false
                                    viewModel.client.sendControllerButtonUp(buttonId)
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = primaryLabel,
                fontSize = (17f * scale).sp,
                fontWeight = FontWeight.Black,
                color = if (isPressed) BgDark else color
            )
            Text(
                text = subLabel,
                fontSize = (8f * scale).sp,
                fontWeight = FontWeight.Bold,
                color = if (isPressed) BgDark.copy(alpha = 0.7f) else color.copy(alpha = 0.7f),
                lineHeight = (8f * scale).sp
            )
        }
    }
}

// ==========================================
// D-PAD CROSS CLUSTER
// ==========================================
@Composable
private fun DpadCluster(viewModel: TouchDeckViewModel) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MultiTouchGamepadButton(
            title = "▲",
            buttonId = "dpad_up",
            color = Color(0xFF4A4A62),
            viewModel = viewModel,
            modifier = Modifier.size(42.dp),
            fontSizeSp = 14
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MultiTouchGamepadButton(
                title = "◀",
                buttonId = "dpad_left",
                color = Color(0xFF4A4A62),
                viewModel = viewModel,
                modifier = Modifier.size(42.dp),
                fontSizeSp = 14
            )
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF1E1E2C), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF333348), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3D3D52))
                )
            }
            MultiTouchGamepadButton(
                title = "▶",
                buttonId = "dpad_right",
                color = Color(0xFF4A4A62),
                viewModel = viewModel,
                modifier = Modifier.size(42.dp),
                fontSizeSp = 14
            )
        }
        MultiTouchGamepadButton(
            title = "▼",
            buttonId = "dpad_down",
            color = Color(0xFF4A4A62),
            viewModel = viewModel,
            modifier = Modifier.size(42.dp),
            fontSizeSp = 14
        )
    }
}

// ==========================================
// DUAL-LABEL SHOULDER BUTTON (LT/L2, LB/L1, etc.)
// ==========================================
@Composable
private fun DualLabelGamepadButton(
    buttonId: String,
    primaryLabel: String,
    secondaryLabel: String,
    color: Color,
    viewModel: TouchDeckViewModel,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (isPressed) color else color.copy(alpha = 0.22f))
            .border(1.5.dp, if (isPressed) Color.White else color.copy(alpha = 0.7f), shape)
            .pointerInput(buttonId) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press -> {
                                if (!isPressed) {
                                    isPressed = true
                                    viewModel.haptics.controllerButton()
                                    viewModel.client.sendControllerButtonDown(buttonId)
                                }
                            }
                            PointerEventType.Release -> {
                                if (isPressed) {
                                    isPressed = false
                                    viewModel.client.sendControllerButtonUp(buttonId)
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = primaryLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPressed) BgDark else TextPrimary
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "($secondaryLabel)",
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                color = if (isPressed) BgDark.copy(alpha = 0.7f) else TextSecondary
            )
        }
    }
}

// ==========================================
// MULTI-TOUCH ZERO-LATENCY BUTTON
// ==========================================
@Composable
private fun MultiTouchGamepadButton(
    buttonId: String,
    title: String,
    color: Color,
    viewModel: TouchDeckViewModel,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    fontSizeSp: Int = 12
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (isPressed) color else color.copy(alpha = 0.22f))
            .border(1.5.dp, if (isPressed) Color.White else color.copy(alpha = 0.7f), shape)
            .pointerInput(buttonId) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press -> {
                                if (!isPressed) {
                                    isPressed = true
                                    viewModel.haptics.controllerButton()
                                    viewModel.client.sendControllerButtonDown(buttonId)
                                }
                            }
                            PointerEventType.Release -> {
                                if (isPressed) {
                                    isPressed = false
                                    viewModel.client.sendControllerButtonUp(buttonId)
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPressed) BgDark else TextPrimary
        )
    }
}

// ==========================================
// VIRTUAL ANALOG JOYSTICK
// ==========================================
@Composable
private fun VirtualJoystick(
    stickName: String,
    label: String,
    viewModel: TouchDeckViewModel,
    sizeDp: Int = 132
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    var baseRadius by remember { mutableFloatStateOf(54f) }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1B1B28), Color(0xFF101018))
                )
            )
            .border(2.dp, Color(0xFF2C2C40), CircleShape)
            .onSizeChanged {
                baseRadius = (it.width / 2f) - 22f
            }
            .pointerInput(stickName) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val delta = offset - center
                        val dist = delta.getDistance()
                        val clamped = if (dist > baseRadius) delta * (baseRadius / dist) else delta
                        thumbOffset = clamped

                        val normX = (clamped.x / baseRadius).toDouble().coerceIn(-1.0, 1.0)
                        val normY = -(clamped.y / baseRadius).toDouble().coerceIn(-1.0, 1.0)
                        viewModel.client.sendControllerStick(stickName, normX, normY)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = thumbOffset + dragAmount
                        val dist = newOffset.getDistance()
                        val clamped = if (dist > baseRadius) newOffset * (baseRadius / dist) else newOffset
                        thumbOffset = clamped

                        val normX = (clamped.x / baseRadius).toDouble().coerceIn(-1.0, 1.0)
                        val normY = -(clamped.y / baseRadius).toDouble().coerceIn(-1.0, 1.0)
                        viewModel.client.sendControllerStick(stickName, normX, normY)
                    },
                    onDragEnd = {
                        thumbOffset = Offset.Zero
                        viewModel.client.sendControllerStick(stickName, 0.0, 0.0)
                    },
                    onDragCancel = {
                        thumbOffset = Offset.Zero
                        viewModel.client.sendControllerStick(stickName, 0.0, 0.0)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Crosshair guides
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.75f)
                .background(Color(0xFF262638))
        )
        Box(
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth(0.75f)
                .background(Color(0xFF262638))
        )

        // Label
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF44445A),
            letterSpacing = 1.sp
        )

        // Draggable Joystick Knob
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.x.roundToInt(), thumbOffset.y.roundToInt()) }
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentCyan, AccentCyanDim)
                    )
                )
                .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.5f))
            )
        }
    }
}
