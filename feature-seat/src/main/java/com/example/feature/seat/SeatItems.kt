package com.example.feature.seat

import com.example.common.ui.settings.ChoiceOptionSlots
import com.example.common.ui.settings.UiChoiceItem
import com.example.common.ui.settings.UiItem
import com.example.common.ui.settings.UiOption
import com.example.common.ui.settings.UiToggleItem
import com.example.core.item.Item
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ============================================================================
// 1. Standard Choice Items (UiChoiceItem : ChoiceItem, UiItem<String>)
// ============================================================================

class DriverSeatHeatingItem : UiChoiceItem {
    override val key: String = "driver_seat_heat"
    override val titleRes: Int = R.string.seat_item_driver_heat_title
    override val subtitleRes: Int = R.string.seat_item_driver_heat_subtitle
    override val iconRes: Int = R.drawable.ic_feature_seat

    override val choiceOptions: List<UiOption<String>> = listOf(
        UiOption("OFF", R.string.seat_heat_off),
        UiOption("LEVEL 1", R.string.seat_heat_level_1),
        UiOption("LEVEL 2", R.string.seat_heat_level_2),
        UiOption("LEVEL 3", R.string.seat_heat_level_3)
    )

    private val _value = MutableStateFlow("OFF")
    override val valueFlow: StateFlow<String> = _value.asStateFlow()

    override fun onValueChanged(newValue: String) {
        if (options.contains(newValue)) {
            _value.value = newValue
        }
    }
}

class PassengerSeatHeatingItem : UiChoiceItem {
    override val key: String = "passenger_seat_heat"
    override val titleRes: Int = R.string.seat_item_passenger_heat_title
    override val subtitleRes: Int = R.string.seat_item_passenger_heat_subtitle
    override val iconRes: Int = R.drawable.ic_feature_seat

    override val choiceOptions: List<UiOption<String>> = listOf(
        UiOption("OFF", R.string.seat_heat_off),
        UiOption("LEVEL 1", R.string.seat_heat_level_1),
        UiOption("LEVEL 2", R.string.seat_heat_level_2),
        UiOption("LEVEL 3", R.string.seat_heat_level_3)
    )

    private val _value = MutableStateFlow("OFF")
    override val valueFlow: StateFlow<String> = _value.asStateFlow()

    override fun onValueChanged(newValue: String) {
        if (options.contains(newValue)) {
            _value.value = newValue
        }
    }
}

/**
 * Variant 1: Icon-Only Slot ([ChoiceOptionSlots.IconOnly])
 * Compact, icon-centric airflow buttons without text labels inside buttons.
 */
class DriverSeatVentilationItem : UiChoiceItem {
    override val key: String = "driver_seat_vent"
    override val titleRes: Int = R.string.seat_item_driver_vent_title
    override val subtitleRes: Int = R.string.seat_item_driver_vent_subtitle
    override val iconRes: Int = R.drawable.ic_seat_ventilation

    override val choiceOptions: List<UiOption<String>> = listOf(
        UiOption("OFF", R.string.seat_vent_off, iconRes = R.drawable.ic_seat_ventilation),
        UiOption("LEVEL 1", R.string.seat_vent_1, iconRes = R.drawable.ic_seat_ventilation),
        UiOption("LEVEL 2", R.string.seat_vent_2, iconRes = R.drawable.ic_seat_ventilation),
        UiOption("LEVEL 3", R.string.seat_vent_3, iconRes = R.drawable.ic_seat_ventilation)
    )

    // ⭐ Reusable Built-in Slot Variant: IconOnly
    override val optionSlot = ChoiceOptionSlots.IconOnly

    private val _value = MutableStateFlow("OFF")
    override val valueFlow: StateFlow<String> = _value.asStateFlow()

    override fun onValueChanged(newValue: String) {
        if (options.contains(newValue)) {
            _value.value = newValue
        }
    }
}

/**
 * Variant 2: Chip Slot ([ChoiceOptionSlots.Chip])
 * Compact filter chip buttons with optional badges.
 */
class SeatMassageModeItem : UiChoiceItem {
    override val key: String = "seat_massage"
    override val titleRes: Int = R.string.seat_item_massage_title
    override val subtitleRes: Int = R.string.seat_item_massage_subtitle
    override val iconRes: Int = R.drawable.ic_feature_seat

