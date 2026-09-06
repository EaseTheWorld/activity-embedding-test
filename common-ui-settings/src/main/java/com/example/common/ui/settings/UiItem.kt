package com.example.common.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.example.core.item.ActionItem
import com.example.core.item.ChoiceItem
import com.example.core.item.Item
import com.example.core.item.SliderItem
import com.example.core.item.ToggleItem
import com.example.core.item.VhalBoundItem
import com.example.core.item.VhalPropertyBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
open class UiOption<T>(
    val value: T,
    @get:StringRes val labelRes: Int,
    @get:DrawableRes val iconRes: Int? = null,
    val badge: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UiOption<*>) return false
        return value == other.value && labelRes == other.labelRes && iconRes == other.iconRes && badge == other.badge
    }

    override fun hashCode(): Int {
        var result = value?.hashCode() ?: 0
        result = 31 * result + labelRes
        result = 31 * result + (iconRes ?: 0)
        result = 31 * result + (badge?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "UiOption(value=$value, labelRes=$labelRes, iconRes=$iconRes, badge=$badge)"
    }
}

/**
 * Cohesive Vehicle HAL option model extending [UiOption] with the hardware [vhalValue].
 *
 * Establishes a tri-directional mapping (Domain Value, UI Resource, Hardware VHAL Value)
 * in a single, unified declaration.
 */
open class CarUiOption<T, V>(
    value: T,
    @StringRes labelRes: Int,
    @DrawableRes iconRes: Int? = null,
    badge: String? = null,
    val vhalValue: V
) : UiOption<T>(value, labelRes, iconRes, badge) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CarUiOption<*, *>) return false
        if (!super.equals(other)) return false
        return vhalValue == other.vhalValue
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (vhalValue?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "CarUiOption(value=$value, labelRes=$labelRes, iconRes=$iconRes, badge=$badge, vhalValue=$vhalValue)"
    }
}

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

// ============================================================================
// Base Abstract Classes & VHAL Bridges (Constructor Delegation via super(...))
// ============================================================================

/**
 * Base abstract class for standard Choice UI items.
 *
 * Implements constructor delegation (`super(...)`) and encapsulates default [MutableStateFlow]
 * state holding, eliminating boilerplate in concrete item classes.
 */
abstract class BaseUiChoiceItem(
    override val key: String,
    @get:StringRes override val titleRes: Int,
    override val choiceOptions: List<UiOption<String>>,
    initialValue: String = choiceOptions.firstOrNull()?.value ?: "",
    @get:StringRes override val subtitleRes: Int? = null,
    @get:DrawableRes override val iconRes: Int? = null,
    override val optionSlot: OptionSlot<String> = ChoiceOptionSlots.Segmented
) : UiChoiceItem {

    private val _valueFlow = MutableStateFlow(initialValue)
    override val valueFlow: StateFlow<String> = _valueFlow.asStateFlow()

    override fun onValueChanged(newValue: String) {
        if (options.contains(newValue)) {
            _valueFlow.value = newValue
        }
    }
}

/**
 * Fallback in-memory VHAL property binder for preview and testing environments.
 */
class InMemoryVhalBinder : VhalPropertyBinder {
    private val flows = mutableMapOf<Pair<Int, Int>, MutableStateFlow<Any?>>()

