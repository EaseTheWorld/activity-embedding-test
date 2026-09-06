package com.example.feature.seat

import com.example.core.item.HasVhalBinding
import com.example.core.item.VhalBinding
import com.example.core.item.VhalPropertyBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Automotive Data Layer Repository contract for Seat properties.
 *
 * Implements [VhalPropertyBinder] to receive pure [VhalBinding] composition objects
 * rather than coupling directly to UI items or `this` references.
 */
interface SeatPropertyRepository : VhalPropertyBinder {
    /**
     * Simulates an incoming raw hardware event from VHAL (e.g. from physical dial, ECU, or CarPropertyManager).
     * Automatically retrieves the registered [VhalBinding] by (propertyId, areaId), maps the raw value to
     * domain value via [VhalBinding.toItemValue], and emits to the live flow.
     */
    fun <V> onVhalHardwareEvent(propertyId: Int, areaId: Int, rawHardwareValue: V)

    /**
     * Convenience overload accepting an item with a [VhalBinding].
     */
    fun <T, V> setProperty(item: HasVhalBinding<T, V>, newValue: T) = setProperty(item.vhalBinding, newValue)

    /**
     * Backward-compatible alias for setProperty.
     */
    fun <T, V> setPropertyValue(item: HasVhalBinding<T, V>, newValue: T) = setProperty(item.vhalBinding, newValue)
}

/**
 * In-memory / VHAL implementation of [SeatPropertyRepository].
 *
 * Demonstrates:
 * 1. How pure [VhalBinding] objects are registered without leaking `this`.
 * 2. How UI writes pass [VhalBinding] and convert to raw VHAL via [VhalBinding.toVhalValue].
 * 3. How incoming VHAL hardware events look up the [VhalBinding] and convert to domain via [VhalBinding.toItemValue].
 */
class SeatPropertyRepositoryImpl : SeatPropertyRepository {

    companion object {
        /** Singleton instance for default injection / static access */
        val shared: SeatPropertyRepository by lazy { SeatPropertyRepositoryImpl() }
    }

    // Registry of active VHAL bindings: (propertyId, areaId) -> VhalBinding
    private val boundBindings = mutableMapOf<Pair<Int, Int>, VhalBinding<*, *>>()

    // Active live state flows: (propertyId, areaId) -> MutableStateFlow
    private val propertyStorage = mutableMapOf<Pair<Int, Int>, MutableStateFlow<Any?>>()

    @Suppress("UNCHECKED_CAST")
    override fun <T, V> bind(binding: VhalBinding<T, V>, initialValue: T): StateFlow<T> {
        val key = binding.propertyId to binding.areaId
        boundBindings[key] = binding

        val flow = propertyStorage.getOrPut(key) {
            MutableStateFlow(initialValue)
        }
        return flow as StateFlow<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T, V> setProperty(binding: VhalBinding<T, V>, newValue: T) {
        val key = binding.propertyId to binding.areaId

        // 1. Convert domain value to raw VHAL hardware value using the binding's mapping
        val rawVhal = binding.toVhalValue(newValue)

        // 2. In production AAOS: carPropertyManager.setProperty(binding.propertyId, binding.areaId, rawVhal)

        // 3. Update local StateFlow
        val flow = propertyStorage.getOrPut(key) {
            MutableStateFlow(newValue)
        } as MutableStateFlow<T>
        flow.value = binding.toItemValue(rawVhal)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V> onVhalHardwareEvent(propertyId: Int, areaId: Int, rawHardwareValue: V) {
        val key = propertyId to areaId
        val binding = boundBindings[key] as? VhalBinding<Any?, V> ?: return

        // Translate raw hardware VHAL value to high-level domain value
        val domainValue = binding.toItemValue(rawHardwareValue)

        val flow = propertyStorage[key]
        flow?.value = domainValue
    }
}
