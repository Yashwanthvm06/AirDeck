package com.touchdeck.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touchdeck.app.TouchDeckViewModel
import com.touchdeck.app.model.ConnectionState
import com.touchdeck.app.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: TouchDeckViewModel,
    onNavigateToMouse: () -> Unit,
    onNavigateToKeyboard: () -> Unit,
    onNavigateToController: () -> Unit,
    onNavigateToMedia: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToConnect: () -> Unit
) {
    val connState by viewModel.client.connectionState.collectAsState()
    val serverName by viewModel.client.serverName.collectAsState()
    val latency by viewModel.client.latencyMs.collectAsState()
    val pairedServer by viewModel.pairedServer.collectAsState()

    val isConnected = connState == ConnectionState.CONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AIRDESK",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = AccentCyan
                )
                Text(
                    text = "PC Controller Suite",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            IconButton(
                onClick = {
                    viewModel.haptics.buttonClick()
                    onNavigateToSettings()
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(BgSurface)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Connection Status Banner
        ConnectionCard(
            connState = connState,
            serverName = if (isConnected) serverName else (pairedServer?.serverName ?: "None"),
            latency = latency,
            hasPairedServer = pairedServer != null,
            onConnectClick = {
                viewModel.haptics.buttonClick()
                if (isConnected) {
                    viewModel.disconnect()
                } else if (pairedServer != null) {
                    viewModel.connectWithSavedServer()
                } else {
                    onNavigateToConnect()
                }
            },
            onSettingsClick = {
                viewModel.haptics.buttonClick()
                onNavigateToConnect()
            }
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "CONTROLLER MODES",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Grid of 4 Modes
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                ModeCard(
                    title = "Touchpad",
                    subtitle = "Precision cursor & gestures",
                    icon = Icons.Default.Mouse,
                    accentColor = AccentCyan,
                    enabled = isConnected,
                    onClick = {
                        viewModel.haptics.buttonClick()
                        onNavigateToMouse()
                    }
                )
            }
            item {
                ModeCard(
                    title = "Keyboard",
                    subtitle = "Full PC keys & input",
                    icon = Icons.Default.Keyboard,
                    accentColor = Color(0xFFB388FF),
                    enabled = isConnected,
                    onClick = {
                        viewModel.haptics.buttonClick()
                        onNavigateToKeyboard()
                    }
                )
            }
            item {
                ModeCard(
                    title = "Gamepad",
                    subtitle = "Virtual console controller",
                    icon = Icons.Default.Gamepad,
                    accentColor = Color(0xFFFF5252),
                    enabled = isConnected,
                    onClick = {
                        viewModel.haptics.buttonClick()
                        onNavigateToController()
                    }
                )
            }
            item {
                ModeCard(
                    title = "Media",
                    subtitle = "Playback & volume remote",
                    icon = Icons.Default.PlayArrow,
                    accentColor = Color(0xFF69F0AE),
                    enabled = isConnected,
                    onClick = {
                        viewModel.haptics.buttonClick()
                        onNavigateToMedia()
                    }
                )
            }
        }

        // Bottom status hint if not connected
        if (!isConnected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgElevated)
                    .clickable {
                        viewModel.haptics.buttonClick()
                        onNavigateToConnect()
                    }
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tap to pair or connect to your Windows PC",
                        fontSize = 13.sp,
                        color = AccentCyan,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    connState: ConnectionState,
    serverName: String,
    latency: Long,
    hasPairedServer: Boolean,
    onConnectClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val isConnected = connState == ConnectionState.CONNECTED
    val isConnecting = connState == ConnectionState.CONNECTING ||
            connState == ConnectionState.PAIRING ||
            connState == ConnectionState.AUTHENTICATING ||
            connState == ConnectionState.RECONNECTING

    val statusColor = when {
        isConnected -> SuccessGreen
        isConnecting -> WarningAmber
        connState == ConnectionState.FAILED -> ErrorRed
        else -> TextDisabled
    }

    val statusText = when (connState) {
        ConnectionState.CONNECTED -> "CONNECTED"
        ConnectionState.CONNECTING -> "CONNECTING..."
        ConnectionState.PAIRING -> "PAIRING..."
        ConnectionState.AUTHENTICATING -> "AUTHENTICATING..."
        ConnectionState.RECONNECTING -> "RECONNECTING..."
        ConnectionState.FAILED -> "CONNECTION FAILED"
        ConnectionState.DISCOVERING -> "SEARCHING..."
        ConnectionState.DISCONNECTED -> "DISCONNECTED"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(BgCard, BgSurface)
                )
            )
            .border(
                width = 1.dp,
                color = if (isConnected) AccentCyanDim.copy(alpha = 0.5f) else Color(0xFF2A2A3A),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = statusColor
                    )
                }

                if (isConnected && latency >= 0) {
                    Text(
                        text = "${latency}ms",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (latency < 25) SuccessGreen else if (latency < 80) WarningAmber else ErrorRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (serverName.isNotBlank() && serverName != "None") serverName else "No PC Selected",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onConnectClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) ErrorRed.copy(alpha = 0.2f) else AccentCyan,
                        contentColor = if (isConnected) ErrorRed else BgDark
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isConnected) "Disconnect" else if (hasPairedServer) "Quick Connect" else "Connect Now",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                OutlinedButton(
                    onClick = onSettingsClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    )
                ) {
                    Text("Pair PC", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) BgCard else BgCard.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = if (enabled) accentColor.copy(alpha = 0.3f) else Color(0xFF222230),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = if (enabled) 0.15f else 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (enabled) accentColor else TextDisabled,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) TextPrimary else TextDisabled
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (enabled) TextSecondary else TextDisabled
                )
            }
        }
    }
}
