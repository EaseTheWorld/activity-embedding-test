package com.example.core.item

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

enum class ItemType {
    TOGGLE,
    SLIDER,
    CHOICE,
    ACTION,
    CUSTOM
}

/**
 * Pure, lightweight interface for a single Setting Item.
 * Contains ZERO Android framework dependencies, ZERO UI toolkit dependencies,
 * and ZERO vehicle domain dependencies.
 *
 * @param T The payload/state type (e.g. Boolean, Int, String, or custom data class).
 */
interface Item<T> {
    val key: String
    val type: ItemType get() = ItemType.CUSTOM

    val isVisible: Flow<Boolean> get() = flowOf(true)
    val isEnabled: Flow<Boolean> get() = flowOf(true)
    val valueFlow: StateFlow<T>

    fun onValueChanged(newValue: T)

    /**
     * Serialized string representation of value for IPC / MatrixCursor transmission.
     */
    val serializedValue: String get() = valueFlow.value.toString()
}

interface ToggleItem : Item<Boolean> {
    override val type: ItemType get() = ItemType.TOGGLE
}

interface ChoiceItem : Item<String> {
    override val type: ItemType get() = ItemType.CHOICE
    val options: List<String>
}

interface SliderItem : Item<Int> {
    override val type: ItemType get() = ItemType.SLIDER
    val min: Int
    val max: Int
    val unitKey: String? get() = null
}

interface ActionItem : Item<Unit> {
    override val type: ItemType get() = ItemType.ACTION
}
