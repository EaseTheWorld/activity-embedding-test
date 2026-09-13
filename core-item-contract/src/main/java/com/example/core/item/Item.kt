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
 * Pure, lightweight Tree Node representing a searchable Setting Item or Category.
 * Contains ZERO Android framework dependencies, ZERO UI toolkit dependencies,
 * and ZERO vehicle domain dependencies.
 *
 * @property id Unique identifier for this item (used as primary key in search and registries).
 * @property children Immutable set of child Items in the settings hierarchy.
 */
open class Item(
    open val id: String,
    open val children: Set<Item> = emptySet()
) {
    init {
        require(id.isNotBlank()) { "Item id cannot be blank" }
    }

    /**
     * Backward-compatibility alias for [id].
     */
    val key: String get() = id

    open val type: ItemType get() = ItemType.CUSTOM
    open val isVisible: Flow<Boolean> get() = flowOf(true)
    open val isEnabled: Flow<Boolean> get() = flowOf(true)
    open val serializedValue: String get() = ""

    /**
     * Recursively searches for an item by id in this tree.
     */
    fun findById(targetId: String): Item? {
        if (this.id == targetId) return this
        for (child in children) {
            val found = child.findById(targetId)
            if (found != null) return found
        }
        return null
    }

    /**
     * Flattens the entire tree into a sequence of items (depth-first traversal).
     * Ideal for SearchManager indexing.
     */
    fun flatten(): Sequence<Item> = sequence {
        yield(this@Item)
        for (child in children) {
            yieldAll(child.flatten())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Item) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Item(id='$id', childrenCount=${children.size})"
}

/**
 * Setting item holding a reactive state value of type [T].
 */
open class ValueItem<T>(
    id: String,
    open val valueFlow: StateFlow<T>,
    children: Set<Item> = emptySet()
) : Item(id, children) {
    open fun onValueChanged(newValue: T) {}

    /**
     * Serialized string representation of value for IPC / MatrixCursor transmission.
     */
    override val serializedValue: String get() = valueFlow.value.toString()
}

open class ToggleItem(
    id: String,
    valueFlow: StateFlow<Boolean>,
    children: Set<Item> = emptySet()
) : ValueItem<Boolean>(id, valueFlow, children) {
    override val type: ItemType get() = ItemType.TOGGLE
}

open class ChoiceItem(
    id: String,
    valueFlow: StateFlow<String>,
    open val options: List<String>,
    children: Set<Item> = emptySet()
) : ValueItem<String>(id, valueFlow, children) {
    override val type: ItemType get() = ItemType.CHOICE
}

open class SliderItem(
    id: String,
    valueFlow: StateFlow<Int>,
    open val min: Int,
    open val max: Int,
    open val unitKey: String? = null,
    children: Set<Item> = emptySet()
) : ValueItem<Int>(id, valueFlow, children) {
    override val type: ItemType get() = ItemType.SLIDER
}

open class ActionItem(
    id: String,
    valueFlow: StateFlow<Unit>,
    children: Set<Item> = emptySet()
) : ValueItem<Unit>(id, valueFlow, children) {
    override val type: ItemType get() = ItemType.ACTION
}

/**
 * Canonical Builder base class for declaring Setting Items with zero omission.
 * Any item registered via [item] is automatically appended to [children].
 */
abstract class SettingCatalog(val id: String) {
    private val _children = mutableSetOf<Item>()
    val children: Set<Item> get() = _children
    val items: List<Item> get() = _children.toList()

    protected fun <T : Item> item(instance: T): T {
        _children.add(instance)
        return instance
    }

    protected fun <T : SettingCatalog> group(catalog: T): T {
        _children.add(catalog.asItem)
        return catalog
    }

    val asItem: Item get() = Item(id = id, children = _children.toSet())
}

/**
 * Encapsulated reactive ViewModel contract for an individual setting item.
 * Decouples the storage mechanism (DataStore, Network, VHAL) from the UI layer.
 *
 * Exposes observable domain state via [valueFlow] and receives user mutations via [setValue].
 */
interface ItemViewModel<T> {
    val valueFlow: StateFlow<T>
    fun setValue(newValue: T)
}

/**
 * Specialized [ItemViewModel] contract for multi-option Choice items.
 * Exposes rich [optionStates] for UI rendering while preserving [valueFlow] and [setValue]
 * for backward compatibility and uniform key-value storage.
 */
interface ChoiceItemViewModel<T> : ItemViewModel<T> {
    val optionStates: StateFlow<List<ValueWithState<T>>>
}


/**
 * Universal Registry mapping setting item IDs to their respective [ItemViewModel].
 */
open class ItemViewModelRegistry(
    bindings: Set<ItemViewModelBinding<*>> = emptySet()
) {
    private val viewModels = mutableMapOf<String, ItemViewModel<*>>()

    init {
        registerAll(bindings)
    }

    fun <T> register(itemId: String, viewModel: ItemViewModel<T>) {
        viewModels[itemId] = viewModel
    }

    fun register(binding: ItemViewModelBinding<*>) {
        viewModels[binding.item.id] = binding.viewModel
    }

    fun registerAll(bindings: Set<ItemViewModelBinding<*>>) {
        bindings.forEach { register(it) }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getViewModel(itemId: String): ItemViewModel<T>? {
        return viewModels[itemId] as? ItemViewModel<T>
    }

    fun <T> getViewModel(item: Item): ItemViewModel<T>? = getViewModel(item.id)

    @Suppress("UNCHECKED_CAST")
    fun <T> getChoiceViewModel(itemId: String): ChoiceItemViewModel<T>? {
        return viewModels[itemId] as? ChoiceItemViewModel<T>
    }

    fun <T> getChoiceViewModel(item: Item): ChoiceItemViewModel<T>? = getChoiceViewModel(item.id)

    fun hasViewModel(itemId: String): Boolean = viewModels.containsKey(itemId)
    fun hasViewModel(item: Item): Boolean = hasViewModel(item.id)
}


