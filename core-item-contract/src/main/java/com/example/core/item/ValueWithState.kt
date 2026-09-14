package com.example.core.item

/**
 * Pure Data-Layer runtime state representation for an individual option in a Choice Item.
 * Contains ZERO Android framework or UI dependencies.
 *
 * @property id The unique option identifier (e.g. "OFF", "WAVE", "LUMBAR", "STRETCH").
 * @property isSelected Whether this option is currently selected.
 * @property isEnabled Whether this option is currently enabled and selectable (e.g. false during driving restriction).
 */
data class ValueWithState<T>(
    val id: T,
    val isSelected: Boolean,
    val isEnabled: Boolean = true
) {
    /**
     * Alias for [id] representing the option's domain value.
     */
    val value: T get() = id
}
