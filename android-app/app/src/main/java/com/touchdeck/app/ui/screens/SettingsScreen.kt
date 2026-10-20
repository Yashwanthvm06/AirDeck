package com.touchdeck.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchdeck.app.TouchDeckViewModel
import com.touchdeck.app.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: TouchDeckViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val pairedServer by viewModel.pairedServer.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
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
                text = "Preferences",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Touch & Cursor Section
        SectionHeader("TOUCHPAD & CURSOR")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BgCard)
                .border(1.dp, Color(0xFF2A2A38), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cursor Sensitivity",
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "%.1fx".format(settings.cursorSensitivity),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }
                Slider(
                    value = settings.cursorSensitivity,
                    onValueChange = {
                        viewModel.updateSettings(settings.copy(cursorSensitivity = it))
                    },
                    valueRange = 0.5f..3.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = Color(0xFF333348)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scroll Speed",
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "%.1fx".format(settings.scrollSpeed),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }
                Slider(
                    value = settings.scrollSpeed,
                    onValueChange = {
                        viewModel.updateSettings(settings.copy(scrollSpeed = it))
                    },
                    valueRange = 0.5f..3.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = Color(0xFF333348)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Tap to Click", fontSize = 14.sp, color = TextPrimary)
                        Text(text = "Tap anywhere on touchpad for left click", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = settings.tapToClick,
                        onCheckedChange = {
                            viewModel.updateSettings(settings.copy(tapToClick = it))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDark,
                            checkedTrackColor = AccentCyan
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Haptic Feedback Section
        SectionHeader("HAPTIC FEEDBACK")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BgCard)
                .border(1.dp, Color(0xFF2A2A38), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Master Haptics", fontSize = 14.sp, color = TextPrimary)
                        Text(text = "Vibrations for clicks and interactions", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = settings.hapticEnabled,
                        onCheckedChange = {
                            viewModel.updateSettings(settings.copy(hapticEnabled = it))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDark,
                            checkedTrackColor = AccentCyan
                        )
                    )
                }

                if (settings.hapticEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Intensity", fontSize = 14.sp, color = TextPrimary)
                        Text(
                            text = "${(settings.hapticIntensity * 100).toInt()}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    }
                    Slider(
                        value = settings.hapticIntensity,
                        onValueChange = {
                            viewModel.updateSettings(settings.copy(hapticIntensity = it))
                        },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCyan,
                            activeTrackColor = AccentCyan,
                            inactiveTrackColor = Color(0xFF333348)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Haptic on Keyboard Keys", fontSize = 14.sp, color = TextPrimary)
                        Switch(
                            checked = settings.hapticOnKeyboard,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(hapticOnKeyboard = it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BgDark,
                                checkedTrackColor = AccentCyan
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Haptic on Gamepad Buttons", fontSize = 14.sp, color = TextPrimary)
                        Switch(
                            checked = settings.hapticOnController,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(hapticOnController = it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BgDark,
                                checkedTrackColor = AccentCyan
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Gamepad & Controller Section
        SectionHeader("GAMEPAD & VIRTUAL CONTROLLER")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BgCard)
                .border(1.dp, Color(0xFF2A2A38), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ABXY / △○✕□ Button Size",
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    val label = when {
                        settings.faceButtonScale <= 0.85f -> "Small (80%)"
                        settings.faceButtonScale <= 1.05f -> "Default (100%)"
                        settings.faceButtonScale <= 1.25f -> "Large (120%)"
                        else -> "Extra Large (140%)"
                    }
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Slider(
                    value = settings.faceButtonScale,
                    onValueChange = {
                        viewModel.updateSettings(settings.copy(faceButtonScale = it))
                    },
                    valueRange = 0.8f..1.4f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = Color(0xFF333348)
                    )
                )

                Text(
                    text = "Adjusts the touch diameter and spacing of the Y, A, X, B action buttons on the virtual controller screen.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Device Identity Section
        SectionHeader("DEVICE IDENTITY")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BgCard)
                .border(1.dp, Color(0xFF2A2A38), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Device Name", fontSize = 13.sp, color = TextSecondary)
                    Text(viewModel.deviceName, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Client ID", fontSize = 13.sp, color = TextSecondary)
                    Text(
                        viewModel.deviceId.take(8) + "...",
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("App Version", fontSize = 13.sp, color = TextSecondary)
                    Text("1.0.0 (Build 1)", fontSize = 13.sp, color = AccentCyan)
                }
                Spacer(modifier = Modifier.height(10.dp))

                var showPrivacyDialog by remember { mutableStateOf(false) }

                TextButton(
                    onClick = {
                        viewModel.haptics.buttonClick()
                        showPrivacyDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Privacy Policy & Security", color = AccentCyan, fontSize = 13.sp)
                }

                if (showPrivacyDialog) {
                    AlertDialog(
                        onDismissRequest = { showPrivacyDialog = false },
                        containerColor = BgCard,
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = AccentCyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Privacy Policy", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "Privacy & Data Handling:\n\n" +
                                            "• Local Network Only: AirDesk connects exclusively to your PC Companion over your local Wi-Fi. No tracking SDKs, cloud backends, or third-party analytics are used.\n\n" +
                                            "• Data Transmitted: Device name and a randomly generated device ID are transmitted locally to your PC to identify your device in the companion window. Input commands (keystrokes, mouse deltas, gamepad buttons) are processed directly in real time on your PC and are never logged or stored.\n\n" +
                                            "• Credential Protection: Authentication tokens are stored exclusively in hardware-backed Android Keystore encrypted storage (AES-256-GCM) and verified via HMAC-SHA256 challenge-response.\n\n" +
                                            "• Permissions: Internet and Vibrate permissions are used solely for local communication and haptic feedback.",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    lineHeight = 18.sp
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showPrivacyDialog = false }) {
                                Text("Close", color = AccentCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (pairedServer != null) {
            OutlinedButton(
                onClick = {
                    viewModel.haptics.buttonClick()
                    viewModel.forgetPairedServer()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Forget Paired PC (${pairedServer?.serverName})")
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = TextSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))
}
