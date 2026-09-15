package com.example.common.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.example.core.item.HasVhalBinding
import com.example.core.item.Item
import com.example.core.item.ItemType
import com.example.core.item.ItemViewModel
import com.example.core.item.MutableItemViewModel
import com.example.core.item.VhalBinding
import com.example.core.item.VhalPropertyBinder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

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
 * UI-enriched Setting Item providing presentation resources.
 * Marked with [Immutable] so Compose compiler can skip recompositions
 * when parameters of this type are unchanged.
 */
@Immutable
open class UiItem(
    id: String,
    @get:StringRes val nameResId: Int,
    @get:DrawableRes val iconResId: Int? = null,
    @get:StringRes val descriptionResId: Int? = null,
    isVisible: Flow<Boolean> = flowOf(true),
    children: Set<Item> = emptySet()
) : Item(id = id, children = children, isVisible = isVisible) {

    val titleRes: Int get() = nameResId
    val subtitleRes: Int? get() = descriptionResId
    val iconRes: Int? get() = iconResId
}

/**
 * Standard Toggle UI Item.
 * Pure declarative presentation metadata without reactive state holding.
 */
@Immutable
open class UiToggleItem(
    id: String,
    @StringRes nameResId: Int,
    @DrawableRes iconResId: Int? = null,
    @StringRes descriptionResId: Int? = null,
    open val badgeKey: String? = null,
    @get:DrawableRes open val onIconRes: Int? = null,
    @get:DrawableRes open val offIconRes: Int? = null,
    isVisible: Flow<Boolean> = flowOf(true),
    children: Set<Item> = emptySet()
) : UiItem(
    id = id,
    nameResId = nameResId,
    iconResId = iconResId,
    descriptionResId = descriptionResId,
    isVisible = isVisible,
    children = children
) {

    override val type: ItemType get() = ItemType.TOGGLE

    open fun getValueVisual(value: Boolean): UiVisualData? {
        val icon = if (value) onIconRes else offIconRes
        return if (icon != null) UiVisualData(iconRes = icon) else null
    }

    fun getValueIconRes(value: Boolean): Int? = getValueVisual(value)?.iconRes
}

/**
 * Composable slot type for rendering an individual option in a ChoiceItem.
 */
typealias OptionSlot<T> = @Composable (option: UiOption<T>, isSelected: Boolean, enabled: Boolean, onClick: () -> Unit) -> Unit

/**
 * Standard Choice UI Item.
 * Pure declarative presentation metadata without reactive state holding.
 */
@Immutable
open class UiChoiceItem(
    id: String,
    @StringRes nameResId: Int,
    val options: List<UiOption<String>>,
    @DrawableRes iconResId: Int? = null,
    @StringRes descriptionResId: Int? = null,
    val optionSlot: OptionSlot<String> = OptionSlots.Segmented,
    isVisible: Flow<Boolean> = flowOf(true),
    children: Set<Item> = emptySet()
) : UiItem(
    id = id,
    nameResId = nameResId,
    iconResId = iconResId,
    descriptionResId = descriptionResId,
    isVisible = isVisible,
    children = children
) {

    override val type: ItemType get() = ItemType.CHOICE

    val optionIds: List<String> get() = options.map { it.value }

    fun getOption(value: String): UiOption<String>? =
        options.firstOrNull { it.value == value }

    open fun getValueVisual(value: String): UiVisualData? {
        val opt = getOption(value) ?: return null
        return UiVisualData(textRes = opt.labelRes, iconRes = opt.iconRes)
    }

    fun getValueTextRes(value: String): Int? = getValueVisual(value)?.textRes
    fun getValueIconRes(value: String): Int? = getValueVisual(value)?.iconRes
}

/**
 * Standard Slider UI Item.
 * Pure declarative presentation metadata without reactive state holding.
 */
@Immutable
open class UiSliderItem(
    id: String,
    @StringRes nameResId: Int,
    val min: Int,
    val max: Int,
    @StringRes val unitRes: Int? = null,
    @DrawableRes iconResId: Int? = null,
    @StringRes descriptionResId: Int? = null,
    isVisible: Flow<Boolean> = flowOf(true),
    children: Set<Item> = emptySet()
) : UiItem(
    id = id,
    nameResId = nameResId,
    iconResId = iconResId,
    descriptionResId = descriptionResId,
    isVisible = isVisible,
    children = children
) {
    override val type: ItemType get() = ItemType.SLIDER
}

/**
 * Standard Action (Button) UI Item.
 * Pure declarative presentation metadata without reactive state holding.
 */
@Immutable
open class UiActionItem(
    id: String,
    @StringRes nameResId: Int,
    @DrawableRes iconResId: Int? = null,
    @StringRes descriptionResId: Int? = null,
    isVisible: Flow<Boolean> = flowOf(true),
    children: Set<Item> = emptySet()
) : UiItem(
    id = id,
    nameResId = nameResId,
    iconResId = iconResId,
    descriptionResId = descriptionResId,
    isVisible = isVisible,
    children = children
) {
    override val type: ItemType get() = ItemType.ACTION
}

// ============================================================================
// Base Abstract Classes & VHAL Bridges
// ============================================================================

/**
 * Convenience subclass for standard Choice UI items with default parameters.
 * Stateless pure metadata.
 */
