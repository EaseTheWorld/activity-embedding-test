@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.uifw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uifw.item.*
import com.example.uifw.controller.LocalItemControllerRegistry
import com.example.uifw.controller.ToggleController

val LocalItemNavigator = staticCompositionLocalOf<(ItemScreen) -> Unit> {
    error("No ItemNavigator provided")
}

val LocalRouteNavigator = staticCompositionLocalOf<((String) -> Unit)?> { null }

val LocalHighlightKey = staticCompositionLocalOf<String?> { null }

@Composable
fun DrawTree(
    tree: ItemTree,
    modifier: Modifier = Modifier,
    customContent: @Composable (customItem: UiCustomItem) -> Unit = {}
) {
    DrawItem(item = tree.root, modifier = modifier, customContent = customContent)
}

@Composable
private fun DrawItem(
    item: UiItem,
    modifier: Modifier = Modifier,
    customContent: @Composable (UiCustomItem) -> Unit
) {
    key(item.key) {
        when (item) {
            is UiSectionItem -> {
                val isHighlighted = LocalHighlightKey.current == item.key
                val bringIntoViewRequester = remember { BringIntoViewRequester() }
                LaunchedEffect(isHighlighted) {
                    if (isHighlighted) {
                        bringIntoViewRequester.bringIntoView()
                    }
                }

                Card(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .then(
                            if (isHighlighted) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            } else {
                                Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isHighlighted) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "📁 ${item.title}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isHighlighted) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "🎯 TARGET DEEPLINK",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Section Key: ${item.key}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(4.dp))
                        item.children.forEach { child ->
                            DrawItem(child, customContent = customContent)
                        }
                    }
                }
            }

            is UiLinearItem -> {
                Column(modifier = modifier.fillMaxWidth()) {
                    if (!item.key.endsWith("/root")) {
                        Text(
                            text = "📁 [Container: ${item.key}]",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                    if (item.orientation == Orientation.VERTICAL) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            item.children.forEach { child -> DrawItem(child, customContent = customContent) }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item.children.forEach { child -> DrawItem(child, customContent = customContent) }
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
                val isHighlighted = LocalHighlightKey.current == item.key

                RenderToggleItem(
                    title = item.title,
                    itemKey = item.key,
                    isChecked = isChecked,
                    isEnabled = isEnabled,
                    isHighlighted = isHighlighted,
                    onToggleChange = controller::onToggleChange,
                    modifier = modifier
                )
            }

            is UiTextItem -> {
                val isHighlighted = LocalHighlightKey.current == item.key
                val bringIntoViewRequester = remember { BringIntoViewRequester() }
                LaunchedEffect(isHighlighted) {
                    if (isHighlighted) {
                        bringIntoViewRequester.bringIntoView()
                    }
                }

                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .then(
                            if (isHighlighted) {
                                Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = item.text, style = MaterialTheme.typography.bodyLarge)
                        if (isHighlighted) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "🎯 TARGET DEEPLINK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Key: ${item.key}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            is UiSubScreenEntryItem -> {
                val navigator = LocalItemNavigator.current
                val routeNavigator = LocalRouteNavigator.current
                val isHighlighted = LocalHighlightKey.current == item.key
                val controller = LocalItemControllerRegistry.current.getOrNull<com.example.uifw.controller.EntryController>(item)
                val bringIntoViewRequester = remember { BringIntoViewRequester() }
                LaunchedEffect(isHighlighted) {
                    if (isHighlighted) {
                        bringIntoViewRequester.bringIntoView()
                    }
                }

                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .then(
                            if (isHighlighted) {
                                Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (controller != null) {
                                    if (routeNavigator != null) {
                                        controller.onNavigate(
                                            screenNavigator = navigator,
                                            routeNavigator = routeNavigator
                                        )
                                    } else {
                                        controller.onClick(navigator)
                                    }
                                } else if (item.targetScreen != null) {
                                    navigator(item.targetScreen)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
                                if (isHighlighted) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "🎯 TARGET DEEPLINK",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            val targetDesc = item.targetScreen?.route ?: "Dynamic (Controller)"
                            Text(
                                text = "Key: ${item.key}  ➔  Target: $targetDesc",
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

            is UiCustomItem -> {
                val isHighlighted = LocalHighlightKey.current == item.key
                val bringIntoViewRequester = remember { BringIntoViewRequester() }
                LaunchedEffect(isHighlighted) {
                    if (isHighlighted) {
                        bringIntoViewRequester.bringIntoView()
                    }
                }

                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .then(
                            if (isHighlighted) {
                                Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isHighlighted) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "🎯 TARGET DEEPLINK",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        customContent(item)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            else -> {
                Text(
                    text = "Unhandled item: ${item.key}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = modifier.padding(16.dp)
                )
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
    isHighlighted: Boolean,
    onToggleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .then(
                if (isHighlighted) {
                    Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            RoundedCornerShape(8.dp)
                        )
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, style = MaterialTheme.typography.bodyLarge)
                    if (isHighlighted) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "🎯 TARGET DEEPLINK",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
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
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
