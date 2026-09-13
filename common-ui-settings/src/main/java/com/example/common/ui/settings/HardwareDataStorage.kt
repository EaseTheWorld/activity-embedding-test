package com.example.common.ui.settings

import com.example.core.item.VehicleProperty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Universal abstraction for underlying vehicle hardware / VHAL storage.
 * Completely decoupled from UI Item models and Composable widgets.
 *
 * Encapsulates [VehicleProperty] mapping internally, exposing clean [DomainT]
 * StateFlows to the higher layers.
 */
interface HardwarePropertyStorage {
    /**
     * Observes the raw hardware property value as a reactive [StateFlow].
     */
    fun <V> observe(propertyId: Int, areaId: Int = 0): StateFlow<V>

    /**
     * Sets the raw hardware value for the given property.
     */
    suspend fun <V> write(propertyId: Int, areaId: Int = 0, value: V)

    /**
     * Observes a [VehicleProperty] with its internal [ValueMapping] applied.
     * The Storage handles raw-to-domain conversion internally!
     */
    fun <DomainT, RawV> observe(property: VehicleProperty<DomainT, RawV>): StateFlow<DomainT>

    /**
     * Writes a domain value for a [VehicleProperty] with its internal [ValueMapping] applied.
     * The Storage handles domain-to-raw conversion internally!
     */
    suspend fun <DomainT, RawV> write(property: VehicleProperty<DomainT, RawV>, value: DomainT)
}

/**
 * Thread-safe, reactive in-memory implementation of [HardwarePropertyStorage]
 * for testing, previews, and runtime simulation of Vehicle HAL.
 */
class InMemoryHardwareStorage : HardwarePropertyStorage {
    private val rawStorage = mutableMapOf<Pair<Int, Int>, MutableStateFlow<Any?>>()
    private val mappers = mutableMapOf<Pair<Int, Int>, (Any?) -> Any?>()
    private val domainStorage = mutableMapOf<Pair<Int, Int>, MutableStateFlow<Any?>>()

    fun <V> setInitialValue(propertyId: Int, areaId: Int = 0, initialValue: V) {
        val key = propertyId to areaId
        rawStorage.getOrPut(key) { MutableStateFlow(initialValue) }.value = initialValue
        mappers[key]?.let { mapper ->
            domainStorage.getOrPut(key) { MutableStateFlow(mapper(initialValue)) }.value = mapper(initialValue)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <DomainT, RawV> setInitialValue(property: VehicleProperty<DomainT, RawV>, domainValue: DomainT) {
        val key = property.propertyId to property.areaId
        val raw = property.mapper.toRaw(domainValue)
        mappers[key] = { property.mapper.toDomain(it as RawV) }
        rawStorage.getOrPut(key) { MutableStateFlow(raw) }.value = raw
        domainStorage.getOrPut(key) { MutableStateFlow(domainValue) }.value = domainValue
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V> observe(propertyId: Int, areaId: Int): StateFlow<V> {
        val key = propertyId to areaId
        val flow = rawStorage.getOrPut(key) {
            MutableStateFlow<Any?>(null)
        }
        return flow as StateFlow<V>
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <V> write(propertyId: Int, areaId: Int, value: V) {
        val key = propertyId to areaId
        val flow = rawStorage.getOrPut(key) {
            MutableStateFlow<Any?>(value)
        } as MutableStateFlow<V>
        flow.value = value

        mappers[key]?.let { mapper ->
            val domainFlow = domainStorage[key]
            if (domainFlow != null) {
                domainFlow.value = mapper(value)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <DomainT, RawV> observe(property: VehicleProperty<DomainT, RawV>): StateFlow<DomainT> {
        val key = property.propertyId to property.areaId
        mappers[key] = { raw ->
            if (raw != null) {
                try {
                    property.mapper.toDomain(raw as RawV)
                } catch (e: Exception) {
                    property.defaultValue
                }
            } else {
                property.defaultValue
            }
        }

        val flow = domainStorage.getOrPut(key) {
            val raw = rawStorage[key]?.value as? RawV
            val initial = if (raw != null) {
                try {
                    property.mapper.toDomain(raw)
                } catch (e: Exception) {
                    property.defaultValue
                }
            } else {
                property.defaultValue ?: runCatching {
                    @Suppress("UNCHECKED_CAST")
                    property.mapper.toDomain(0 as RawV)
                }.getOrNull()
            }
            MutableStateFlow<Any?>(initial)
        } as MutableStateFlow<DomainT>
        return flow
    }

    override suspend fun <DomainT, RawV> write(property: VehicleProperty<DomainT, RawV>, value: DomainT) {
        val rawValue = property.mapper.toRaw(value)
        write(property.propertyId, property.areaId, rawValue)
    }

    /**
     * Simulates an asynchronous external hardware event (e.g., from physical switch, ECU, or VHAL).
     */
    fun <V> simulateHardwareEvent(propertyId: Int, areaId: Int = 0, rawValue: V) {
        val key = propertyId to areaId
        val flow = rawStorage.getOrPut(key) {
            MutableStateFlow<Any?>(rawValue)
        }
        flow.value = rawValue

        mappers[key]?.let { mapper ->
            val domainFlow = domainStorage[key]
            if (domainFlow != null) {
                domainFlow.value = mapper(rawValue)
            }
        }
    }
}
