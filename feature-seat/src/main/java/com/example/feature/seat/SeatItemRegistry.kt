package com.example.feature.seat

import com.example.core.item.CategoryItemProvider
import com.example.core.item.Item

// ============================================================================
// Registry SSOT for Category Discovery & Flat Indexing
// ============================================================================

object SeatItemRegistry : CategoryItemProvider {
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
