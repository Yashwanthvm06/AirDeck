package com.touchdeck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.touchdeck.app.ui.screens.*
import com.touchdeck.app.ui.theme.TouchDeckTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TouchDeckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TouchDeckTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateToMouse = { navController.navigate("mouse") },
                            onNavigateToKeyboard = { navController.navigate("keyboard") },
                            onNavigateToController = { navController.navigate("controller") },
                            onNavigateToMedia = { navController.navigate("media") },
                            onNavigateToSettings = { navController.navigate("settings") },
                            onNavigateToConnect = { navController.navigate("connect") }
                        )
                    }
                    composable("connect") {
                        ConnectScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("mouse") {
                        MouseScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("keyboard") {
                        KeyboardScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("controller") {
                        ControllerScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("media") {
                        MediaScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.client.disconnect()
    }
}