    override val choiceOptions: List<UiOption<String>> = listOf(
        UiOption("OFF", R.string.massage_off),
        UiOption("WAVE", R.string.massage_wave, badge = "추천"),
        UiOption("LUMBAR", R.string.massage_lumbar),
        UiOption("STRETCH", R.string.massage_stretch)
    )

    // ⭐ Reusable Built-in Slot Variant: Chip
    override val optionSlot = ChoiceOptionSlots.Chip

    private val _value = MutableStateFlow("OFF")
    override val valueFlow: StateFlow<String> = _value.asStateFlow()

    override fun onValueChanged(newValue: String) {
        if (options.contains(newValue)) {
            _value.value = newValue
        }
    }
}

// ============================================================================
// 2. Standard Toggle Item (UiToggleItem : ToggleItem, UiItem<Boolean>)
// ============================================================================

class EasyEntryExitItem : UiToggleItem {
    override val key: String = "easy_entry_exit"
    override val titleRes: Int = R.string.seat_item_easy_entry_title
    override val subtitleRes: Int = R.string.seat_item_easy_entry_subtitle
    override val iconRes: Int = R.drawable.ic_feature_seat

    private val _value = MutableStateFlow(true)
    override val valueFlow: StateFlow<Boolean> = _value.asStateFlow()

    override fun onValueChanged(newValue: Boolean) {
        _value.value = newValue
    }
}

// ============================================================================
// 3. Custom DataType (Item<SeatLumbarSupport>, UiItem<SeatLumbarSupport>)
// ============================================================================

/**
 * Custom Data Class representing 2D Pneumatic Lumbar Support coordinates.
 * Demonstrates how complex non-primitive state integrates with Item<T>.
 */
data class SeatLumbarSupport(
    val heightPercent: Int, // 0..100% (Vertical position)
    val depthPercent: Int   // 0..100% (Support firmness/extension)
)

/**
 * Setting Item using the Custom DataType.
 * Implements UiItem so it can draw itself polymorphically
 * without GenericSettingsActivity needing to know its concrete class.
 */
class SeatLumbarSupportItem : Item<SeatLumbarSupport>, UiItem<SeatLumbarSupport> {
    override val key: String = "seat_lumbar"
    override val titleRes: Int = R.string.seat_item_lumbar_title
    override val subtitleRes: Int = R.string.seat_item_lumbar_subtitle
    override val iconRes: Int = R.drawable.ic_feature_seat

    private val _value = MutableStateFlow(SeatLumbarSupport(heightPercent = 50, depthPercent = 30))
    override val valueFlow: StateFlow<SeatLumbarSupport> = _value.asStateFlow()

    override fun onValueChanged(newValue: SeatLumbarSupport) {
        _value.value = newValue
    }

    @androidx.compose.runtime.Composable
    override fun Draw() {
        SeatLumbarRow(item = this)
    }

    override val serializedValue: String
        get() = "${valueFlow.value.heightPercent},${valueFlow.value.depthPercent}"

    fun updateFromSerialized(raw: String) {
        val parts = raw.split(",")
        if (parts.size == 2) {
            val h = parts[0].trim().toIntOrNull() ?: 50
            val d = parts[1].trim().toIntOrNull() ?: 30
            onValueChanged(SeatLumbarSupport(heightPercent = h, depthPercent = d))
        }
    }
}

// ============================================================================
// Registry SSOT
// ============================================================================

object SeatItemRegistry : com.example.core.item.CategoryItemProvider {
    override val categoryId: String = "seat"
    override val authority: String = SeatSettingsProvider.AUTHORITY
    override val titleKey: String = "category_seat_title"

    val driverSeatHeat = DriverSeatHeatingItem()
    val driverSeatVent = DriverSeatVentilationItem()
    val seatMassage = SeatMassageModeItem()
    val passengerSeatHeat = PassengerSeatHeatingItem()
    val easyEntryExit = EasyEntryExitItem()
    val seatLumbar = SeatLumbarSupportItem()

    override val items: List<Item<*>> = listOf(
        driverSeatHeat,
        driverSeatVent,
        seatMassage,
        passengerSeatHeat,
        easyEntryExit,
        seatLumbar
    )
}
