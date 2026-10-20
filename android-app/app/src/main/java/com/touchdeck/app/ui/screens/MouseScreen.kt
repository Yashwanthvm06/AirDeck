package com.touchdeck.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchdeck.app.TouchDeckViewModel
import com.touchdeck.app.ui.theme.*

@Composable
fun MouseScreen(
    viewModel: TouchDeckViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val latency by viewModel.client.latencyMs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        viewModel.haptics.buttonClick()
                        onBack()
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Touchpad",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            if (latency >= 0) {
                Text(
                    text = "${latency}ms",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (latency < 25) SuccessGreen else WarningAmber
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Touchpad Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .border(1.dp, Color(0xFF2A2A38), RoundedCornerShape(16.dp))
                .pointerInput(settings) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            val pointerCount = changes.size

                            when (pointerCount) {
                                1 -> {
                                    val change = changes[0]
                                    if (change.pressed) {
                                        val dx = (change.position.x - change.previousPosition.x) * settings.cursorSensitivity
                                        val dy = (change.position.y - change.previousPosition.y) * settings.cursorSensitivity
                                        if (dx != 0f || dy != 0f) {
                                            viewModel.client.sendMouseMove(dx.toDouble(), dy.toDouble())
                                        }
                                        change.consume()
                                    }
                                }
                                2 -> {
                                    // 2-finger scroll
                                    val c1 = changes[0]
                                    val c2 = changes[1]
                                    if (c1.pressed && c2.pressed) {
                                        val dy1 = c1.position.y - c1.previousPosition.y
                                        val dy2 = c2.position.y - c2.previousPosition.y
                                        val avgDy = (dy1 + dy2) / 2.0 * settings.scrollSpeed
                                        if (kotlin.math.abs(avgDy) > 0.5) {
                                            viewModel.client.sendMouseScroll(0.0, avgDy * 2.0)
                                        }
                                        c1.consume()
                                        c2.consume()
                                    }
                                }
                            }
                        }
                    }
                }
                .pointerInput(settings) {
                    detectTapGestures(
                        onTap = {
                            if (settings.tapToClick) {
                                viewModel.haptics.mouseClick()
                                viewModel.client.sendMouseButtonDown("left")
                                viewModel.client.sendMouseButtonUp("left")
                            }
                        },
                        onDoubleTap = {
                            viewModel.haptics.mouseClick()
                            viewModel.client.sendMouseButtonDown("left")
                            viewModel.client.sendMouseButtonUp("left")
                            viewModel.client.sendMouseButtonDown("left")
                            viewModel.client.sendMouseButtonUp("left")
                        },
                        onLongPress = {
                            viewModel.haptics.mouseClick()
                            viewModel.client.sendMouseButtonDown("right")
                            viewModel.client.sendMouseButtonUp("right")
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PRECISION TRACKPAD",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = TextDisabled.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "1 finger: Move & Tap  •  2 fingers: Scroll  •  Long press: Right-click",
                    fontSize = 11.sp,
                    color = TextDisabled.copy(alpha = 0.4f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Physical Click Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Button
            MouseButton(
                modifier = Modifier.weight(1.4f),
                title = "LEFT CLICK",
                onDown = {
                    viewModel.haptics.mouseClick()
                    viewModel.client.sendMouseButtonDown("left")
                },
                onUp = {
                    viewModel.client.sendMouseButtonUp("left")
                }
            )

            // Middle Button / Scroll
            MouseButton(
                modifier = Modifier.weight(0.8f),
                title = "MIDDLE",
                onDown = {
                    viewModel.haptics.mouseClick()
                    viewModel.client.sendMouseButtonDown("middle")
                },
                onUp = {
                    viewModel.client.sendMouseButtonUp("middle")
                }
            )

            // Right Button
            MouseButton(
                modifier = Modifier.weight(1.4f),
                title = "RIGHT CLICK",
                onDown = {
                    viewModel.haptics.mouseClick()
                    viewModel.client.sendMouseButtonDown("right")
                },
                onUp = {
                    viewModel.client.sendMouseButtonUp("right")
                }
            )
        }
    }
}

@Composable
private fun MouseButton(
    modifier: Modifier = Modifier,
    title: String,
    onDown: () -> Unit,
    onUp: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            onDown()
        } else {
            onUp()
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPressed) AccentCyanDim else ButtonSurface)
            .border(
                1.dp,
                if (isPressed) AccentCyan else Color(0xFF333348),
                RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = if (isPressed) BgDark else TextPrimary
        )
    }
}
