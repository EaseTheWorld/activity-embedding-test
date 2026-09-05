package com.example.feature.seat

import com.example.common.ui.settings.ComposableChoiceItem
import com.example.common.ui.settings.ComposableItemRenderer
import com.example.common.ui.settings.ComposableToggleItem
import com.example.core.item.Item
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ============================================================================
// 1. Standard Choice Items (ComposableChoiceItem : ChoiceItem, ComposableItemRenderer)
// ============================================================================

class DriverSeatHeatingItem : ComposableChoiceItem {
    override val key: String = "driver_seat_heat"
    override val titleKey: String = "seat_item_driver_heat_title"
    override val subtitleKey: String = "seat_item_driver_heat_subtitle"
    override val iconKey: String = "ic_feature_seat"
    override val options: List<String> = listOf("OFF", "LEVEL 1", "LEVEL 2", "LEVEL 3")

    private val _value = MutableStateFlow("OFF")
    override val valueFlow: StateFlow<String> = _value.asStateFlow()

    override fun onValueChanged(newValue: String) {
        if (options.contains(newValue)) {
            _value.value = newValue
        }
    }
}

class PassengerSeatHeatingItem : ComposableChoiceItem {
    override val key: String = "passenger_seat_heat"
    override val titleKey: String = "seat_item_passenger_heat_title"
    override val subtitleKey: String = "seat_item_passenger_heat_subtitle"
    override val iconKey: String = "ic_feature_seat"
    override val options: List<String> = listOf("OFF", "LEVEL 1", "LEVEL 2", "LEVEL 3")

    private val _value = MutableStateFlow("OFF")
    override val valueFlow: StateFlow<String> = _value.asStateFlow()

    override fun onValueChanged(newValue: String) {
        if (options.contains(newValue)) {
            _value.value = newValue
        }
    }
}

// ============================================================================
// 2. Standard Toggle Item (ComposableToggleItem : ToggleItem, ComposableItemRenderer)
// ============================================================================

class EasyEntryExitItem : ComposableToggleItem {
    override val key: String = "easy_entry_exit"
    override val titleKey: String = "seat_item_easy_entry_title"
    override val subtitleKey: String = "seat_item_easy_entry_subtitle"
    override val iconKey: String = "ic_feature_seat"

    private val _value = MutableStateFlow(true)
    override val valueFlow: StateFlow<Boolean> = _value.asStateFlow()

    override fun onValueChanged(newValue: Boolean) {
        _value.value = newValue
    }
}

// ============================================================================
// 3. Custom DataType (Item<SeatLumbarSupport>)
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
 * Implements ComposableItemRenderer so it can draw itself polymorphically
 * without GenericSettingsActivity needing to know its concrete class.
 */
class SeatLumbarSupportItem : Item<SeatLumbarSupport>, ComposableItemRenderer {
    override val key: String = "seat_lumbar"
    override val titleKey: String = "seat_item_lumbar_title"
    override val subtitleKey: String = "seat_item_lumbar_subtitle"
    override val iconKey: String = "ic_feature_seat"

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
    val passengerSeatHeat = PassengerSeatHeatingItem()
    val easyEntryExit = EasyEntryExitItem()
    val seatLumbar = SeatLumbarSupportItem()

    override val items: List<Item<*>> = listOf(
        driverSeatHeat,
        passengerSeatHeat,
        easyEntryExit,
        seatLumbar
    )
}
