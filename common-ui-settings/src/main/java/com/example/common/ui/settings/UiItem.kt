package com.example.common.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.item.ActionItem
import com.example.core.item.ChoiceItem
import com.example.core.item.ContainerItem
import com.example.core.item.HasVhalBinding
import com.example.core.item.Item
import com.example.core.item.SliderItem
import com.example.core.item.ToggleItem
import com.example.core.item.ValueItem
import com.example.core.item.VhalBinding
import com.example.core.item.VhalPropertyBinder
import java.util.concurrent.atomic.AtomicInteger
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
 * Unified Root UI Contract extending [Item] with presentation metadata and rendering capabilities.
 * Free of state requirements, enabling non-state items (Containers, Actions, Static Headers).
 */
interface UiItem : Item, ComposableItemRenderer {
    @get:StringRes val titleRes: Int
    @get:StringRes val subtitleRes: Int? get() = null
    @get:DrawableRes val iconRes: Int? get() = null
}

/**
 * Stateful UI Contract extending [UiItem] and [ValueItem] with visual data mapping for state values [T].
 */
interface UiValueItem<T> : UiItem, ValueItem<T> {
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

// Backward-compatibility alias
typealias UiItemT<T> = UiValueItem<T>

/**
 * Standard Toggle UI Item.
 * Supports optional badge, toggle-specific on/off icons, and default Switch row rendering.
 */
interface UiToggleItem : ToggleItem, UiValueItem<Boolean> {
    val badgeKey: String? get() = null
    @get:DrawableRes val onIconRes: Int? get() = null
    @get:DrawableRes val offIconRes: Int? get() = null

    override fun getValueVisual(value: Boolean): UiVisualData? {
        val icon = if (value) onIconRes else offIconRes
        return if (icon != null) UiVisualData(iconRes = icon) else null
    }

    @Composable
    override fun Draw(modifier: Modifier) {
        ToggleItemRow(item = this, modifier = modifier)
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
interface UiChoiceItem : ChoiceItem, UiValueItem<String> {
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
    override fun Draw(modifier: Modifier) {
        ChoiceItemRow(item = this, modifier = modifier)
    }
}

/**
 * Standard Slider UI Item.
 */
interface UiSliderItem : SliderItem, UiValueItem<Int> {
    @get:StringRes val unitRes: Int? get() = null

    @Composable
    override fun Draw(modifier: Modifier) {
        SliderItemRow(item = this, modifier = modifier)
    }
}

/**
 * Standard Action (Button) UI Item.
 */
interface UiActionItem : ActionItem, UiValueItem<Unit> {
    @get:StringRes val buttonLabelRes: Int? get() = null
}

/**
 * Composite UI Container Item.
 */
interface UiContainerItem : ContainerItem, UiItem {
    override val children: List<UiItem> get() = emptyList()
}

private val spacerCounter = AtomicInteger(0)

/**
 * Structural Spacer Item for data-driven layout margins.
 *
 * Exposes an automatic unique key sequence by default (`"spacer_${counter}"`)
 * or allows an explicit, deterministic key when needed.
 */
class SpacerItem(
    val heightDp: Int = 16,
    override val key: String = "spacer_${spacerCounter.incrementAndGet()}"
) : UiItem {
    override val titleRes: Int = 0

    @Composable
    override fun Draw(modifier: Modifier) {
        Spacer(modifier = modifier.height(heightDp.dp))
    }
}

/**
 * Convenience extension appending a [SpacerItem] tied deterministically to this item's key.
 */
fun Item.withSpacer(heightDp: Int = 16): List<Item> = listOf(
    this,
    SpacerItem(heightDp = heightDp, key = "${this.key}_spacer")
)

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
    override fun <T, V> bind(binding: VhalBinding<T, V>, initialValue: T): StateFlow<T> {
        val key = binding.propertyId to binding.areaId
        val flow = flows.getOrPut(key) {
            MutableStateFlow(initialValue)
        }
        return flow as StateFlow<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T, V> setProperty(binding: VhalBinding<T, V>, newValue: T) {
        val key = binding.propertyId to binding.areaId
        val flow = flows[key] as? MutableStateFlow<T>
        flow?.value = newValue
    }
}

/**
 * Bridge abstract class unifying UI presentation ([BaseUiChoiceItem]) with hardware
 * VHAL binding via composition ([VhalBinding]).
 *
 * Implements Field Composition (has-a) instead of Interface Inheritance (is-a):
 * - Holds a pure [vhalBinding] field.
 * - Passes only [vhalBinding] (never `this`) to [binder].
 * - Eliminates multiple-inheritance data source ambiguity and constructor `this` leakage.
 */
abstract class VhalChoiceItem<V>(
    key: String,
    titleRes: Int,
    val propertyId: Int,
    val areaId: Int = 0,
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
), HasVhalBinding<String, V> {

    // ★ VhalBinding을 필드로 합성(has-a)
    override val vhalBinding: VhalBinding<String, V> = VhalBinding(
        propertyId = propertyId,
        areaId = areaId,
        toItemValue = { raw -> carOptions.firstOrNull { it.vhalValue == raw }?.value ?: initialValue },
        toVhalValue = { domain -> carOptions.firstOrNull { it.value == domain }?.vhalValue ?: carOptions.first().vhalValue }
    )

    // ★ this가 아닌 순수 데이터 객체 vhalBinding만 바인더에 전달! (No leaky this!)
    override val valueFlow: StateFlow<String> by lazy {
        binder.bind(vhalBinding, choiceOptions.firstOrNull()?.value ?: "")
    }

    override fun onValueChanged(newValue: String) {
        if (options.contains(newValue)) {
            binder.setProperty(vhalBinding, newValue)
        }
    }

    fun toItemValue(vhalValue: V): String = vhalBinding.toItemValue(vhalValue)
    fun toVhalValue(itemValue: String): V = vhalBinding.toVhalValue(itemValue)
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
 * Bridge abstract class unifying Toggle UI with VHAL binding via composition ([VhalBinding]).
 */
abstract class VhalToggleItem<V>(
    key: String,
    titleRes: Int,
    val propertyId: Int,
    val areaId: Int = 0,
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
), HasVhalBinding<Boolean, V> {

    // ★ VhalBinding을 필드로 합성(has-a)
    override val vhalBinding: VhalBinding<Boolean, V> = VhalBinding(
        propertyId = propertyId,
        areaId = areaId,
        toItemValue = { raw -> raw == onVhalValue },
        toVhalValue = { domain -> if (domain) onVhalValue else offVhalValue }
    )

    // ★ this가 아닌 순수 데이터 객체 vhalBinding만 바인더에 전달!
    override val valueFlow: StateFlow<Boolean> by lazy {
        binder.bind(vhalBinding, initialValue)
    }

    override fun onValueChanged(newValue: Boolean) {
        binder.setProperty(vhalBinding, newValue)
    }

    fun toItemValue(vhalValue: V): Boolean = vhalBinding.toItemValue(vhalValue)
    fun toVhalValue(itemValue: Boolean): V = vhalBinding.toVhalValue(itemValue)
}
