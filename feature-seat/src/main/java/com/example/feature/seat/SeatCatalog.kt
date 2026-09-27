package com.example.feature.seat

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.common.ui.settings.CarUiOption
import com.example.common.ui.settings.OptionSlots
import com.example.common.ui.settings.UiChoiceItem
import com.example.common.ui.settings.UiItem
import com.example.common.ui.settings.UiOption
import com.example.common.ui.settings.UiToggleItem
import com.example.common.ui.settings.VhalChoiceItem
import com.example.core.item.ItemCapability
import com.example.core.item.ItemType
import com.example.core.item.ParameterSpec
import com.example.core.item.SettingCatalog

// ============================================================================
// Layer 1: UI Presentation Layer (Canonical SSOT Definition)
// ============================================================================

/**
 * Type-safe Canonical Catalog for Seat Settings.
 * Declares Item ID ("seat_massage"), Option IDs ("OFF", "WAVE", ...),
 * and UI presentation metadata (strings, icons, slots) in EXACTLY ONE PLACE.
 */
object SeatCatalog : SettingCatalog("seat") {

    val driverSeatHeat: UiChoiceItem = item(
        UiChoiceItem(
            id = "driver_seat_heat",
            nameResId = R.string.seat_item_driver_heat_title,
            descriptionResId = R.string.seat_item_driver_heat_subtitle,
            iconResId = R.drawable.ic_feature_seat,
            options = listOf(
                UiOption("OFF", R.string.seat_heat_off),
                UiOption("LEVEL 1", R.string.seat_heat_level_1),
                UiOption("LEVEL 2", R.string.seat_heat_level_2),
                UiOption("LEVEL 3", R.string.seat_heat_level_3)
            ),
            keywords = listOf("운전석열선", "엉뜨", "시트히터", "운전석시트열선", "driver seat heat")
        )
    )

    val passengerSeatHeat: UiChoiceItem = item(
        UiChoiceItem(
            id = "passenger_seat_heat",
            nameResId = R.string.seat_item_passenger_heat_title,
            descriptionResId = R.string.seat_item_passenger_heat_subtitle,
            iconResId = R.drawable.ic_feature_seat,
            options = listOf(
                UiOption("OFF", R.string.seat_heat_off),
                UiOption("LEVEL 1", R.string.seat_heat_level_1),
                UiOption("LEVEL 2", R.string.seat_heat_level_2),
                UiOption("LEVEL 3", R.string.seat_heat_level_3)
            ),
            keywords = listOf("조수석열선", "조수석엉뜨", "조수석시트히터", "passenger seat heat")
        )
    )

    val driverSeatVent: UiChoiceItem = item(
        UiChoiceItem(
            id = "driver_seat_vent",
            nameResId = R.string.seat_item_driver_vent_title,
            descriptionResId = R.string.seat_item_driver_vent_subtitle,
            iconResId = R.drawable.ic_seat_ventilation,
            optionSlot = OptionSlots.IconOnly,
            options = listOf(
                UiOption("OFF", R.string.seat_vent_off, iconRes = R.drawable.ic_seat_ventilation),
                UiOption("LEVEL 1", R.string.seat_vent_1, iconRes = R.drawable.ic_seat_ventilation),
                UiOption("LEVEL 2", R.string.seat_vent_2, iconRes = R.drawable.ic_seat_ventilation),
                UiOption("LEVEL 3", R.string.seat_vent_3, iconRes = R.drawable.ic_seat_ventilation)
            )
        )
    )

    val massageMode: UiChoiceItem = item(
        UiChoiceItem(
            id = "seat_massage",
            nameResId = R.string.seat_item_massage_title,
            descriptionResId = R.string.seat_item_massage_subtitle,
            iconResId = R.drawable.ic_feature_seat,
            optionSlot = OptionSlots.Chip,
            options = listOf(
                UiOption("OFF", R.string.massage_off),
                UiOption("WAVE", R.string.massage_wave, badge = "추천"),
                UiOption("LUMBAR", R.string.massage_lumbar),
                UiOption("STRETCH", R.string.massage_stretch)
            )
        )
    )

