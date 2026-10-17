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
import com.example.uifw.item.*
import com.example.uifw.controller.LocalItemControllerRegistry
import com.example.uifw.controller.TextController
import com.example.uifw.controller.ToggleController

val LocalItemNavigator = staticCompositionLocalOf<(ItemScreen) -> Unit> {
    error("No ItemNavigator provided")
}

@Composable
fun DrawTree(
    tree: ItemTree,
    modifier: Modifier = Modifier
) {
    DrawItem(item = tree.root, modifier = modifier)
}

@Composable
private fun DrawItem(
    item: UiItem,
    modifier: Modifier = Modifier
) {
    key(item.key) {
        when (item) {
            is UiLinearItem -> {
                if (item.orientation == Orientation.VERTICAL) {
                    Column(modifier = modifier) {
                        item.children.forEach { child -> DrawItem(child) }
                    }
                } else {
                    Row(
                        modifier = modifier,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item.children.forEach { child -> DrawItem(child) }
                    }
                }
            }

            is UiToggleItem -> {
                val registry = LocalItemControllerRegistry.current
                val controller = remember(item.key) {
                    registry.getOrCreate<ToggleController>(item)
                }
                val isChecked by controller.isChecked.collectAsStateWithLifecycle()
                val isEnabled by controller.isEnabled.collectAsStateWithLifecycle()

                RenderToggleItem(
                    title = item.title,
                    isChecked = isChecked,
                    isEnabled = isEnabled,
                    onToggleChange = controller::onToggleChange,
                    modifier = modifier
                )
            }

            is UiTextItem -> {
                Text(
                    text = item.text,
                    modifier = modifier.padding(16.dp)
                )
            }

            is UiSubScreenEntryItem -> {
                val navigator = LocalItemNavigator.current
                Row(
                    modifier = modifier
                        .fillMaxWidth()
                        .clickable { navigator(item.targetScreen) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = item.title)
                    Text(text = ">")
                }
            }
        }
    }
}

@Composable
private fun RenderToggleItem(
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
