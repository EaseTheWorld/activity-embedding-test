package com.example.common.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Predefined, reusable Composable slots for rendering options in [UiChoiceItem].
 */
object ChoiceOptionSlots {

    /**
     * Standard Segmented Button slot with filled selected state and outlined unselected state.
     */
    val Segmented: OptionSlot<String> = { option, isSelected, onClick ->
        val label = stringResource(option.labelRes)
        if (isSelected) {
            Button(
                onClick = { /* already selected */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(8.dp)
            ) {
                OptionContent(option = option, label = label, textColor = Color.White)
            }
        } else {
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                OptionContent(option = option, label = label, textColor = Color(0xFF49454F))
            }
        }
    }

    /**
     * Compact FilterChip slot.
     */
    val Chip: OptionSlot<String> = { option, isSelected, onClick ->
        val label = stringResource(option.labelRes)
        FilterChip(
            selected = isSelected,
            onClick = onClick,
            label = { Text(text = label, fontSize = 13.sp) },
            leadingIcon = option.iconRes?.let { iconRes ->
                {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

    /**
     * Icon-only slot.
     */
    val IconOnly: OptionSlot<String> = { option, isSelected, onClick ->
        if (isSelected) {
            Button(
                onClick = { /* already selected */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (option.iconRes != null) {
                    Icon(
                        painter = painterResource(option.iconRes),
                        contentDescription = stringResource(option.labelRes),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (option.iconRes != null) {
                    Icon(
                        painter = painterResource(option.iconRes),
                        contentDescription = stringResource(option.labelRes),
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF49454F)
                    )
                }
            }
        }
    }

    @Composable
    private fun OptionContent(option: UiOption<String>, label: String, textColor: Color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (option.iconRes != null) {
                Icon(
                    painter = painterResource(option.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
            if (option.badge != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Badge {
                    Text(text = option.badge, fontSize = 10.sp)
                }
            }
        }
    }
}
