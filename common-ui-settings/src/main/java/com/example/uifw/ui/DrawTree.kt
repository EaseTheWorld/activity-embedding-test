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
import com.example.uifw.controller.*
import com.example.uifw.item.*

val LocalItemNavigator = staticCompositionLocalOf<ItemNavigator> {
    error("No ItemNavigator provided")
}

val LocalHighlightKey = compositionLocalOf<String?> { null }

/**
 * 모든 UiItem에 대해:
 * 1) Controller에서 동적 ItemUiState(value, isVisible, isEnabled)를 구독 (없는 정적 노드는 기본 UiState 생성)
 * 2) isVisible == false 인 경우 즉시 렌더링 생략
 * 3) ItemUiState의 클래스 타입(uiState::class) 기준으로 ItemRendererRegistry에서 렌더러 조회
 * 4) 렌더러에 순수 `state`와 `controller::onValueChanged` 람다를 분리하여 전달
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
    val controllerRegistry = LocalItemControllerRegistry.current
    val rendererRegistry = LocalItemRendererRegistry.current

    key(item.key) {
        val controller = remember(item.key) {
            controllerRegistry.getOrNull<ItemController>(item)
        }

        val uiState: ItemUiState = if (controller != null) {
            val collected by controller.uiState.collectAsStateWithLifecycle()
            collected
        } else {
            remember(item) { createDefaultUiState(item) }
        }

        // 1. 공통 가시성(isVisible) 제어: false이면 하위 트리 및 자신을 렌더링하지 않음
        if (!uiState.isVisible) return@key

        // 2. 공통 DeepLink 하이라이트 및 자동 스크롤(Anchoring)
        val isHighlighted = LocalHighlightKey.current == item.key
        val bringIntoViewRequester = remember { BringIntoViewRequester() }
        LaunchedEffect(isHighlighted) {
            if (isHighlighted) {
                bringIntoViewRequester.bringIntoView()
            }
        }

        val anchoredModifier = modifier.bringIntoViewRequester(bringIntoViewRequester)

        // 3. UiState 타입 기준으로 ItemRenderer 조회 및 controller::onValueChanged 바인딩
        val renderer = rendererRegistry.getOrNull(uiState)
        val onValueChanged: (Any?) -> Unit = remember(controller, item) {
            if (controller != null) {
                val callback: (Any?) -> Unit = controller::onValueChanged
                callback
            } else {
                val fallback: (Any?) -> Unit = { value ->
                    if (value is ItemNavigator && item is UiSubScreenEntryItem && item.targetScreen != null) {
                        value(item.targetScreen)
                    }
                }
                fallback
            }
        }

        if (renderer != null) {
            renderer.Render(
                state = uiState,
                onValueChanged = onValueChanged,
                isHighlighted = isHighlighted,
                modifier = anchoredModifier,
                renderChild = { child -> DrawItem(item = child) }
            )
        } else {
            Text(
                text = "No ItemRenderer registered for UiState ${uiState::class.simpleName} (key: ${item.key})",
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

// ── Built-in Common UiState Renderers ────────────────────────────────────────

object SectionItemRenderer : ItemRenderer<SectionUiState, Unit>(SectionUiState::class) {
    @Composable
    override fun Render(
        state: SectionUiState,
        onValueChanged: (Unit) -> Unit,
        isHighlighted: Boolean,
        modifier: Modifier,
        renderChild: @Composable (UiItem) -> Unit
    ) {
        val item = state.item
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
                        text = "📁 ${state.title}",
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

object LinearItemRenderer : ItemRenderer<LinearUiState, Unit>(LinearUiState::class) {
    @Composable
    override fun Render(
        state: LinearUiState,
        onValueChanged: (Unit) -> Unit,
        isHighlighted: Boolean,
        modifier: Modifier,
        renderChild: @Composable (UiItem) -> Unit
    ) {
        val item = state.item
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

object ToggleItemRenderer : ItemRenderer<ToggleUiState, Boolean>(ToggleUiState::class) {
    @Composable
    override fun Render(
        state: ToggleUiState,
        onValueChanged: (Boolean) -> Unit,
        isHighlighted: Boolean,
        modifier: Modifier
    ) {
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
                        Text(text = state.title, style = MaterialTheme.typography.bodyLarge)
                        if (isHighlighted) {
                            Spacer(modifier = Modifier.width(8.dp))
                            DeepLinkTargetBadge()
                        }
                    }
                    Text(
                        text = "Key: ${state.item.key}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.isChecked,
                    enabled = state.isEnabled,
                    onCheckedChange = onValueChanged
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

object TextItemRenderer : ItemRenderer<TextUiState, Unit>(TextUiState::class) {
    @Composable
    override fun Render(
        state: TextUiState,
        onValueChanged: (Unit) -> Unit,
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
                Text(text = state.text, style = MaterialTheme.typography.bodyLarge)
                if (isHighlighted) {
                    DeepLinkTargetBadge()
                }
            }
            Text(
                text = "Key: ${state.item.key}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

object SubScreenEntryItemRenderer : ItemRenderer<EntryUiState, ItemNavigator>(EntryUiState::class) {
    @Composable
    override fun Render(
        state: EntryUiState,
        onValueChanged: (ItemNavigator) -> Unit,
        isHighlighted: Boolean,
        modifier: Modifier
    ) {
        val navigator = LocalItemNavigator.current

        Column(
            modifier = modifier
                .fillMaxWidth()
                .deepLinkHighlightBox(isHighlighted)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = state.isEnabled) {
                        onValueChanged(navigator)
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = state.title, style = MaterialTheme.typography.bodyLarge)
                        if (isHighlighted) {
                            Spacer(modifier = Modifier.width(8.dp))
                            DeepLinkTargetBadge()
                        }
                    }
                    val targetDesc = state.targetScreen?.route ?: "Dynamic (Controller)"
                    Text(
                        text = "Key: ${state.item.key}  ➔  Target: $targetDesc",
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
    val all: List<ItemRenderer<*, *>> = listOf(
        SectionItemRenderer,
        LinearItemRenderer,
        ToggleItemRenderer,
        TextItemRenderer,
        SubScreenEntryItemRenderer
    )
}
