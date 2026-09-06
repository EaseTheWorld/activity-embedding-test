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
 * Visual presentation metadata for an individual value or state.
 * Encapsulates localized string and icon resources in a single cohesive object.
 */
data class UiVisualData(
    @StringRes val textRes: Int? = null,
    val text: String? = null,
    @DrawableRes val iconRes: Int? = null
)

/**
 * Cohesive option model binding value, localized label, icon, and optional badge in a single definition.
 * Eliminates the need to maintain separate value lists and resource mapping tables.
 */
data class UiOption<T>(
    val value: T,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int? = null,
    val badge: String? = null
)

/**
 * Unified UI Contract extending [Item] with presentation metadata and rendering capabilities.
 */
interface UiItem<T> : Item<T>, ComposableItemRenderer {
    @get:StringRes val titleRes: Int
    @get:StringRes val subtitleRes: Int? get() = null
    @get:DrawableRes val iconRes: Int? get() = null

    /**
     * Unified visual data representation for a state value [T].
     */
    fun getValueVisual(value: T): UiVisualData? = null

    /**
     * Dynamic value-to-string mapping for current state or discrete options.
     * Delegates to [getValueVisual].
     */
    @StringRes
    fun getValueTextRes(value: T): Int? = getValueVisual(value)?.textRes

    /**
     * Dynamic value-to-icon mapping for current state or discrete options.
     * Delegates to [getValueVisual].
     */
    @DrawableRes
    fun getValueIconRes(value: T): Int? = getValueVisual(value)?.iconRes
}

/**
 * Standard Toggle UI Item.
 * Supports optional badge, toggle-specific on/off icons, and default Switch row rendering.
 */
interface UiToggleItem : ToggleItem, UiItem<Boolean> {
    val badgeKey: String? get() = null
    @get:DrawableRes val onIconRes: Int? get() = null
    @get:DrawableRes val offIconRes: Int? get() = null

    override fun getValueVisual(value: Boolean): UiVisualData? {
        val icon = if (value) onIconRes else offIconRes
        return if (icon != null) UiVisualData(iconRes = icon) else null
    }

    @Composable
    override fun Draw() {
        ToggleItemRow(item = this)
    }
}

/**
 * Composable slot type for rendering an individual option in a ChoiceItem.
 */
typealias OptionSlot<T> = @Composable (option: UiOption<T>, isSelected: Boolean, onClick: () -> Unit) -> Unit

/**
 * Standard Choice UI Item (SegmentedButton, Multi-option).
 *
 * Encapsulates options as [UiOption]s and delegates rendering to [optionSlot].
 * Derives the core contract's [options] automatically from [choiceOptions].
 */
interface UiChoiceItem : ChoiceItem, UiItem<String> {
    val choiceOptions: List<UiOption<String>>

    override val options: List<String>
        get() = choiceOptions.map { it.value }

    fun getOption(value: String): UiOption<String>? =
        choiceOptions.firstOrNull { it.value == value }

    override fun getValueVisual(value: String): UiVisualData? {
        val opt = getOption(value) ?: return null
        return UiVisualData(textRes = opt.labelRes, iconRes = opt.iconRes)
    }

    /**
     * Composable slot for rendering an individual option button.
     * Defaults to [ChoiceOptionSlots.Segmented]. Subclasses can override with predefined slots or custom lambdas.
     */
    val optionSlot: OptionSlot<String>
        get() = ChoiceOptionSlots.Segmented

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
