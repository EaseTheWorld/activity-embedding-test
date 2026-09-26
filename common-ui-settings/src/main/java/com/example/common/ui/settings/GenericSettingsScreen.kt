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
    onNavigateBack: (() -> Unit)? = null,
    onItemClick: ((Item) -> Unit)? = null,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current
) {
    val scrollState = rememberScrollState()
    val highlightEvent = LocalHighlightEvent.current
    val effectiveTargetId = highlightEvent?.itemId ?: targetItemId

    CompositionLocalProvider(LocalAnchorTarget provides effectiveTargetId) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF6F7FB)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (onNavigateBack != null) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1E1E2E)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1E2E)
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF6B7280)
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

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
