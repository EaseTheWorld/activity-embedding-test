package com.example.common.ui.settings

import com.example.core.item.ChoiceItemViewModel
import com.example.core.item.ItemViewModel
import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.ValueWithState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Application-lifetime CoroutineScope for operations that must not be cancelled
 * when a user leaves the screen (e.g. DataStore writes, Network API sync).
 */
object AppScope {
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default

    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
}

/**
 * Thread-safe, reactive in-memory ViewModel for tests, previews, and runtime settings.
 */
class InMemoryItemViewModel<T>(
    initialValue: T
) : com.example.core.item.ItemViewModel<T> {
    private val _valueFlow = MutableStateFlow(initialValue)
    override val valueFlow: StateFlow<T> = _valueFlow.asStateFlow()

    override fun setValue(newValue: T) {
        _valueFlow.value = newValue
    }
}

/**
 * DataStore/Local Persistence simulation ViewModel running within an ApplicationScope.
 * Guarantees that local storage writes are completed even if UI compositions are disposed.
 */
class LocalStorageItemViewModel<T>(
    initialValue: T,
    private val scope: CoroutineScope = AppScope.scope,
    private val onPersist: suspend (T) -> Unit = {}
) : com.example.core.item.ItemViewModel<T> {
    private val _valueFlow = MutableStateFlow(initialValue)
    override val valueFlow: StateFlow<T> = _valueFlow.asStateFlow()

    override fun setValue(newValue: T) {
        _valueFlow.value = newValue
        scope.launch {
            onPersist(newValue)
        }
    }
}

/**
 * Remote API / Network ViewModel with optimistic updates and automatic rollback on failure.
 * Runs in an ApplicationScope to prevent cancellation mid-flight.
 */
class NetworkItemViewModel<T>(
    initialValue: T,
    private val scope: CoroutineScope = AppScope.scope,
    private val remoteUpdate: suspend (T) -> Boolean
) : com.example.core.item.ItemViewModel<T> {
    private val _valueFlow = MutableStateFlow(initialValue)
    override val valueFlow: StateFlow<T> = _valueFlow.asStateFlow()

    override fun setValue(newValue: T) {
        val previous = _valueFlow.value
        // 1. Optimistic update (Immediate UI feedback)
        _valueFlow.value = newValue

        // 2. Background network call
        scope.launch {
            try {
                val success = remoteUpdate(newValue)
                if (!success) {
                    // 3. Rollback on failure
                    _valueFlow.value = previous
                }
            } catch (_: Exception) {
                _valueFlow.value = previous
            }
        }
    }
}

/**
 * Bridges a high-level Item to a low-level [HardwarePropertyStorage] using a [VehicleProperty].
 *
 * Implements both:
 * 1. **Key Mapping**: [property.propertyId], [property.areaId]
 * 2. **Value Mapping**: Handled internally by [HardwarePropertyStorage] using [property.mapper]
 *
 * Runs background hardware writes within the given [scope] (@ApplicationScope)
 * to guarantee that writes cannot be cancelled mid-flight.
 */
class HardwareItemViewModel<DomainT, RawV>(
    val property: com.example.core.item.VehicleProperty<DomainT, RawV>,
    private val storage: HardwarePropertyStorage,
    private val scope: CoroutineScope = AppScope.scope
) : com.example.core.item.ItemViewModel<DomainT> {

    override val valueFlow: StateFlow<DomainT> = storage.observe(property)

    override fun setValue(newValue: DomainT) {
        scope.launch {
            storage.write(property, newValue)
        }
    }

    // Backward-compatible constructor for HardwareBinding
    constructor(
        binding: com.example.core.item.HardwareBinding<DomainT, RawV>,
        storage: HardwarePropertyStorage,
        scope: CoroutineScope = AppScope.scope
    ) : this(
        property = com.example.core.item.VehicleProperty(
            propertyId = binding.storageKey.propertyId,
            areaId = binding.storageKey.areaId,
            mapper = binding.valueMapping
        ),
        storage = storage,
        scope = scope
    )
}

/**
 * Bridges an Item to a key-value storage (Preferences / DataStore) with Key and Value mapping.
 */
class KeyValueItemViewModel<DomainT, RawV>(
    val binding: com.example.core.item.PropertyBinding<DomainT, RawV, String>,
    initialRawValue: RawV,
    private val scope: CoroutineScope = AppScope.scope,
    private val onPersist: suspend (key: String, rawValue: RawV) -> Unit = { _, _ -> }
) : com.example.core.item.ItemViewModel<DomainT> {

    private val _valueFlow = MutableStateFlow(binding.valueMapping.toDomain(initialRawValue))
    override val valueFlow: StateFlow<DomainT> = _valueFlow.asStateFlow()

    override fun setValue(newValue: DomainT) {
        _valueFlow.value = newValue
        val raw = binding.valueMapping.toRaw(newValue)
        scope.launch {
            onPersist(binding.storageKey, raw)
        }
    }
}

