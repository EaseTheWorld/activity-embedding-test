package com.example.feature.seat

import com.example.common.ui.settings.BaseUiToggleItem
import com.example.common.ui.settings.CarUiOption
import com.example.common.ui.settings.ChoiceOptionSlots
import com.example.common.ui.settings.UiItem
import com.example.common.ui.settings.UiValueItem
import com.example.common.ui.settings.VhalChoiceItem
import com.example.core.item.Item
import com.example.core.item.ValueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ============================================================================
// 1. Standard VHAL Choice Items (VhalChoiceItem<Int> : BaseUiChoiceItem, VhalBoundItem<String, Int>)
// ============================================================================

class DriverSeatHeatingItem(
    repository: SeatPropertyRepository = SeatPropertyRepositoryImpl.shared
) : VhalChoiceItem<Int>(
    key = "driver_seat_heat",
    titleRes = R.string.seat_item_driver_heat_title,
    subtitleRes = R.string.seat_item_driver_heat_subtitle,
    iconRes = R.drawable.ic_feature_seat,
    propertyId = 0x11400503, // HVAC_SEAT_TEMPERATURE
    areaId = 1,              // SEAT_ROW_1_LEFT
    carOptions = listOf(
        CarUiOption("OFF", R.string.seat_heat_off, vhalValue = 0),
        CarUiOption("LEVEL 1", R.string.seat_heat_level_1, vhalValue = 1),
        CarUiOption("LEVEL 2", R.string.seat_heat_level_2, vhalValue = 2),
        CarUiOption("LEVEL 3", R.string.seat_heat_level_3, vhalValue = 3)
    ),
    initialValue = "OFF",
    binder = repository
)

class PassengerSeatHeatingItem(
    repository: SeatPropertyRepository = SeatPropertyRepositoryImpl.shared
) : VhalChoiceItem<Int>(
    key = "passenger_seat_heat",
    titleRes = R.string.seat_item_passenger_heat_title,
    subtitleRes = R.string.seat_item_passenger_heat_subtitle,
    iconRes = R.drawable.ic_feature_seat,
    propertyId = 0x11400503, // HVAC_SEAT_TEMPERATURE
    areaId = 2,              // SEAT_ROW_1_RIGHT
    carOptions = listOf(
        CarUiOption("OFF", R.string.seat_heat_off, vhalValue = 0),
        CarUiOption("LEVEL 1", R.string.seat_heat_level_1, vhalValue = 1),
        CarUiOption("LEVEL 2", R.string.seat_heat_level_2, vhalValue = 2),
        CarUiOption("LEVEL 3", R.string.seat_heat_level_3, vhalValue = 3)
    ),
    initialValue = "OFF",
    binder = repository
)

/**
 * Variant 1: Icon-Only Slot ([ChoiceOptionSlots.IconOnly])
 * Compact, icon-centric airflow buttons without text labels inside buttons.
 */
class DriverSeatVentilationItem(
    repository: SeatPropertyRepository = SeatPropertyRepositoryImpl.shared
) : VhalChoiceItem<Int>(
    key = "driver_seat_vent",
    titleRes = R.string.seat_item_driver_vent_title,
    subtitleRes = R.string.seat_item_driver_vent_subtitle,
    iconRes = R.drawable.ic_seat_ventilation,
    propertyId = 0x11400504, // HVAC_SEAT_VENTILATION
    areaId = 1,              // SEAT_ROW_1_LEFT
    carOptions = listOf(
        CarUiOption("OFF", R.string.seat_vent_off, iconRes = R.drawable.ic_seat_ventilation, vhalValue = 0),
        CarUiOption("LEVEL 1", R.string.seat_vent_1, iconRes = R.drawable.ic_seat_ventilation, vhalValue = 1),
        CarUiOption("LEVEL 2", R.string.seat_vent_2, iconRes = R.drawable.ic_seat_ventilation, vhalValue = 2),
        CarUiOption("LEVEL 3", R.string.seat_vent_3, iconRes = R.drawable.ic_seat_ventilation, vhalValue = 3)
    ),
    initialValue = "OFF",
    optionSlot = ChoiceOptionSlots.IconOnly,
    binder = repository
)

/**
 * Variant 2: Chip Slot ([ChoiceOptionSlots.Chip])
 * Compact filter chip buttons with optional badges.
 */
class SeatMassageModeItem(
    repository: SeatPropertyRepository = SeatPropertyRepositoryImpl.shared
) : VhalChoiceItem<Int>(
    key = "seat_massage",
    titleRes = R.string.seat_item_massage_title,
    subtitleRes = R.string.seat_item_massage_subtitle,
    iconRes = R.drawable.ic_feature_seat,
    propertyId = 0x11400F00, // SEAT_MASSAGE_MODE
    areaId = 1,              // SEAT_ROW_1_LEFT
    carOptions = listOf(
        CarUiOption("OFF", R.string.massage_off, vhalValue = 0),
        CarUiOption("WAVE", R.string.massage_wave, badge = "추천", vhalValue = 1),
        CarUiOption("LUMBAR", R.string.massage_lumbar, vhalValue = 2),
        CarUiOption("STRETCH", R.string.massage_stretch, vhalValue = 3)
    ),
    initialValue = "OFF",
    optionSlot = ChoiceOptionSlots.Chip,
    binder = repository
)

// ============================================================================
// 2. Standard Toggle Item (BaseUiToggleItem : UiToggleItem)
// ============================================================================

class EasyEntryExitItem : BaseUiToggleItem(
    key = "easy_entry_exit",
    titleRes = R.string.seat_item_easy_entry_title,
    subtitleRes = R.string.seat_item_easy_entry_subtitle,
    iconRes = R.drawable.ic_feature_seat,
    initialValue = true
)

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
 * Implements UiValueItem so it can draw itself polymorphically
 * without GenericSettingsActivity needing to know its concrete class.
 */
class SeatLumbarSupportItem : ValueItem<SeatLumbarSupport>, UiValueItem<SeatLumbarSupport> {
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
    override fun Draw(modifier: androidx.compose.ui.Modifier) {
        SeatLumbarRow(item = this, modifier = modifier)
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

    override val items: List<Item> = listOf(
        driverSeatHeat,
        driverSeatVent,
        seatMassage,
        passengerSeatHeat,
        easyEntryExit,
        seatLumbar
    )
}
