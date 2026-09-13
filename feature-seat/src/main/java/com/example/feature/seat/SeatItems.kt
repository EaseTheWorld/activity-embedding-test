package com.example.feature.seat

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.common.ui.settings.AppScope
import com.example.common.ui.settings.CarUiOption
import com.example.common.ui.settings.ChoiceHardwareItemViewModel
import com.example.common.ui.settings.ChoiceOptionSlots
import com.example.common.ui.settings.HardwarePropertyStorage
import com.example.common.ui.settings.InMemoryHardwareStorage
import com.example.common.ui.settings.LocalStorageItemViewModel
import com.example.common.ui.settings.UiChoiceItem
import com.example.common.ui.settings.UiItem
import com.example.common.ui.settings.UiOption
import com.example.common.ui.settings.UiToggleItem
import com.example.common.ui.settings.VhalChoiceItem
import com.example.core.item.Item
import com.example.core.item.ItemType
import com.example.core.item.ItemViewModel
import com.example.core.item.ItemViewModelBinding
import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.SettingCatalog
import com.example.core.item.ValueMapping
import com.example.core.item.VehicleProperty
import com.example.core.item.bindsTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ============================================================================
// Layer 1: Canonical Catalog SSOT
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
            choiceOptions = listOf(
                UiOption("OFF", R.string.seat_heat_off),
                UiOption("LEVEL 1", R.string.seat_heat_level_1),
                UiOption("LEVEL 2", R.string.seat_heat_level_2),
                UiOption("LEVEL 3", R.string.seat_heat_level_3)
            )
        )
    )

    val passengerSeatHeat: UiChoiceItem = item(
        UiChoiceItem(
            id = "passenger_seat_heat",
            nameResId = R.string.seat_item_passenger_heat_title,
            descriptionResId = R.string.seat_item_passenger_heat_subtitle,
            iconResId = R.drawable.ic_feature_seat,
            choiceOptions = listOf(
                UiOption("OFF", R.string.seat_heat_off),
                UiOption("LEVEL 1", R.string.seat_heat_level_1),
                UiOption("LEVEL 2", R.string.seat_heat_level_2),
                UiOption("LEVEL 3", R.string.seat_heat_level_3)
            )
        )
    )

    val driverSeatVent: UiChoiceItem = item(
        UiChoiceItem(
            id = "driver_seat_vent",
            nameResId = R.string.seat_item_driver_vent_title,
            descriptionResId = R.string.seat_item_driver_vent_subtitle,
            iconResId = R.drawable.ic_seat_ventilation,
            optionSlot = ChoiceOptionSlots.IconOnly,
            choiceOptions = listOf(
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
            optionSlot = ChoiceOptionSlots.Chip,
            choiceOptions = listOf(
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
// Layer 2: Hardware / HAL Layer (Pure Infrastructure, Zero UI Dependencies)
// ============================================================================

/**
 * Automotive Vehicle HAL Properties for Seat controls.
 * ValueMappings are constructed dynamically from the Catalog's option IDs via [ValueMapping.fromOptions].
 * ZERO raw strings are declared in this layer!
 */
object SeatVehicleProperties {
    val DRIVER_HEAT: VehicleProperty<String, Int> = VehicleProperty(
        propertyId = 0x11400503,
        areaId = 1,
        mapper = ValueMapping.fromOptions(SeatCatalog.driverSeatHeat.optionIds),
        defaultValue = SeatCatalog.driverSeatHeat.optionIds.first()
    )

    val PASSENGER_HEAT: VehicleProperty<String, Int> = VehicleProperty(
        propertyId = 0x11400503,
        areaId = 2,
        mapper = ValueMapping.fromOptions(SeatCatalog.passengerSeatHeat.optionIds),
        defaultValue = SeatCatalog.passengerSeatHeat.optionIds.first()
    )

    val DRIVER_VENT: VehicleProperty<String, Int> = VehicleProperty(
        propertyId = 0x11400504,
        areaId = 1,
        mapper = ValueMapping.fromOptions(SeatCatalog.driverSeatVent.optionIds),
        defaultValue = SeatCatalog.driverSeatVent.optionIds.first()
    )

    val MASSAGE_MODE: VehicleProperty<String, Int> = VehicleProperty(
        propertyId = 0x11400F00,
        areaId = 1,
        mapper = ValueMapping.fromOptions(SeatCatalog.massageMode.optionIds),
        defaultValue = SeatCatalog.massageMode.optionIds.first()
    )
}

// ============================================================================
// Layer 3: Assembler / DI Module Layer (The Sole Coupling Point)
// ============================================================================

/**
 * Provides the complete set of [ItemViewModelBinding] for Seat settings.
 * Bridges UI Catalog items with their corresponding ChoiceHardwareItemViewModels.
 */
object SeatViewModelModule {
    fun provideSeatBindings(
        hardwareStorage: HardwarePropertyStorage,
        disabledMassageOptionsFlow: StateFlow<Set<String>> = MutableStateFlow(emptySet()),
        lumbarViewModel: ItemViewModel<SeatLumbarSupport> = SeatItemRegistry.seatLumbarViewModel,
        scope: CoroutineScope = AppScope.scope
    ): Set<ItemViewModelBinding<*>> = setOf(
        SeatCatalog.massageMode bindsTo ChoiceHardwareItemViewModel(
            property = SeatVehicleProperties.MASSAGE_MODE,
            supportedOptionIds = SeatCatalog.massageMode.optionIds,
            storage = hardwareStorage,
            disabledOptionIdsFlow = disabledMassageOptionsFlow,
            scope = scope
        ),
        SeatCatalog.driverSeatVent bindsTo ChoiceHardwareItemViewModel(
            property = SeatVehicleProperties.DRIVER_VENT,
            supportedOptionIds = SeatCatalog.driverSeatVent.optionIds,
            storage = hardwareStorage,
            scope = scope
        ),
        SeatCatalog.driverSeatHeat bindsTo ChoiceHardwareItemViewModel(
            property = SeatVehicleProperties.DRIVER_HEAT,
            supportedOptionIds = SeatCatalog.driverSeatHeat.optionIds,
            storage = hardwareStorage,
            scope = scope
        ),
        SeatCatalog.passengerSeatHeat bindsTo ChoiceHardwareItemViewModel(
            property = SeatVehicleProperties.PASSENGER_HEAT,
            supportedOptionIds = SeatCatalog.passengerSeatHeat.optionIds,
            storage = hardwareStorage,
            scope = scope
        ),
        SeatCatalog.easyEntryExit bindsTo LocalStorageItemViewModel(
            initialValue = true,
            scope = scope
        ),
        SeatCatalog.seatLumbar bindsTo lumbarViewModel
    )
}

/**
 * Binds ViewModels for Seat settings running within an ApplicationScope.
 */
object SeatViewModelBinder {
    fun bindAll(
        registry: ItemViewModelRegistry,
        hardwareStorage: HardwarePropertyStorage = InMemoryHardwareStorage().apply {
            setInitialValue(SeatVehicleProperties.MASSAGE_MODE, "OFF")
            setInitialValue(SeatVehicleProperties.DRIVER_VENT, "OFF")
            setInitialValue(SeatVehicleProperties.DRIVER_HEAT, "OFF")
            setInitialValue(SeatVehicleProperties.PASSENGER_HEAT, "OFF")
        },
        disabledMassageOptionsFlow: StateFlow<Set<String>> = MutableStateFlow(emptySet()),
        lumbarViewModel: ItemViewModel<SeatLumbarSupport> = SeatItemRegistry.seatLumbarViewModel,
        scope: CoroutineScope = AppScope.scope
    ) {
        val bindings = SeatViewModelModule.provideSeatBindings(
            hardwareStorage = hardwareStorage,
            disabledMassageOptionsFlow = disabledMassageOptionsFlow,
            lumbarViewModel = lumbarViewModel,
            scope = scope
        )
        registry.registerAll(bindings)
    }
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
    binder = repository
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
    binder = repository
)

/**
 * Variant 1: Icon-Only Slot ([ChoiceOptionSlots.IconOnly])
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
    optionSlot = ChoiceOptionSlots.Chip,
    binder = repository
)

// ============================================================================
// 2. Standard Toggle Item (UiToggleItem)
// ============================================================================

class EasyEntryExitItem : UiToggleItem(
    id = SeatCatalog.easyEntryExit.id,
    nameResId = R.string.seat_item_easy_entry_title,
    descriptionResId = R.string.seat_item_easy_entry_subtitle,
    iconResId = R.drawable.ic_feature_seat
)

// ============================================================================
// 3. Custom DataType & ViewModel
// ============================================================================

/**
 * Custom Data Class representing 2D Pneumatic Lumbar Support coordinates.
 */
data class SeatLumbarSupport(
    val heightPercent: Int = 50, // 0..100% (Vertical position)
    val depthPercent: Int = 30   // 0..100% (Support firmness/extension)
) {
    fun toSerialized(): String = "$heightPercent,$depthPercent"

    companion object {
        val DEFAULT = SeatLumbarSupport(heightPercent = 50, depthPercent = 30)

        fun fromSerialized(raw: String, fallback: SeatLumbarSupport = DEFAULT): SeatLumbarSupport {
            val parts = raw.split(",")
            if (parts.size == 2) {
                val h = parts[0].trim().toIntOrNull() ?: fallback.heightPercent
                val d = parts[1].trim().toIntOrNull() ?: fallback.depthPercent
                return SeatLumbarSupport(heightPercent = h, depthPercent = d)
            }
            return fallback
        }
    }
}

/**
 * Setting Item using the Custom DataType.
 * Pure UI Presentation Metadata (Stateless). Does NOT hold valueFlow or mutate state directly.
 * State ownership belongs strictly to [SeatLumbarViewModel].
 */
class SeatLumbarSupportItem(
    id: String = "seat_lumbar",
    @StringRes nameResId: Int = R.string.seat_item_lumbar_title,
    @StringRes descriptionResId: Int? = R.string.seat_item_lumbar_subtitle,
    @DrawableRes iconResId: Int? = R.drawable.ic_feature_seat
) : UiItem(
    id = id,
    nameResId = nameResId,
    iconResId = iconResId,
    descriptionResId = descriptionResId
) {
    override val type: ItemType get() = ItemType.CUSTOM
}

/**
 * Dedicated ItemViewModel managing reactive domain state for [SeatLumbarSupportItem].
 * Owns [valueFlow] and receives user mutations via [setValue].
 * Also handles IPC serialization / deserialization roundtrips.
 */
class SeatLumbarViewModel(
    initialValue: SeatLumbarSupport = SeatLumbarSupport.DEFAULT
) : ItemViewModel<SeatLumbarSupport> {

    private val _valueFlow = MutableStateFlow(initialValue)
    override val valueFlow: StateFlow<SeatLumbarSupport> = _valueFlow.asStateFlow()

    override fun setValue(newValue: SeatLumbarSupport) {
        _valueFlow.value = newValue
    }

    /**
     * Backward-compatibility or direct update hook.
     */
    fun onValueChanged(newValue: SeatLumbarSupport) {
        setValue(newValue)
    }

    /**
     * Deserializes wire payload from IPC (ContentProvider) and updates state.
     */
    fun updateFromSerialized(raw: String) {
        val updated = SeatLumbarSupport.fromSerialized(raw, fallback = _valueFlow.value)
        setValue(updated)
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
    val seatLumbar = SeatCatalog.seatLumbar
    val seatLumbarViewModel = SeatLumbarViewModel()

    override val items: List<Item> = listOf(
        driverSeatHeat,
        driverSeatVent,
        seatMassage,
        passengerSeatHeat,
        easyEntryExit,
        seatLumbar
    )
}
