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
import com.example.uifw.node.NodeScreen
import com.example.uifw.ui.DrawTree
import com.example.uifw.ui.LocalNodeNavigator
import com.example.uifw.vm.LocalNodeViewModelRegistry
import com.example.uifw.vm.ScreenHostViewModel

@Composable
fun SampleSettingsScreen(
    hostViewModel: ScreenHostViewModel = hiltViewModel()
) {
    val navController = rememberNavController()

    CompositionLocalProvider(
        LocalNodeViewModelRegistry provides hostViewModel.nodeRegistry,
        LocalNodeNavigator provides { targetScreen ->
            navController.navigate(targetScreen.route)
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = SettingNodes.route
        ) {
            // 타입 안전한 route 참조로 SubScreen 등록
            val screens = listOf(
                SettingNodes,
                SettingNodes.Bluetooth,
                SettingNodes.Bluetooth.Advanced,
                SettingNodes.Wifi
            )

            screens.forEach { screen ->
                composable(route = screen.route) {
                    NodeScreenScaffold(screen = screen)
                }
            }
        }
    }
}

@Composable
private fun NodeScreenScaffold(screen: NodeScreen) {
    Column {
        Text(
            text = screen.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )
        DrawTree(tree = screen.tree)
    }
}
