package com.example.common.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.item.Item
import com.example.core.item.ItemViewModelRegistry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal to control whether the Category Main Screen renders its top title/header.
 * - Narrow (Overlay) Mode: true (Displays title to clarify current category context).
 * - Wide (Split) Mode: false (Hidden to avoid redundancy with the persistent left 35% sidebar).
 */
val LocalShowCategoryHeader = compositionLocalOf { true }

/**
 * Universal Settings Screen rendered via Jetpack Compose.
 * Renders standard and domain items directly via declarative Compose rows,
 * resolving the single-source-of-truth ItemViewModel by item ID.
 *
 * Supports navigation back via [onNavigateBack], anchor scrolling to [targetItemId],
 * and intra-category item drill-down via [onItemClick] for items with detail screens.
 */
@Composable
fun GenericSettingsScreen(
    title: String,
    subtitle: String,
    items: List<Item>,
    targetItemId: String? = null,
    showCategoryHeader: Boolean = LocalShowCategoryHeader.current,
    onMenuClick: (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
    onItemClick: ((Item) -> Unit)? = null,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current
) {
    val scrollState = rememberScrollState()
    val highlightEvent = LocalHighlightEvent.current
    val effectiveHighlightEvent = highlightEvent ?: targetItemId?.let { HighlightEvent(itemId = it) }

    CompositionLocalProvider(LocalHighlightEvent provides effectiveHighlightEvent) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF6F7FB)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            val effectiveMenuClick = if (showCategoryHeader) onMenuClick else null
            if (showCategoryHeader || onNavigateBack != null) {
                CategoryMainHeader(
                    title = title,
                    subtitle = subtitle,
                    showMenuButton = effectiveMenuClick != null,
                    showTitle = showCategoryHeader,
                    onMenuClick = effectiveMenuClick,
                    onNavigateBack = onNavigateBack
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.material3.Button(
                onClick = {
                    val subIntent = android.content.Intent(context, GenericSubActivity::class.java).apply {
                        putExtra(GenericSubActivity.EXTRA_PARENT_TITLE, title)
                    }
                    context.startActivity(subIntent)
                }
            ) {
                Text("Open Sub-Setting Page (GenericSubActivity)")
            }
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    items.forEach { item ->
                        val isItemVisible by item.isVisible.collectAsState(initial = true)
                        val vm = viewModelRegistry.getViewModel<Any>(item.id)
                        val isVmVisible = (vm?.isVisibleFlow?.collectAsState())?.value ?: true
                        if (isItemVisible && isVmVisible) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .anchor(item.id)
                                    .padding(vertical = 48.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    when (item) {
                                        is UiToggleItem -> ToggleItemRow(item = item, viewModelRegistry = viewModelRegistry)
                                        is UiChoiceItem -> ChoiceItemRow(item = item, viewModelRegistry = viewModelRegistry)
                                        is UiSliderItem -> SliderItemRow(item = item, viewModelRegistry = viewModelRegistry)
                                        is UiItem -> {
                                            Text(
                                                text = androidx.compose.ui.res.stringResource(item.nameResId),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                        else -> {
                                            Text(
                                                text = "Item: ${item.id}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                                if (onItemClick != null && item.hasDetailScreen) {
                                    IconButton(
                                        onClick = { onItemClick(item) },
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "View details for ${item.id}",
                                            tint = Color(0xFF9E9E9E)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = Color(0xFFF1F2F6)
                            )
                        }
                    }
                }
            }
        }
    }
}
}
