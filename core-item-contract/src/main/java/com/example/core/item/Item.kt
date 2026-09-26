package com.example.core.item

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * @property isVisible Reactive stream determining whether this item should be displayed.
 */
open class Item(
    open val id: String,
    open val children: Set<Item> = emptySet(),
    open val isVisible: Flow<Boolean> = flowOf(true),
    open val hasDetailScreen: Boolean = children.isNotEmpty()
) {
    init {
        require(id.isNotBlank()) { "Item id cannot be blank" }
    }

    open val type: ItemType get() = ItemType.CUSTOM
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
 * Encapsulated reactive ViewModel contract for an individual setting item (Read-Only).
 * Decouples the storage mechanism (DataStore, Network, VHAL) from the UI layer.
 *
 * Exposes observable domain state via [valueFlow] and dynamic visibility via [isVisibleFlow].
 * Pure, minimal base contract for all setting items (Toggles, Sliders, Custom items, etc.).
 */
interface ItemViewModel<T> {
    val valueFlow: StateFlow<T>
    val isVisibleFlow: StateFlow<Boolean> get() = MutableStateFlow(true)
}

/**
 * Mutable ViewModel contract for interactive setting items supporting user input.
 * Receives user mutations via [setValue].
 *
 * Adheres to the Interface Segregation Principle (ISP) by separating
 * write capability from read-only observation ([ItemViewModel]).
 */
interface MutableItemViewModel<T> : ItemViewModel<T> {
    fun setValue(newValue: T)

    /**
     * Mutates internal domain state from key-value parameters
     * (e.g. from Intent Extras or IPC Bundles).
     *
     * Default implementation:
     * 1. If this ViewModel implements [SerializedMutableItemViewModel], delegates to [SerializedMutableItemViewModel.updateFromSerialized].
     * 2. Otherwise parses standard "value" key according to current [valueFlow] type and invokes [setValue].
     *
     * @param parameters Key-value parameters extracted from Intent Extras.
     * @return True if parsing and mutation succeeded, false otherwise.
     */
    fun updateFromParameters(parameters: Map<String, String>): Boolean {
        val rawValue = parameters["value"] ?: return false
        if (this is SerializedMutableItemViewModel) {
            return updateFromSerialized(rawValue)
        }
        val current = valueFlow.value
        val parsed = parseTypedValue(current, rawValue) ?: return false
        setValue(parsed)
        return true
    }
}

/**
 * Specialized [ItemViewModel] contract for multi-option Choice items (Read-Only).
 * Extends [ItemViewModel<T>] where T is the domain value type (e.g. String or Enum).
 * Exposes [optionStates] representing dynamic per-option selection and enablement.
 */
interface ChoiceItemViewModel<T> : ItemViewModel<T> {
    val optionStates: StateFlow<List<ValueWithState<T>>>

    val selectedValue: T?
        get() = optionStates.value.firstOrNull { it.isSelected }?.id ?: valueFlow.value
}

/**
 * Mutable Choice ViewModel contract supporting user option selection.
 * Receives the selected option ID [newValue] to update domain state.
 */
interface MutableChoiceItemViewModel<T> : ChoiceItemViewModel<T>, MutableItemViewModel<T> {
    override fun updateFromParameters(parameters: Map<String, String>): Boolean {
        val rawValue = parameters["value"] ?: return false
        val options = optionStates.value.mapNotNull { it.id }
        val matched = options.findMatchingOptionId(rawValue) ?: return false
        setValue(matched)
        return true
    }
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
    fun <T> getMutableViewModel(itemId: String): MutableItemViewModel<T>? {
        return viewModels[itemId] as? MutableItemViewModel<T>
    }

    fun <T> getMutableViewModel(item: Item): MutableItemViewModel<T>? = getMutableViewModel(item.id)

    @Suppress("UNCHECKED_CAST")
    fun <T> getChoiceViewModel(itemId: String): ChoiceItemViewModel<T>? {
        return viewModels[itemId] as? ChoiceItemViewModel<T>
    }

    fun <T> getChoiceViewModel(item: Item): ChoiceItemViewModel<T>? = getChoiceViewModel(item.id)

    @Suppress("UNCHECKED_CAST")
    fun <T> getMutableChoiceViewModel(itemId: String): MutableChoiceItemViewModel<T>? {
        return viewModels[itemId] as? MutableChoiceItemViewModel<T>
    }

    fun <T> getMutableChoiceViewModel(item: Item): MutableChoiceItemViewModel<T>? = getMutableChoiceViewModel(item.id)

    fun hasViewModel(itemId: String): Boolean = viewModels.containsKey(itemId)
    fun hasViewModel(item: Item): Boolean = hasViewModel(item.id)
}


