package com.example.core.item

import kotlinx.coroutines.flow.StateFlow

/**
 * Pure, immutable Data Binding representation for a Vehicle HAL (VHAL) property.
 *
 * Implements Field Composition (has-a) rather than Interface Inheritance (is-a):
 * - Guarantees single-source-of-truth without multiple-inheritance collisions (e.g. VHAL vs Preferences).
 * - Eliminates escaping `this` issues during object initialization.
 * - Pure Kotlin with ZERO Android framework or Car SDK dependencies.
 *
 * @param T The domain state type of the Item (e.g. String, Int, Boolean).
 * @param V The raw VHAL hardware value type (e.g. Int, Float, Boolean).
 */
data class VhalBinding<T, V>(
    val propertyId: Int,
    val areaId: Int = 0,
    val toItemValue: (V) -> T,
    val toVhalValue: (T) -> V
)

/**
 * Capability interface for items that expose a [VhalBinding] via composition.
 */
interface HasVhalBinding<T, V> {
    val vhalBinding: VhalBinding<T, V>
}

// Backward-compatibility alias
typealias VhalBoundItem<T, V> = HasVhalBinding<T, V>

/**
 * Universal binder contract connecting a [VhalBinding] to the hardware/VHAL layer.
 *
 * Notice that the binder operates strictly on the pure [VhalBinding] data class,
 * completely decoupled from UI widgets, Composables, or `this` references.
 */
interface VhalPropertyBinder {
    /**
     * Binds the given [binding] to the VHAL hardware layer and returns a reactive [StateFlow].
     */
    fun <T, V> bind(binding: VhalBinding<T, V>, initialValue: T): StateFlow<T>

    /**
     * Dispatches a new domain [newValue] for the [binding] down to the VHAL layer.
     */
    fun <T, V> setProperty(binding: VhalBinding<T, V>, newValue: T)
}
