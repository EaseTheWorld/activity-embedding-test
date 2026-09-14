package com.example.feature.seat

import com.example.core.item.ValueMapping
import com.example.core.item.VehicleProperty

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
