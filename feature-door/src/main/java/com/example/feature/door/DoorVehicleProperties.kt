package com.example.feature.door

import com.example.core.item.ValueMapping
import com.example.core.item.VehicleProperty

// ============================================================================
// Layer 2: Hardware / HAL Layer (Pure Infrastructure, Zero UI Dependencies)
// ============================================================================

/**
 * Automotive Vehicle HAL Properties for Door controls.
 * Encapsulates Property ID and ValueMapping without any knowledge of Android UI.
 */
object DoorVehicleProperties {

    /**
     * Factory function creating a standard boolean VHAL property.
     */
    fun booleanProp(propertyId: Int): VehicleProperty<Boolean, Int> =
        VehicleProperty(propertyId = propertyId, mapper = ValueMapping.BooleanToInt)

    val AUTO_LOCK = booleanProp(0x11400bc0)
    val CHILD_LOCK = booleanProp(0x11400bc1)
    val AUTO_RELOCK = booleanProp(0x11400bc2)
}
