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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Encapsulated Composable UI for SeatLumbarSupportItem located completely inside :feature-seat.
 */
@Composable
fun SeatLumbarRow(
    item: SeatLumbarSupportItem
) {
    val lumbar by item.valueFlow.collectAsState()

    val title = androidx.compose.ui.res.stringResource(item.titleRes)
    val subtitle = androidx.compose.ui.res.stringResource(item.subtitleRes)

    Column(
        modifier = Modifier
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
                    item.onValueChanged(next)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Height ▲", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    val next = lumbar.copy(heightPercent = (lumbar.heightPercent - 10).coerceAtLeast(0))
                    item.onValueChanged(next)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Height ▼", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    val next = lumbar.copy(depthPercent = (lumbar.depthPercent + 10).coerceAtMost(100))
                    item.onValueChanged(next)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Depth +", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    val next = lumbar.copy(depthPercent = (lumbar.depthPercent - 10).coerceAtLeast(0))
                    item.onValueChanged(next)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Depth -", fontSize = 12.sp)
            }
        }
    }
}
