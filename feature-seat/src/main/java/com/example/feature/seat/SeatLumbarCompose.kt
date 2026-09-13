package com.example.feature.seat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.common.ui.settings.ItemRenderer
import com.example.common.ui.settings.LocalItemViewModelRegistry
import com.example.core.item.ItemViewModel
import com.example.core.item.ItemViewModelRegistry

/**
 * Encapsulated Composable UI for SeatLumbarSupportItem located completely inside :feature-seat.
 * Decoupled from state management: observes and mutates via [ItemViewModel].
 */
@Composable
fun SeatLumbarRow(
    item: SeatLumbarSupportItem,
    viewModel: ItemViewModel<SeatLumbarSupport>,
    modifier: Modifier = Modifier
) {
    val lumbar by viewModel.valueFlow.collectAsState()

    val title = androidx.compose.ui.res.stringResource(item.titleRes)
    val subtitle = item.subtitleRes?.let { androidx.compose.ui.res.stringResource(it) } ?: ""

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E1E2E)
            )
        )
        Text(
            text = "Position: Height ${lumbar.heightPercent}% | Depth ${lumbar.depthPercent}% (Custom 2D)",
            style = MaterialTheme.typography.labelMedium.copy(
                color = Color(0xFF6750A4),
                fontWeight = FontWeight.Medium
            )
        )
        if (subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF757575)
                )
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val next = lumbar.copy(heightPercent = (lumbar.heightPercent + 10).coerceAtMost(100))
                    viewModel.setValue(next)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Height ▲", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    val next = lumbar.copy(heightPercent = (lumbar.heightPercent - 10).coerceAtLeast(0))
                    viewModel.setValue(next)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Height ▼", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    val next = lumbar.copy(depthPercent = (lumbar.depthPercent + 10).coerceAtMost(100))
                    viewModel.setValue(next)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Depth +", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    val next = lumbar.copy(depthPercent = (lumbar.depthPercent - 10).coerceAtLeast(0))
                    viewModel.setValue(next)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Depth -", fontSize = 12.sp)
            }
        }
    }
}

/**
 * Overload resolving [SeatLumbarViewModel] from [ItemViewModelRegistry].
 */
@Composable
fun SeatLumbarRow(
    item: SeatLumbarSupportItem,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current,
    modifier: Modifier = Modifier
) {
    val viewModel = viewModelRegistry.getViewModel<SeatLumbarSupport>(item.id)
        ?: error("No ItemViewModel found for lumbar item: ${item.id}")
    SeatLumbarRow(item = item, viewModel = viewModel, modifier = modifier)
}

/**
 * ItemRenderer implementation for SeatLumbarSupportItem, enabling dynamic rendering via [ItemRendererRegistry].
 */
class SeatLumbarRenderer : ItemRenderer<SeatLumbarSupportItem, SeatLumbarSupport> {
    @Composable
    override fun Render(
        item: SeatLumbarSupportItem,
        viewModel: ItemViewModel<SeatLumbarSupport>,
        modifier: Modifier
    ) {
        SeatLumbarRow(item = item, viewModel = viewModel, modifier = modifier)
    }
}
