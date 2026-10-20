package com.touchdeck.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchdeck.app.TouchDeckViewModel
import com.touchdeck.app.ui.theme.*

@Composable
fun MediaScreen(
    viewModel: TouchDeckViewModel,
    onBack: () -> Unit
) {
    val latency by viewModel.client.latencyMs.collectAsState()

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
                    text = "Media & Remote",
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

        Spacer(modifier = Modifier.height(20.dp))

        // Playback Remote Card
        Text(
            text = "MEDIA PLAYBACK",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .border(1.dp, Color(0xFF2A2A3A), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Large Play/Pause & Skip Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prev
                    MediaCircularButton(
                        icon = Icons.Default.SkipPrevious,
                        size = 54.dp,
                        iconSize = 28.dp,
                        onClick = {
                            viewModel.haptics.buttonClick()
                            viewModel.client.sendMediaAction("prev")
                        }
                    )

                    // Big Play/Pause
                    MediaCircularButton(
                        icon = Icons.Default.PlayArrow,
                        size = 72.dp,
                        iconSize = 38.dp,
                        isPrimary = true,
                        onClick = {
                            viewModel.haptics.buttonClick()
                            viewModel.client.sendMediaAction("play_pause")
                        }
                    )

                    // Next
                    MediaCircularButton(
                        icon = Icons.Default.SkipNext,
                        size = 54.dp,
                        iconSize = 28.dp,
                        onClick = {
                            viewModel.haptics.buttonClick()
                            viewModel.client.sendMediaAction("next")
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stop button
                OutlinedButton(
                    onClick = {
                        viewModel.haptics.buttonClick()
                        viewModel.client.sendMediaAction("stop")
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop Playback", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Volume Controls
        Text(
            text = "VOLUME & AUDIO",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MediaTileButton(
                title = "MUTE",
                icon = Icons.AutoMirrored.Filled.VolumeOff,
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.haptics.buttonClick()
                    viewModel.client.sendMediaAction("mute")
                }
            )
            MediaTileButton(
                title = "VOL -",
                icon = Icons.AutoMirrored.Filled.VolumeDown,
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.haptics.buttonClick()
                    viewModel.client.sendMediaAction("vol_down")
                }
            )
            MediaTileButton(
                title = "VOL +",
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.haptics.buttonClick()
                    viewModel.client.sendMediaAction("vol_up")
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Presentation Remote Mode
        Text(
            text = "PRESENTATION SLIDE REMOTE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .border(1.dp, Color(0xFF2A2A3A), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.haptics.buttonClick()
                            viewModel.client.sendKeyDown(0x21) // Page Up
                            viewModel.client.sendKeyUp(0x21)
                        },
                        modifier = Modifier.weight(1f).height(65.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ButtonSurface, contentColor = TextPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("◀", fontSize = 18.sp, color = AccentCyan)
                            Text("PREV SLIDE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.haptics.buttonClick()
                            viewModel.client.sendKeyDown(0x22) // Page Down
                            viewModel.client.sendKeyUp(0x22)
                        },
                        modifier = Modifier.weight(1f).height(65.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("▶", fontSize = 18.sp)
                            Text("NEXT SLIDE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.haptics.buttonClick()
                            viewModel.client.sendKeyDown(0x74) // F5 (Start Slideshow)
                            viewModel.client.sendKeyUp(0x74)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("Start (F5)", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.haptics.buttonClick()
                            viewModel.client.sendKeyDown(0x1B) // Escape (End Show)
                            viewModel.client.sendKeyUp(0x1B)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("Exit Show", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.haptics.buttonClick()
                            viewModel.client.sendKeyDown(0x42) // 'B' (Black Screen)
                            viewModel.client.sendKeyUp(0x42)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("Blank", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaCircularButton(
    icon: ImageVector,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isPrimary) AccentCyan else ButtonSurface)
            .border(1.dp, if (isPrimary) AccentCyan else Color(0xFF333348), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isPrimary) BgDark else TextPrimary,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun MediaTileButton(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, Color(0xFF2A2A38), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = AccentCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}
