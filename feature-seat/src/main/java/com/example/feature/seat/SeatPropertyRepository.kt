package com.example.feature.seat

import com.example.core.item.VhalBoundItem
import com.example.core.item.VhalPropertyBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Automotive Data Layer Repository contract for Seat properties.
 *
 * Implements [VhalPropertyBinder] to seamlessly receive [VhalBoundItem]s from
 * the UI / Item layer via Inversion of Control.
 */
interface SeatPropertyRepository : VhalPropertyBinder {
    /**
     * Simulates an incoming raw hardware event from VHAL (e.g. from physical dial, ECU, or CarPropertyManager).
     * Automatically retrieves the bound [VhalBoundItem] by (propertyId, areaId), maps the raw value to
     * domain value via [VhalBoundItem.toItemValue], and emits to the live flow.
     */
    fun <V> onVhalHardwareEvent(propertyId: Int, areaId: Int, rawHardwareValue: V)

    /**
     * Backward-compatible alias for setProperty.
     */
    fun <T, V> setPropertyValue(item: VhalBoundItem<T, V>, newValue: T) = setProperty(item, newValue)

    /**
     * Backward-compatible alias for getPropertyFlow.
     */
    fun <T, V> getPropertyFlow(item: VhalBoundItem<T, V>): StateFlow<T> = bind(item, item.valueFlow.value)
}

/**
 * In-memory / VHAL implementation of [SeatPropertyRepository].
 *
 * Demonstrates:
 * 1. How [VhalBoundItem] is registered upon `bind(item, initialValue)`.
 * 2. How UI writes pass [VhalBoundItem] and convert to raw VHAL via [VhalBoundItem.toVhalValue].
 * 3. How incoming VHAL hardware events look up the [VhalBoundItem] and convert to domain via [VhalBoundItem.toItemValue].
 */
class SeatPropertyRepositoryImpl : SeatPropertyRepository {

    companion object {
        /** Singleton instance for default injection / static access */
        val shared: SeatPropertyRepository by lazy { SeatPropertyRepositoryImpl() }
    }

    // Registry of active VHAL bound items: (propertyId, areaId) -> VhalBoundItem
    private val boundItems = mutableMapOf<Pair<Int, Int>, VhalBoundItem<*, *>>()

    // Active live state flows: (propertyId, areaId) -> MutableStateFlow
    private val propertyStorage = mutableMapOf<Pair<Int, Int>, MutableStateFlow<Any?>>()

    @Suppress("UNCHECKED_CAST")
    override fun <T, V> bind(item: VhalBoundItem<T, V>, initialValue: T): StateFlow<T> {
        val key = item.propertyId to item.areaId
        boundItems[key] = item

        val flow = propertyStorage.getOrPut(key) {
            MutableStateFlow(initialValue)
        }
        return flow as StateFlow<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T, V> setProperty(item: VhalBoundItem<T, V>, newValue: T) {
        val key = item.propertyId to item.areaId

        // 1. Convert domain value to raw VHAL hardware value using the item's mapping
        val rawVhal = item.toVhalValue(newValue)

        // 2. In production AAOS: carPropertyManager.setProperty(item.propertyId, item.areaId, rawVhal)

        // 3. Update local StateFlow
        val flow = propertyStorage.getOrPut(key) {
            MutableStateFlow(newValue)
        } as MutableStateFlow<T>
        flow.value = item.toItemValue(rawVhal)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V> onVhalHardwareEvent(propertyId: Int, areaId: Int, rawHardwareValue: V) {
        val key = propertyId to areaId
        val item = boundItems[key] as? VhalBoundItem<Any?, V> ?: return

        // Translate raw hardware VHAL value to high-level domain value
        val domainValue = item.toItemValue(rawHardwareValue)

        val flow = propertyStorage[key]
        flow?.value = domainValue
    }
}
