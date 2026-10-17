package com.example.uifw.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uifw.node.*
import com.example.uifw.vm.LocalNodeViewModelRegistry
import com.example.uifw.vm.TextController
import com.example.uifw.vm.ToggleController

val LocalNodeNavigator = staticCompositionLocalOf<(NodeScreen) -> Unit> {
    error("No NodeNavigator provided")
}

@Composable
fun DrawTree(
    tree: NodeTree,
    modifier: Modifier = Modifier
) {
    DrawNode(node = tree.root, modifier = modifier)
}

@Composable
private fun DrawNode(
    node: UiNode,
    modifier: Modifier = Modifier
) {
    key(node.key) {
        when (node) {
            is UiLinearNode -> {
                if (node.orientation == Orientation.VERTICAL) {
                    Column(modifier = modifier) {
                        node.children.forEach { child -> DrawNode(child) }
                    }
                } else {
                    Row(
                        modifier = modifier,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        node.children.forEach { child -> DrawNode(child) }
                    }
                }
            }

            is UiToggleNode -> {
                val registry = LocalNodeViewModelRegistry.current
                val controller = remember(node.key) {
                    registry.getOrCreate<ToggleController>(node)
                }
                val isChecked by controller.isChecked.collectAsStateWithLifecycle()
                val isEnabled by controller.isEnabled.collectAsStateWithLifecycle()

                RenderToggleNode(
                    title = node.title,
                    isChecked = isChecked,
                    isEnabled = isEnabled,
                    onToggleChange = controller::onToggleChange,
                    modifier = modifier
                )
            }

            is UiTextNode -> {
                Text(
                    text = node.text,
                    modifier = modifier.padding(16.dp)
                )
            }

            is UiSubScreenEntryNode -> {
                val navigator = LocalNodeNavigator.current
                Row(
                    modifier = modifier
                        .fillMaxWidth()
                        .clickable { navigator(node.targetScreen) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = node.title)
                    Text(text = ">")
                }
            }
        }
    }
}

@Composable
private fun RenderToggleNode(
    title: String,
    isChecked: Boolean,
    isEnabled: Boolean,
    onToggleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title)
        Switch(
            checked = isChecked,
            enabled = isEnabled,
            onCheckedChange = onToggleChange
        )
    }
}