open class BaseUiChoiceItem(
    id: String,
    @StringRes nameResId: Int,
    options: List<UiOption<String>>,
    @StringRes descriptionResId: Int? = null,
    @DrawableRes iconResId: Int? = null,
    optionSlot: OptionSlot<String> = OptionSlots.Segmented,
    isVisible: Flow<Boolean> = flowOf(true),
    children: Set<Item> = emptySet()
) : UiChoiceItem(
    id = id,
    nameResId = nameResId,
    options = options,
    iconResId = iconResId,
    descriptionResId = descriptionResId,
    optionSlot = optionSlot,
    isVisible = isVisible,
    children = children
)

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
 * Implements [ItemViewModel] for legacy backwards compatibility.
 */
abstract class VhalChoiceItem<V>(
    id: String,
    @StringRes nameResId: Int,
    val propertyId: Int,
    val areaId: Int = 0,
    val carOptions: List<CarUiOption<String, V>>,
    val initialValue: String = carOptions.firstOrNull()?.value ?: "",
    @StringRes descriptionResId: Int? = null,
    @DrawableRes iconResId: Int? = null,
    optionSlot: OptionSlot<String> = OptionSlots.Segmented,
    private val binder: VhalPropertyBinder = InMemoryVhalBinder(),
    isVisible: Flow<Boolean> = flowOf(true),
    children: Set<Item> = emptySet()
) : BaseUiChoiceItem(
    id = id,
    nameResId = nameResId,
    options = carOptions,
    descriptionResId = descriptionResId,
    iconResId = iconResId,
    optionSlot = optionSlot,
    isVisible = isVisible,
    children = children
), HasVhalBinding<String, V>, MutableItemViewModel<String> {

    override val vhalBinding: VhalBinding<String, V> = VhalBinding(
        propertyId = propertyId,
        areaId = areaId,
        toItemValue = { raw -> carOptions.firstOrNull { it.vhalValue == raw }?.value ?: initialValue },
        toVhalValue = { domain -> carOptions.firstOrNull { it.value == domain }?.vhalValue ?: carOptions.first().vhalValue }
    )

    override val valueFlow: StateFlow<String> by lazy {
        binder.bind(vhalBinding, initialValue)
    }

    override fun setValue(newValue: String) {
        onValueChanged(newValue)
    }

    open val onValueChanged: (String) -> Unit = { newValue ->
        if (optionIds.contains(newValue)) {
            binder.setProperty(vhalBinding, newValue)
        }
    }

    fun toItemValue(vhalValue: V): String = vhalBinding.toItemValue(vhalValue)
    fun toVhalValue(itemValue: String): V = vhalBinding.toVhalValue(itemValue)
}

/**
 * Convenience subclass for standard Toggle UI items with default parameters.
 * Stateless pure metadata.
 */
open class BaseUiToggleItem(
    id: String,
    @StringRes nameResId: Int,
    @StringRes descriptionResId: Int? = null,
    @DrawableRes iconResId: Int? = null,
    badgeKey: String? = null,
    @DrawableRes onIconRes: Int? = null,
    @DrawableRes offIconRes: Int? = null,
    isVisible: Flow<Boolean> = flowOf(true),
    children: Set<Item> = emptySet()
) : UiToggleItem(
    id = id,
    nameResId = nameResId,
    iconResId = iconResId,
    descriptionResId = descriptionResId,
    badgeKey = badgeKey,
    onIconRes = onIconRes,
    offIconRes = offIconRes,
    isVisible = isVisible,
    children = children
)

/**
 * Bridge abstract class unifying Toggle UI with VHAL binding via composition ([VhalBinding]).
 * Implements [ItemViewModel] for legacy backwards compatibility.
 */
abstract class VhalToggleItem<V>(
    id: String,
    @StringRes nameResId: Int,
    val propertyId: Int,
    val areaId: Int = 0,
    val onVhalValue: V,
    val offVhalValue: V,
    val initialValue: Boolean = false,
    @StringRes descriptionResId: Int? = null,
    @DrawableRes iconResId: Int? = null,
    badgeKey: String? = null,
    @DrawableRes onIconRes: Int? = null,
    @DrawableRes offIconRes: Int? = null,
    private val binder: VhalPropertyBinder = InMemoryVhalBinder(),
    isVisible: Flow<Boolean> = flowOf(true),
    children: Set<Item> = emptySet()
) : BaseUiToggleItem(
    id = id,
    nameResId = nameResId,
    descriptionResId = descriptionResId,
    iconResId = iconResId,
    badgeKey = badgeKey,
    onIconRes = onIconRes,
    offIconRes = offIconRes,
    isVisible = isVisible,
    children = children
), HasVhalBinding<Boolean, V>, MutableItemViewModel<Boolean> {

    override val vhalBinding: VhalBinding<Boolean, V> = VhalBinding(
        propertyId = propertyId,
        areaId = areaId,
        toItemValue = { raw -> raw == onVhalValue },
        toVhalValue = { domain -> if (domain) onVhalValue else offVhalValue }
    )

    override val valueFlow: StateFlow<Boolean> by lazy {
        binder.bind(vhalBinding, initialValue)
    }

    override fun setValue(newValue: Boolean) {
        onValueChanged(newValue)
    }

    open val onValueChanged: (Boolean) -> Unit = { newValue ->
        binder.setProperty(vhalBinding, newValue)
    }

    fun toItemValue(vhalValue: V): Boolean = vhalBinding.toItemValue(vhalValue)
    fun toVhalValue(itemValue: Boolean): V = vhalBinding.toVhalValue(itemValue)
}