    val easyEntryExit: UiToggleItem = item(
        UiToggleItem(
            id = "easy_entry_exit",
            nameResId = R.string.seat_item_easy_entry_title,
            descriptionResId = R.string.seat_item_easy_entry_subtitle,
            iconResId = R.drawable.ic_feature_seat
        )
    )

    val seatLumbar: SeatLumbarSupportItem = item(
        SeatLumbarSupportItem()
    )
}

// ============================================================================
// Standard & Custom UI Presentation Items
// ============================================================================

class EasyEntryExitItem : UiToggleItem(
    id = SeatCatalog.easyEntryExit.id,
    nameResId = R.string.seat_item_easy_entry_title,
    descriptionResId = R.string.seat_item_easy_entry_subtitle,
    iconResId = R.drawable.ic_feature_seat,
    keywords = listOf("이지엔트리", "승하차편의", "시트자동이동", "easy entry")
)

/**
 * Setting Item using the Custom DataType.
 * Pure UI Presentation Metadata (Stateless). Does NOT hold valueFlow or mutate state directly.
 * State ownership belongs strictly to [SeatLumbarViewModel].
 */
class SeatLumbarSupportItem(
    id: String = "seat_lumbar",
    @StringRes nameResId: Int = R.string.seat_item_lumbar_title,
    @StringRes descriptionResId: Int? = R.string.seat_item_lumbar_subtitle,
    @DrawableRes iconResId: Int? = R.drawable.ic_feature_seat,
    keywords: List<String> = listOf("요추", "럼버서포트", "허리받침", "허리지지대", "lumbar support")
) : UiItem(
    id = id,
    nameResId = nameResId,
    iconResId = iconResId,
    descriptionResId = descriptionResId,
    keywords = keywords
) {
    override val type: ItemType get() = ItemType.CUSTOM
    override val hasDetailScreen: Boolean get() = true

    override val capability: ItemCapability get() = ItemCapability(
        itemId = id,
        actionType = ItemType.CUSTOM,
        acceptedParameters = listOf(
            ParameterSpec(name = "height", type = "int", min = 0, max = 100, description = "Lumbar height percentage (0..100)"),
            ParameterSpec(name = "depth", type = "int", min = 0, max = 100, description = "Lumbar depth percentage (0..100)"),
            ParameterSpec(name = "value", type = "string", description = "Combined height,depth string")
        ),
        keywords = keywords
    )
}

// ============================================================================
// Legacy VHAL Choice Items (Kept for backwards compatibility)
// ============================================================================

class DriverSeatHeatingItem(
    repository: SeatPropertyRepository = SeatPropertyRepositoryImpl.shared
) : VhalChoiceItem<Int>(
    id = SeatCatalog.driverSeatHeat.id,
    nameResId = R.string.seat_item_driver_heat_title,
    descriptionResId = R.string.seat_item_driver_heat_subtitle,
    iconResId = R.drawable.ic_feature_seat,
    propertyId = SeatVehicleProperties.DRIVER_HEAT.propertyId,
    areaId = 1,              // SEAT_ROW_1_LEFT
    carOptions = listOf(
        CarUiOption("OFF", R.string.seat_heat_off, vhalValue = 0),
        CarUiOption("LEVEL 1", R.string.seat_heat_level_1, vhalValue = 1),
        CarUiOption("LEVEL 2", R.string.seat_heat_level_2, vhalValue = 2),
        CarUiOption("LEVEL 3", R.string.seat_heat_level_3, vhalValue = 3)
    ),
    initialValue = "OFF",
    binder = repository,
    keywords = listOf("운전석열선", "엉뜨", "시트히터", "운전석시트열선", "driver seat heat")
)

