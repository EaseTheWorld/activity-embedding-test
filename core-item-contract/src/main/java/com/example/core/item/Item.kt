package com.example.core.item

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

enum class ItemType {
    TOGGLE,
    SLIDER,
    CHOICE,
    ACTION,
    CONTAINER,
    CUSTOM
}

/**
 * Pure, lightweight root interface for a single Setting Item.
 * Represents identity, categorization, and reactive visibility/enabled states.
 * Contains ZERO Android framework dependencies, ZERO UI toolkit dependencies,
 * and ZERO vehicle domain dependencies.
 *
 * Notice: Free of generic type parameters and state contracts.
 * Non-state items (ActionItem, ContainerItem, Static headers) implement this directly.
 */
interface Item {
    val key: String
    val type: ItemType get() = ItemType.CUSTOM

    val isVisible: Flow<Boolean> get() = flowOf(true)
    val isEnabled: Flow<Boolean> get() = flowOf(true)
}

/**
 * Stateful Setting Item contract exposing reactive state and value mutation.
 * Implements the Interface Segregation Principle (ISP) by separating value concerns
 * from the structural [Item] interface.
 *
 * @param T The payload/state type (e.g. Boolean, Int, String, or custom data class).
 */
interface ValueItem<T> : Item {
    val valueFlow: StateFlow<T>

    fun onValueChanged(newValue: T)

    /**
     * Serialized string representation of value for IPC / MatrixCursor transmission.
     */
    val serializedValue: String get() = valueFlow.value.toString()
}

interface ToggleItem : ValueItem<Boolean> {
    override val type: ItemType get() = ItemType.TOGGLE
}

interface ChoiceItem : ValueItem<String> {
    override val type: ItemType get() = ItemType.CHOICE
    val options: List<String>
}

interface SliderItem : ValueItem<Int> {
    override val type: ItemType get() = ItemType.SLIDER
    val min: Int
    val max: Int
    val unitKey: String? get() = null
}

/**
 * Pure action trigger item (e.g. "Reset Settings", "Calibrate Cameras").
 * Does not hold a state value or require dummy Unit StateFlows.
 */
interface ActionItem : Item {
    override val type: ItemType get() = ItemType.ACTION
    fun onClick()
}

/**
 * Structural / composite item containing child setting items.
 * Enables data-driven grouping, multi-control rows (e.g. Frunk & Trunk toggles),
 * and spatial canvases (e.g. 2D vehicle seats/windows).
 */
interface ContainerItem : Item {
    override val type: ItemType get() = ItemType.CONTAINER
    val children: List<Item> get() = emptyList()
}
