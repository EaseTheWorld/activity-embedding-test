package com.example.uifw.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
                Column(modifier = modifier.fillMaxWidth()) {
                    if (!item.key.endsWith("/root")) {
                        Text(
                            text = "📁 [Column: ${item.key}]",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                    if (item.orientation == Orientation.VERTICAL) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            item.children.forEach { child -> DrawItem(child) }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item.children.forEach { child -> DrawItem(child) }
                        }
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
                    itemKey = item.key,
                    isChecked = isChecked,
                    isEnabled = isEnabled,
                    onToggleChange = controller::onToggleChange,
                    modifier = modifier
                )
            }

            is UiTextItem -> {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(text = item.text, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Key: ${item.key}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is UiSubScreenEntryItem -> {
                val navigator = LocalItemNavigator.current
                Row(
                    modifier = modifier
                        .fillMaxWidth()
                        .clickable { navigator(item.targetScreen) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Key: ${item.key}  ➔  Target: ${item.targetScreen.route}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun RenderToggleItem(
    title: String,
    itemKey: String,
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
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Key: $itemKey",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isChecked,
            enabled = isEnabled,
            onCheckedChange = onToggleChange
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
