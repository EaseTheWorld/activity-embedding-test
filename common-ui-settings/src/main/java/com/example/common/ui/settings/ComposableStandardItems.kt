package com.example.common.ui.settings

import androidx.compose.runtime.Composable
import com.example.core.item.ChoiceItem
import com.example.core.item.ToggleItem

/**
 * Standard Toggle Item equipped with default Composable self-drawing capability.
 */
interface ComposableToggleItem : ToggleItem, ComposableItemRenderer {
    @Composable
    override fun Draw() {
        ToggleItemRow(item = this)
    }
}

/**
 * Standard Choice Item equipped with default Composable self-drawing capability.
 */
interface ComposableChoiceItem : ChoiceItem, ComposableItemRenderer {
    @Composable
    override fun Draw() {
        ChoiceItemRow(item = this)
    }
}
