package com.example.feature.seat

import com.example.core.item.VhalBoundItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Automotive Data Layer Repository contract for Seat properties.
 *
 * Demonstrates how the Data Layer interacts with [VhalBoundItem]s without
 * coupling the presentation layer to Android Automotive OS VHAL or CarPropertyManager.
 */
interface SeatPropertyRepository {
    /**
     * Retrieves the current live state flow for a VHAL property key.
     */
    fun <T, V> getPropertyFlow(item: VhalBoundItem<T, V>): StateFlow<T>

    /**
     * Dispatches a new property value to the VHAL hardware layer.
     */
    fun <T, V> setPropertyValue(item: VhalBoundItem<T, V>, newValue: T)
}

/**
 * In-memory / VHAL implementation of [SeatPropertyRepository].
 *
 * In a production AAOS environment, this class delegates to `CarPropertyManager.registerCallback`
 * and `CarPropertyManager.setProperty`.
 */
class SeatPropertyRepositoryImpl : SeatPropertyRepository {

    private val propertyStorage = mutableMapOf<Pair<Int, Int>, MutableStateFlow<Any?>>()

    @Suppress("UNCHECKED_CAST")
    override fun <T, V> getPropertyFlow(item: VhalBoundItem<T, V>): StateFlow<T> {
        val key = item.propertyId to item.areaId
        val flow = propertyStorage.getOrPut(key) {
            MutableStateFlow(item.valueFlow.value)
        }
        return flow as StateFlow<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T, V> setPropertyValue(item: VhalBoundItem<T, V>, newValue: T) {
        val key = item.propertyId to item.areaId
        val rawVhal = item.toVhalValue(newValue)
        val flow = propertyStorage.getOrPut(key) {
            MutableStateFlow(item.valueFlow.value)
        } as MutableStateFlow<T>

        flow.value = item.toItemValue(rawVhal)
        item.onValueChanged(newValue)
    }
}
