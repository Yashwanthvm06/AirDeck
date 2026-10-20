package com.touchdeck.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchdeck.app.TouchDeckViewModel
import com.touchdeck.app.ui.theme.*
import kotlin.math.abs

@Composable
fun KeyboardScreen(
    viewModel: TouchDeckViewModel,
    onBack: () -> Unit
) {
    var textBuffer by remember { mutableStateOf("") }
    var showFunctionRow by remember { mutableStateOf(false) }
    var showShortcuts by remember { mutableStateOf(false) }
    var showFullQwerty by remember { mutableStateOf(true) }

    val settings by viewModel.settings.collectAsState()
    val latency by viewModel.client.latencyMs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
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
                Column {
                    Text(
                        text = "Keyboard & Mousepad",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (latency >= 0) {
                        Text(
                            text = "${latency}ms latency",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (latency < 25) SuccessGreen else WarningAmber
                        )
                    }
                }
            }

            // Quick toggles
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = showFullQwerty,
                    onClick = { showFullQwerty = !showFullQwerty },
                    label = { Text("QWERTY", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCyanDim.copy(alpha = 0.4f),
                        selectedLabelColor = AccentCyan
                    )
                )
                FilterChip(
                    selected = showFunctionRow,
                    onClick = { showFunctionRow = !showFunctionRow },
                    label = { Text("F-Keys", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCyanDim.copy(alpha = 0.4f),
                        selectedLabelColor = AccentCyan
                    )
                )
                FilterChip(
                    selected = showShortcuts,
                    onClick = { showShortcuts = !showShortcuts },
                    label = { Text("Shortcuts", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCyanDim.copy(alpha = 0.4f),
                        selectedLabelColor = AccentCyan
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ==========================================
        // 1. INTEGRATED PRECISION TRACKPAD
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BgCard)
                .border(1.dp, Color(0xFF262638), RoundedCornerShape(14.dp))
        ) {
            // Touch Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
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
                                            if (abs(avgDy) > 0.4) {
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
                // Background visual cue
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PRECISION TRACKPAD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = TextDisabled.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "1-Finger Move • Tap Click • 2-Finger Scroll",
                        fontSize = 10.sp,
                        color = TextDisabled.copy(alpha = 0.4f)
                    )
                }
            }

            // Attached Mouse Buttons (Left & Right Click Bar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(Color(0xFF14141E))
                    .border(width = 1.dp, color = Color(0xFF222230))
            ) {
                TrackpadMouseButton(
                    title = "LEFT CLICK",
                    buttonId = "left",
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF262638)))
                TrackpadMouseButton(
                    title = "RIGHT CLICK",
                    buttonId = "right",
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ==========================================
        // 2. DIRECT TEXT INPUT FIELD
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BgCard)
                .border(1.dp, Color(0xFF2A2A3A), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = textBuffer,
                    onValueChange = { textBuffer = it },
                    placeholder = { Text("Type text to send to PC...", fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (textBuffer.isNotBlank()) {
                                viewModel.haptics.keyboardKey()
                                viewModel.client.sendTextInput(textBuffer)
                                textBuffer = ""
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = Color(0xFF333348)
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (textBuffer.isNotBlank()) {
                            viewModel.haptics.keyboardKey()
                            viewModel.client.sendTextInput(textBuffer)
                            textBuffer = ""
                        }
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentCyan)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = BgDark
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ==========================================
        // 3. COLLAPSIBLE FUNCTION KEYS (F1 - F12)
        // ==========================================
        AnimatedVisibility(
            visible = showFunctionRow,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Text(
                    text = "FUNCTION KEYS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 1..6) {
                        KeyButton(
                            title = "F$i",
                            vkCode = 0x70 + (i - 1),
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f),
                            heightDp = 38
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 7..12) {
                        KeyButton(
                            title = "F$i",
                            vkCode = 0x70 + (i - 1),
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f),
                            heightDp = 38
                        )
                    }
                }
            }
        }

        // ==========================================
        // 4. COLLAPSIBLE WINDOWS SHORTCUTS
        // ==========================================
        AnimatedVisibility(
            visible = showShortcuts,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Text(
                    text = "WINDOWS SHORTCUTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShortcutButton("Ctrl+C", Modifier.weight(1f)) {
                        sendShortcut(viewModel, listOf(0x11, 0x43))
                    }
                    ShortcutButton("Ctrl+V", Modifier.weight(1f)) {
                        sendShortcut(viewModel, listOf(0x11, 0x56))
                    }
                    ShortcutButton("Ctrl+Z", Modifier.weight(1f)) {
                        sendShortcut(viewModel, listOf(0x11, 0x5A))
                    }
                    ShortcutButton("Ctrl+A", Modifier.weight(1f)) {
                        sendShortcut(viewModel, listOf(0x11, 0x41))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShortcutButton("Alt+Tab", Modifier.weight(1f)) {
                        sendShortcut(viewModel, listOf(0x12, 0x09))
                    }
                    ShortcutButton("Win+D", Modifier.weight(1f)) {
                        sendShortcut(viewModel, listOf(0x5B, 0x44))
                    }
                    ShortcutButton("Win+L", Modifier.weight(1f)) {
                        sendShortcut(viewModel, listOf(0x5B, 0x4C))
                    }
                    ShortcutButton("TaskMgr", Modifier.weight(1f)) {
                        sendShortcut(viewModel, listOf(0x11, 0x10, 0x1B))
                    }
                }
            }
        }

        // ==========================================
        // 5. SYSTEM & MODIFIER KEYS (ESC, TAB, WIN, DEL, CTRL, ALT, SHIFT)
        // ==========================================
        Text(
            text = "SYSTEM & MODIFIERS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyButton("ESC", 0x1B, viewModel, Modifier.weight(1f), heightDp = 42)
            KeyButton("TAB", 0x09, viewModel, Modifier.weight(1f), heightDp = 42)
            KeyButton("WIN", 0x5B, viewModel, Modifier.weight(1f), heightDp = 42)
            KeyButton("CTRL", 0x11, viewModel, Modifier.weight(1f), heightDp = 42)
            KeyButton("ALT", 0x12, viewModel, Modifier.weight(1f), heightDp = 42)
            KeyButton("SHIFT", 0x10, viewModel, Modifier.weight(1f), heightDp = 42)
            KeyButton("DEL", 0x2E, viewModel, Modifier.weight(1f), heightDp = 42)
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Action Keys (Backspace, Enter, Space)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeyButton("BKSP", 0x08, viewModel, Modifier.weight(1.2f), heightDp = 44)
            KeyButton("SPACEBAR", 0x20, viewModel, Modifier.weight(2f), heightDp = 44)
            KeyButton("ENTER", 0x0D, viewModel, Modifier.weight(1.4f), highlight = true, heightDp = 44)
        }

        // ==========================================
        // 6. EXPANDABLE QWERTY KEYBOARD
        // ==========================================
        AnimatedVisibility(
            visible = showFullQwerty,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                // Row 1: Q W E R T Y U I O P
                QwertyRow(
                    keys = listOf(
                        "Q" to 0x51, "W" to 0x57, "E" to 0x45, "R" to 0x52, "T" to 0x54,
                        "Y" to 0x59, "U" to 0x55, "I" to 0x49, "O" to 0x4F, "P" to 0x50
                    ),
                    viewModel = viewModel
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Row 2: A S D F G H J K L
                QwertyRow(
                    keys = listOf(
                        "A" to 0x41, "S" to 0x53, "D" to 0x44, "F" to 0x46, "G" to 0x47,
                        "H" to 0x48, "J" to 0x4A, "K" to 0x4B, "L" to 0x4C
                    ),
                    viewModel = viewModel
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Row 3: Z X C V B N M
                QwertyRow(
                    keys = listOf(
                        "Z" to 0x5A, "X" to 0x58, "C" to 0x43, "V" to 0x56, "B" to 0x42,
                        "N" to 0x4E, "M" to 0x4D
                    ),
                    viewModel = viewModel
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ==========================================
        // 7. ARROW NAVIGATION CLUSTER
        // ==========================================
        Text(
            text = "ARROW NAVIGATION",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            KeyButton(
                title = "▲",
                vkCode = 0x26,
                viewModel = viewModel,
                modifier = Modifier.width(80.dp),
                heightDp = 42
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyButton(
                    title = "◀",
                    vkCode = 0x25,
                    viewModel = viewModel,
                    modifier = Modifier.width(80.dp),
                    heightDp = 42
                )
                KeyButton(
                    title = "▼",
                    vkCode = 0x28,
                    viewModel = viewModel,
                    modifier = Modifier.width(80.dp),
                    heightDp = 42
                )
                KeyButton(
                    title = "▶",
                    vkCode = 0x27,
                    viewModel = viewModel,
                    modifier = Modifier.width(80.dp),
                    heightDp = 42
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun QwertyRow(
    keys: List<Pair<String, Int>>,
    viewModel: TouchDeckViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for ((char, vk) in keys) {
            KeyButton(
                title = char,
                vkCode = vk,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
                heightDp = 38
            )
        }
    }
}

@Composable
private fun TrackpadMouseButton(
    title: String,
    buttonId: String,
    viewModel: TouchDeckViewModel,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(if (isPressed) AccentCyanDim.copy(alpha = 0.5f) else Color.Transparent)
            .pointerInput(buttonId) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press -> {
                                if (!isPressed) {
                                    isPressed = true
                                    viewModel.haptics.mouseClick()
                                    viewModel.client.sendMouseButtonDown(buttonId)
                                }
                            }
                            PointerEventType.Release -> {
                                if (isPressed) {
                                    isPressed = false
                                    viewModel.client.sendMouseButtonUp(buttonId)
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
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = if (isPressed) AccentCyan else TextSecondary
        )
    }
}

@Composable
private fun KeyButton(
    title: String,
    vkCode: Int,
    viewModel: TouchDeckViewModel,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    heightDp: Int = 46
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(heightDp.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isPressed) AccentCyan
                else if (highlight) AccentCyanDim.copy(alpha = 0.35f)
                else ButtonSurface
            )
            .border(
                1.dp,
                if (isPressed || highlight) AccentCyan else Color(0xFF333345),
                RoundedCornerShape(6.dp)
            )
            .pointerInput(vkCode) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press -> {
                                if (!isPressed) {
                                    isPressed = true
                                    viewModel.haptics.keyboardKey()
                                    viewModel.client.sendKeyDown(vkCode)
                                }
                            }
                            PointerEventType.Release -> {
                                if (isPressed) {
                                    isPressed = false
                                    viewModel.client.sendKeyUp(vkCode)
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
            fontSize = if (title.length > 3) 10.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (isPressed) BgDark else TextPrimary
        )
    }
}

@Composable
private fun ShortcutButton(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(BgElevated)
            .border(1.dp, Color(0xFF333348), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = AccentCyan
        )
    }
}

private fun sendShortcut(viewModel: TouchDeckViewModel, keys: List<Int>) {
    keys.forEach { viewModel.client.sendKeyDown(it) }
    keys.reversed().forEach { viewModel.client.sendKeyUp(it) }
}
