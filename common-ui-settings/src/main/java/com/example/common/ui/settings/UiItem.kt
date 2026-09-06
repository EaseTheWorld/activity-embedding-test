package com.example.common.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.example.core.item.ActionItem
import com.example.core.item.ChoiceItem
import com.example.core.item.Item
import com.example.core.item.SliderItem
import com.example.core.item.ToggleItem

/**
 * Unified UI Contract extending [Item] with presentation metadata and rendering capabilities.
 *
 * Extensibility features:
 * - [titleRes]: Mandatory @StringRes for compile-time safety and instant rendering.
 * - [subtitleRes]: Optional @StringRes (defaults to null).
 * - [iconRes]: Optional @DrawableRes (defaults to null).
 * - [getValueTextRes]: Dynamic mapping from state value [T] to localized @StringRes (e.g. option labels).
 * - [getValueIconRes]: Dynamic mapping from state value [T] to @DrawableRes (e.g. dynamic state icons).
 * - [Draw]: Polymorphic Composable renderer with ZERO 'when' branching in the host screen.
 */
interface UiItem<T> : Item<T>, ComposableItemRenderer {
    @get:StringRes val titleRes: Int
    @get:StringRes val subtitleRes: Int? get() = null
    @get:DrawableRes val iconRes: Int? get() = null

    /**
     * Dynamic value-to-string mapping for current state or discrete options.
     */
    @StringRes
    fun getValueTextRes(value: T): Int? = null

    /**
     * Dynamic value-to-icon mapping for current state or discrete options.
     */
    @DrawableRes
    fun getValueIconRes(value: T): Int? = null
}

/**
 * Standard Toggle UI Item.
 * Supports optional badge, toggle-specific on/off icons, and default Switch row rendering.
 */
interface UiToggleItem : ToggleItem, UiItem<Boolean> {
    val badgeKey: String? get() = null
    @get:DrawableRes val onIconRes: Int? get() = null
    @get:DrawableRes val offIconRes: Int? get() = null

    override fun getValueIconRes(value: Boolean): Int? = if (value) onIconRes else offIconRes

    @Composable
    override fun Draw() {
        ToggleItemRow(item = this)
    }
}

/**
 * Standard Choice UI Item (SegmentedButton, Multi-option).
 * Supports option label mappings and option icon mappings.
 */
interface UiChoiceItem : ChoiceItem, UiItem<String> {
    val optionLabels: Map<String, Int> get() = emptyMap()
    val optionIcons: Map<String, Int> get() = emptyMap()

    @StringRes
    override fun getValueTextRes(value: String): Int? = optionLabels[value]

    @DrawableRes
    override fun getValueIconRes(value: String): Int? = optionIcons[value]

    @Composable
    override fun Draw() {
        ChoiceItemRow(item = this)
    }
}

/**
 * Standard Slider UI Item.
 */
interface UiSliderItem : SliderItem, UiItem<Int> {
    @get:StringRes val unitRes: Int? get() = null

    @Composable
    override fun Draw() {
        SliderItemRow(item = this)
    }
}

/**
 * Standard Action (Button) UI Item.
 */
interface UiActionItem : ActionItem, UiItem<Unit> {
    @get:StringRes val buttonLabelRes: Int? get() = null
}

// Backward-compatibility aliases
typealias ComposableToggleItem = UiToggleItem
typealias ComposableChoiceItem = UiChoiceItem