    @Suppress("UNCHECKED_CAST")
    override fun <T, V> bind(item: VhalBoundItem<T, V>, initialValue: T): StateFlow<T> {
        val key = item.propertyId to item.areaId
        val flow = flows.getOrPut(key) {
            MutableStateFlow(initialValue)
        }
        return flow as StateFlow<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T, V> setProperty(item: VhalBoundItem<T, V>, newValue: T) {
        val key = item.propertyId to item.areaId
        val flow = flows[key] as? MutableStateFlow<T>
        flow?.value = newValue
    }
}

/**
 * Bridge abstract class unifying UI presentation ([BaseUiChoiceItem]) with hardware
 * VHAL binding ([VhalBoundItem]).
 *
 * Automatically delegates state to [binder] by passing `this`:
 * - [valueFlow]: Bound via `binder.bind(this, initialValue)`.
 * - [onValueChanged]: Dispatches new value via `binder.setProperty(this, newValue)`.
 * - [toItemValue]: Maps raw hardware [vhalValue] to domain [String] value.
 * - [toVhalValue]: Maps domain [String] value to raw hardware [vhalValue].
 */
abstract class VhalChoiceItem<V>(
    key: String,
    titleRes: Int,
    override val propertyId: Int,
    override val areaId: Int = 0,
    val carOptions: List<CarUiOption<String, V>>,
    initialValue: String = carOptions.firstOrNull()?.value ?: "",
    subtitleRes: Int? = null,
    iconRes: Int? = null,
    optionSlot: OptionSlot<String> = ChoiceOptionSlots.Segmented,
    private val binder: VhalPropertyBinder = InMemoryVhalBinder()
) : BaseUiChoiceItem(
    key = key,
    titleRes = titleRes,
    choiceOptions = carOptions,
    initialValue = initialValue,
    subtitleRes = subtitleRes,
    iconRes = iconRes,
    optionSlot = optionSlot
), VhalBoundItem<String, V> {

    // ★ Repository/Binder에 VhalBoundItem(this)을 전달하여 하드웨어/VHAL과 양방향 연동!
    override val valueFlow: StateFlow<String> by lazy {
        binder.bind(this, choiceOptions.firstOrNull()?.value ?: "")
    }

    override fun onValueChanged(newValue: String) {
        if (options.contains(newValue)) {
            binder.setProperty(this, newValue)
        }
    }

    override fun toItemValue(vhalValue: V): String {
        return carOptions.firstOrNull { it.vhalValue == vhalValue }?.value ?: valueFlow.value
    }

    override fun toVhalValue(itemValue: String): V {
        return carOptions.firstOrNull { it.value == itemValue }?.vhalValue
            ?: carOptions.first().vhalValue
    }
}

/**
 * Base abstract class for standard Toggle UI items.
 */
abstract class BaseUiToggleItem(
    override val key: String,
    @get:StringRes override val titleRes: Int,
    initialValue: Boolean = false,
    @get:StringRes override val subtitleRes: Int? = null,
    @get:DrawableRes override val iconRes: Int? = null,
    override val badgeKey: String? = null,
    @get:DrawableRes override val onIconRes: Int? = null,
    @get:DrawableRes override val offIconRes: Int? = null
) : UiToggleItem {

    private val _valueFlow = MutableStateFlow(initialValue)
    override val valueFlow: StateFlow<Boolean> = _valueFlow.asStateFlow()

    override fun onValueChanged(newValue: Boolean) {
        _valueFlow.value = newValue
    }
}

/**
 * Bridge abstract class unifying Toggle UI with VHAL binding.
 */
abstract class VhalToggleItem<V>(
    key: String,
    titleRes: Int,
    override val propertyId: Int,
    override val areaId: Int = 0,
    val onVhalValue: V,
    val offVhalValue: V,
    initialValue: Boolean = false,
    subtitleRes: Int? = null,
    iconRes: Int? = null,
    badgeKey: String? = null,
    onIconRes: Int? = null,
    offIconRes: Int? = null,
    private val binder: VhalPropertyBinder = InMemoryVhalBinder()
) : BaseUiToggleItem(
    key = key,
    titleRes = titleRes,
    initialValue = initialValue,
    subtitleRes = subtitleRes,
    iconRes = iconRes,
    badgeKey = badgeKey,
    onIconRes = onIconRes,
    offIconRes = offIconRes
), VhalBoundItem<Boolean, V> {

    // ★ Repository/Binder에 VhalBoundItem(this)을 전달하여 하드웨어/VHAL과 양방향 연동!
    override val valueFlow: StateFlow<Boolean> by lazy {
        binder.bind(this, false)
    }

    override fun onValueChanged(newValue: Boolean) {
        binder.setProperty(this, newValue)
    }

    override fun toItemValue(vhalValue: V): Boolean {
        return vhalValue == onVhalValue
    }

    override fun toVhalValue(itemValue: Boolean): V {
        return if (itemValue) onVhalValue else offVhalValue
    }
}
