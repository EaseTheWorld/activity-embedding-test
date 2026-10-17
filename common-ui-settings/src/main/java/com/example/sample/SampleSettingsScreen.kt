package com.example.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.uifw.item.ItemScreen
import com.example.uifw.ui.DrawTree
import com.example.uifw.ui.LocalItemNavigator
import com.example.uifw.controller.LocalItemControllerRegistry
import com.example.uifw.controller.ScreenHostViewModel

@Composable
fun SampleSettingsScreen(
    hostViewModel: ScreenHostViewModel = hiltViewModel()
) {
    val navController = rememberNavController()

    CompositionLocalProvider(
        LocalItemControllerRegistry provides hostViewModel.itemRegistry,
        LocalItemNavigator provides { targetScreen ->
            navController.navigate(targetScreen.route)
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = SettingItems.route
        ) {
            val screens = listOf(
                SettingItems,
                SettingItems.Bluetooth,
                SettingItems.Bluetooth.Advanced,
                SettingItems.Wifi
            )

            screens.forEach { screen ->
                composable(route = screen.route) {
                    ItemScreenScaffold(screen = screen)
                }
            }
        }
    }
}

@Composable
private fun ItemScreenScaffold(screen: ItemScreen) {
    Column {
        Text(
            text = screen.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )
        DrawTree(tree = screen.tree)
    }
}