class PassengerSeatHeatingItem(
    repository: SeatPropertyRepository = SeatPropertyRepositoryImpl.shared
) : VhalChoiceItem<Int>(
    id = SeatCatalog.passengerSeatHeat.id,
    nameResId = R.string.seat_item_passenger_heat_title,
    descriptionResId = R.string.seat_item_passenger_heat_subtitle,
    iconResId = R.drawable.ic_feature_seat,
    propertyId = SeatVehicleProperties.PASSENGER_HEAT.propertyId,
    areaId = 2,              // SEAT_ROW_1_RIGHT
    carOptions = listOf(
        CarUiOption("OFF", R.string.seat_heat_off, vhalValue = 0),
        CarUiOption("LEVEL 1", R.string.seat_heat_level_1, vhalValue = 1),
        CarUiOption("LEVEL 2", R.string.seat_heat_level_2, vhalValue = 2),
        CarUiOption("LEVEL 3", R.string.seat_heat_level_3, vhalValue = 3)
    ),
    initialValue = "OFF",
    binder = repository,
    keywords = listOf("조수석열선", "조수석엉뜨", "조수석시트히터", "passenger seat heat")
)

/**
 * Variant 1: Icon-Only Slot ([OptionSlots.IconOnly])
 * Compact, icon-centric airflow buttons without text labels inside buttons.
 */
class DriverSeatVentilationItem(
    repository: SeatPropertyRepository = SeatPropertyRepositoryImpl.shared
) : VhalChoiceItem<Int>(
    id = SeatCatalog.driverSeatVent.id,
    nameResId = R.string.seat_item_driver_vent_title,
    descriptionResId = R.string.seat_item_driver_vent_subtitle,
    iconResId = R.drawable.ic_seat_ventilation,
    propertyId = 0x11400504, // HVAC_SEAT_VENTILATION
    areaId = 1,              // SEAT_ROW_1_LEFT
    carOptions = listOf(
        CarUiOption("OFF", R.string.seat_vent_off, iconRes = R.drawable.ic_seat_ventilation, vhalValue = 0),
        CarUiOption("LEVEL 1", R.string.seat_vent_1, iconRes = R.drawable.ic_seat_ventilation, vhalValue = 1),
        CarUiOption("LEVEL 2", R.string.seat_vent_2, iconRes = R.drawable.ic_seat_ventilation, vhalValue = 2),
        CarUiOption("LEVEL 3", R.string.seat_vent_3, iconRes = R.drawable.ic_seat_ventilation, vhalValue = 3)
    ),
    initialValue = "OFF",
    optionSlot = OptionSlots.IconOnly,
    binder = repository,
    keywords = listOf("운전석통풍", "통풍시트", "엉시", "시트통풍", "driver seat vent")
)

/**
 * Variant 2: Chip Slot ([OptionSlots.Chip])
 * Compact filter chip buttons with optional badges.
 */
class SeatMassageModeItem(
    repository: SeatPropertyRepository = SeatPropertyRepositoryImpl.shared
) : VhalChoiceItem<Int>(
    id = SeatCatalog.massageMode.id,
    nameResId = R.string.seat_item_massage_title,
    descriptionResId = R.string.seat_item_massage_subtitle,
    iconResId = R.drawable.ic_feature_seat,
    propertyId = SeatVehicleProperties.MASSAGE_MODE.propertyId,
    areaId = 1,              // SEAT_ROW_1_LEFT
    carOptions = listOf(
        CarUiOption("OFF", R.string.massage_off, vhalValue = 0),
        CarUiOption("WAVE", R.string.massage_wave, badge = "추천", vhalValue = 1),
        CarUiOption("LUMBAR", R.string.massage_lumbar, vhalValue = 2),
        CarUiOption("STRETCH", R.string.massage_stretch, vhalValue = 3)
    ),
    initialValue = "OFF",
    optionSlot = OptionSlots.Chip,
    binder = repository,
    keywords = listOf("마사지", "시트마사지", "안마시트", "seat massage")
)
