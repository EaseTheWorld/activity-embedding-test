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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uifw.controller.EntryController
import com.example.uifw.controller.ItemNavigator
import com.example.uifw.controller.LocalItemControllerRegistry
import com.example.uifw.controller.ToggleController
import com.example.uifw.item.*

val LocalItemNavigator = staticCompositionLocalOf<ItemNavigator> {
    error("No ItemNavigator provided")
}

val LocalHighlightKey = compositionLocalOf<String?> { null }

/**
 * Common Item과 Custom Item 구분 없이 모든 UiItem을 ItemRendererRegistry를 통해 동일한 파이프라인으로 렌더링합니다.
 * - 고유 key(item.key) 스코핑
 * - DeepLink 하이라이트(LocalHighlightKey) 판별
 * - BringIntoViewRequester 자동 스크롤(Anchoring)
 */
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
    val rendererRegistry = LocalItemRendererRegistry.current

    key(item.key) {
        val isHighlighted = LocalHighlightKey.current == item.key
        val bringIntoViewRequester = remember { BringIntoViewRequester() }
        LaunchedEffect(isHighlighted) {
            if (isHighlighted) {
                bringIntoViewRequester.bringIntoView()
            }
        }

        val anchoredModifier = modifier.bringIntoViewRequester(bringIntoViewRequester)
        val renderer = rendererRegistry.getOrNull(item)

        if (renderer != null) {
            renderer.Render(
                item = item,
                isHighlighted = isHighlighted,
                modifier = anchoredModifier,
                renderChild = { child -> DrawItem(item = child) }
            )
        } else {
            Text(
                text = "No ItemRenderer registered for ${item::class.simpleName} (key: ${item.key})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = anchoredModifier.padding(16.dp)
            )
        }
    }
}

/**
 * 공통 DeepLink 하이라이트 배지 ("🎯 TARGET DEEPLINK")
 */
@Composable
fun DeepLinkTargetBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = "🎯 TARGET DEEPLINK",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Leaf Item(Toggle, Text, Entry, CustomItem 등)에 공통 적용 가능한 하이라이트 테두리/배경 Modifier 확장 함수
 */
@Composable
fun Modifier.deepLinkHighlightBox(isHighlighted: Boolean): Modifier =
    if (isHighlighted) {
        this
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                RoundedCornerShape(8.dp)
            )
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
    } else {
        this
    }

// ── Built-in Common Item Renderers ───────────────────────────────────────────

object SectionItemRenderer : ItemRenderer<UiSectionItem>(UiSectionItem::class) {
    @Composable
    override fun Render(
        item: UiSectionItem,
        isHighlighted: Boolean,
        modifier: Modifier,
        renderChild: @Composable (UiItem) -> Unit
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .then(
                    if (isHighlighted) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    } else {
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
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
                        DeepLinkTargetBadge()
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
                    renderChild(child)
                }
            }
        }
    }
}

object LinearItemRenderer : ItemRenderer<UiLinearItem>(UiLinearItem::class) {
    @Composable
    override fun Render(
        item: UiLinearItem,
        isHighlighted: Boolean,
        modifier: Modifier,
        renderChild: @Composable (UiItem) -> Unit
    ) {
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
                    item.children.forEach { child -> renderChild(child) }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item.children.forEach { child -> renderChild(child) }
                }
            }
        }
    }
}

object ToggleItemRenderer : ItemRenderer<UiToggleItem>(UiToggleItem::class) {
    @Composable
    override fun Render(
        item: UiToggleItem,
        isHighlighted: Boolean,
        modifier: Modifier
    ) {
        val registry = LocalItemControllerRegistry.current
        val controller = remember(item.key) {
            registry.getOrCreate<ToggleController>(item)
        }
        val isChecked by controller.isChecked.collectAsStateWithLifecycle()
        val isEnabled by controller.isEnabled.collectAsStateWithLifecycle()

        Box(
            modifier = modifier
                .fillMaxWidth()
                .deepLinkHighlightBox(isHighlighted)
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
                        Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
                        if (isHighlighted) {
                            Spacer(modifier = Modifier.width(8.dp))
                            DeepLinkTargetBadge()
                        }
                    }
                    Text(
                        text = "Key: ${item.key}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isChecked,
                    enabled = isEnabled,
                    onCheckedChange = controller::onToggleChange
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

object TextItemRenderer : ItemRenderer<UiTextItem>(UiTextItem::class) {
    @Composable
    override fun Render(
        item: UiTextItem,
        isHighlighted: Boolean,
        modifier: Modifier
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .deepLinkHighlightBox(isHighlighted)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = item.text, style = MaterialTheme.typography.bodyLarge)
                if (isHighlighted) {
                    DeepLinkTargetBadge()
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
}

object SubScreenEntryItemRenderer : ItemRenderer<UiSubScreenEntryItem>(UiSubScreenEntryItem::class) {
    @Composable
    override fun Render(
        item: UiSubScreenEntryItem,
        isHighlighted: Boolean,
        modifier: Modifier
    ) {
        val navigator = LocalItemNavigator.current
        val controller = LocalItemControllerRegistry.current.getOrNull<EntryController>(item)

        Column(
            modifier = modifier
                .fillMaxWidth()
                .deepLinkHighlightBox(isHighlighted)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (controller != null) {
                            controller.onClick(navigator)
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
                            DeepLinkTargetBadge()
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
}

object DefaultItemRenderers {
    val all: List<ItemRenderer<*>> = listOf(
        SectionItemRenderer,
        LinearItemRenderer,
        ToggleItemRenderer,
        TextItemRenderer,
        SubScreenEntryItemRenderer
    )
}
