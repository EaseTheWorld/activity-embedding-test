package com.example.core.item

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