/**
 * Convenience extension to bind a hardware property with explicit Key Mapping and Value Mapping.
 */
fun <DomainT, RawV> com.example.core.item.ItemViewModelRegistry.bindHardware(
    itemId: String,
    propertyId: Int,
    areaId: Int = 0,
    valueMapping: com.example.core.item.ValueMapping<DomainT, RawV>,
    storage: HardwarePropertyStorage,
    scope: CoroutineScope = AppScope.scope
): HardwareItemViewModel<DomainT, RawV> {
    val binding = com.example.core.item.PropertyBinding(
        itemId = itemId,
        storageKey = com.example.core.item.HardwareKey(propertyId, areaId),
        valueMapping = valueMapping
    )
    val viewModel = HardwareItemViewModel(binding, storage, scope)
    register(itemId, viewModel)
    return viewModel
}

/**
 * Convenience extension to bind a key-value / preference property with explicit Key Mapping and Value Mapping.
 */
fun <DomainT, RawV> com.example.core.item.ItemViewModelRegistry.bindStorage(
    itemId: String,
    storageKey: String,
    valueMapping: com.example.core.item.ValueMapping<DomainT, RawV>,
    initialRawValue: RawV,
    scope: CoroutineScope = AppScope.scope,
    onPersist: suspend (key: String, rawValue: RawV) -> Unit = { _, _ -> }
): KeyValueItemViewModel<DomainT, RawV> {
    val binding = com.example.core.item.PropertyBinding(
        itemId = itemId,
        storageKey = storageKey,
        valueMapping = valueMapping
    )
    val viewModel = KeyValueItemViewModel(binding, initialRawValue, scope, onPersist)
    register(itemId, viewModel)
    return viewModel
}

/**
 * In-memory [ChoiceItemViewModel] for tests, previews, and runtime settings.
 * Produces [optionStates] with dynamic [ValueWithState] flags (isSelected, isEnabled)
 * while preserving SSOT (option IDs come from the Item Catalog).
 */
class InMemoryChoiceItemViewModel<T>(
    val supportedOptionIds: List<T>,
    initialSelectedId: T = supportedOptionIds.first(),
    private val disabledOptionIdsFlow: StateFlow<Set<T>> = MutableStateFlow(emptySet()),
    scope: CoroutineScope = AppScope.scope
) : ChoiceItemViewModel<T> {
    private val _selectedIdFlow = MutableStateFlow(initialSelectedId)
    override val valueFlow: StateFlow<T> = _selectedIdFlow.asStateFlow()

    private fun computeStates(selectedId: T, disabledSet: Set<T>): List<ValueWithState<T>> =
        supportedOptionIds.map { id ->
            ValueWithState(
                id = id,
                isSelected = (id == selectedId),
                isEnabled = !disabledSet.contains(id)
            )
        }

    override val optionStates: StateFlow<List<ValueWithState<T>>> = combine(
        _selectedIdFlow,
        disabledOptionIdsFlow
    ) { selectedId, disabledSet ->
        computeStates(selectedId, disabledSet)
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        computeStates(_selectedIdFlow.value, disabledOptionIdsFlow.value)
    )

    override fun setValue(newValue: T) {
        if (supportedOptionIds.contains(newValue)) {
            _selectedIdFlow.value = newValue
        }
    }
}

/**
 * Hardware-backed [ChoiceItemViewModel] bridging a Choice Item to [HardwarePropertyStorage].
 * Exposes dynamic [optionStates] with per-option [ValueWithState] (isSelected, isEnabled)
 * while preserving SSOT (option IDs come from the Item Catalog).
 */
class ChoiceHardwareItemViewModel<DomainT, RawV>(
    val property: com.example.core.item.VehicleProperty<DomainT, RawV>,
    val supportedOptionIds: List<DomainT>,
    private val storage: HardwarePropertyStorage,
    private val disabledOptionIdsFlow: StateFlow<Set<DomainT>> = MutableStateFlow(emptySet()),
    private val scope: CoroutineScope = AppScope.scope
) : ChoiceItemViewModel<DomainT> {

    override val valueFlow: StateFlow<DomainT> = storage.observe(property)

    private fun computeStates(selectedId: DomainT?, disabledSet: Set<DomainT>): List<ValueWithState<DomainT>> =
        supportedOptionIds.map { id ->
            ValueWithState(
                id = id,
                isSelected = (id == selectedId),
                isEnabled = !disabledSet.contains(id)
            )
        }

    override val optionStates: StateFlow<List<ValueWithState<DomainT>>> = combine(
        valueFlow,
        disabledOptionIdsFlow
    ) { selectedId, disabledSet ->
        computeStates(selectedId, disabledSet)
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        computeStates(valueFlow.value, disabledOptionIdsFlow.value)
    )

    override fun setValue(newValue: DomainT) {
        if (supportedOptionIds.contains(newValue)) {
            scope.launch {
                storage.write(property, newValue)
            }
        }
    }
}




