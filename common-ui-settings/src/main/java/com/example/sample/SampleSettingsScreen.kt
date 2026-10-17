package com.example.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
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
                    ItemScreenScaffold(
                        screen = screen,
                        canNavigateBack = screen.route != SettingItems.route,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemScreenScaffold(
    screen: ItemScreen,
    canNavigateBack: Boolean,
    onNavigateBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canNavigateBack) {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Column(modifier = Modifier.padding(start = if (canNavigateBack) 4.dp else 12.dp)) {
                    Text(
                        text = screen.title,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Route: ${screen.route}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            HorizontalDivider()
            DrawTree(tree = screen.tree)
        }
    }
}
