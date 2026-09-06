package com.example.core.item

import kotlinx.coroutines.flow.StateFlow

/**
 * Capability interface for an [Item] that binds to a Vehicle HAL (VHAL) property.
 *
 * Designed as a pure Kotlin interface with ZERO Android framework or Car SDK dependencies:
 * - [propertyId] and [areaId] use primitive [Int] representations.
 * - [toItemValue] and [toVhalValue] provide bidirectional mapping between
 *   the high-level item domain value [T] and low-level hardware representation [V].
 *
 * @param T The domain state type of the Item (e.g. String, Int, Boolean).
 * @param V The raw VHAL hardware value type (e.g. Int, Float, Boolean).
 */
interface VhalBoundItem<T, V> : Item<T> {
    val propertyId: Int
    val areaId: Int get() = 0

    fun toItemValue(vhalValue: V): T
    fun toVhalValue(itemValue: T): V
}

/**
 * Universal binder contract connecting a [VhalBoundItem] to the hardware/VHAL layer.
 *
 * Enables inversion of control: the [VhalBoundItem] passes `this` to [bind],
 * allowing the Data Layer repository to manage VHAL subscription and hardware dispatch
 * without coupling the Item or UI to Android Automotive OS APIs.
 */
interface VhalPropertyBinder {
    /**
     * Binds the given [item] to the VHAL hardware layer and returns a reactive [StateFlow].
     */
    fun <T, V> bind(item: VhalBoundItem<T, V>, initialValue: T): StateFlow<T>

    /**
     * Dispatches a new domain [newValue] for the [item] down to the VHAL layer.
     */
    fun <T, V> setProperty(item: VhalBoundItem<T, V>, newValue: T)
}
