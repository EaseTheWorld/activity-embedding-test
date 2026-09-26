package com.example.common.ui.settings

import com.example.core.item.ItemViewModel
import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.MutableChoiceItemViewModel
import com.example.core.item.MutableItemViewModel
import com.example.core.item.ValueWithState
import com.example.core.item.findMatchingOptionId
import com.example.core.item.parseTypedValue
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
 * Lightweight read-only [ItemViewModel] wrapping a given [StateFlow].
 * Ideal for telemetry sensors, battery levels, speed, or read-only vehicle gauges.
 */
class ReadOnlyItemViewModel<T>(
    override val valueFlow: StateFlow<T>,
    override val isVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true)
) : ItemViewModel<T>

fun <T> StateFlow<T>.asReadOnlyViewModel(
    isVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true)
): ItemViewModel<T> = ReadOnlyItemViewModel(this, isVisibleFlow)

/**
 * Read-only hardware ViewModel observing [VehicleProperty] from [HardwarePropertyStorage]
 * without offering mutation capabilities.
 */
class ReadOnlyHardwareItemViewModel<DomainT, RawV>(
    val property: com.example.core.item.VehicleProperty<DomainT, RawV>,
    private val storage: HardwarePropertyStorage,
    override val isVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true)
) : ItemViewModel<DomainT> {
    override val valueFlow: StateFlow<DomainT> = storage.observe(property)
}

/**
 * Thread-safe, reactive in-memory ViewModel for tests, previews, and runtime settings.
 */
class InMemoryItemViewModel<T>(
    initialValue: T,
    override val isVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true)
) : MutableItemViewModel<T> {
    private val _valueFlow = MutableStateFlow(initialValue)
    override val valueFlow: StateFlow<T> = _valueFlow.asStateFlow()

    override fun setValue(newValue: T) {
        _valueFlow.value = newValue
    }
}

/**
 * DataStore/Local Persistence ViewModel running within an ApplicationScope.
 * Clean Architecture compliant: Injects [SettingRepository] or UseCases,
 * while providing convenience constructors for backward compatibility and testing.
 */
class LocalStorageItemViewModel<T>(
    private val repository: SettingRepository<T>,
    private val scope: CoroutineScope = AppScope.scope,
    override val isVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true)
) : MutableItemViewModel<T> {

    override val valueFlow: StateFlow<T> = repository.valueFlow

    override fun setValue(newValue: T) {
        scope.launch {
            repository.save(newValue)
        }
    }

    /**
     * Backward-compatible convenience constructor for testing and inline persistence simulation.
     */
    constructor(
        initialValue: T,
        scope: CoroutineScope = AppScope.scope,
        isVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
        onPersist: suspend (T) -> Unit = {}
    ) : this(
        repository = InMemorySettingRepository(initialValue, onPersist),
        scope = scope,
        isVisibleFlow = isVisibleFlow
    )
}

/**
 * Remote API / Network ViewModel with optimistic updates and automatic rollback on failure.
 * Runs in an ApplicationScope to prevent cancellation mid-flight.
 */
class NetworkItemViewModel<T>(
    initialValue: T,
    private val scope: CoroutineScope = AppScope.scope,
    override val isVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
    private val remoteUpdate: suspend (T) -> Boolean
) : MutableItemViewModel<T> {
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
    private val scope: CoroutineScope = AppScope.scope,
    override val isVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true)
) : MutableItemViewModel<DomainT> {

    override val valueFlow: StateFlow<DomainT> = storage.observe(property)

    override fun setValue(newValue: DomainT) {
        scope.launch {
            storage.write(property, newValue)
        }
    }

    override fun updateFromParameters(parameters: Map<String, String>): Boolean {
        val propKey = property.propertyId.toString()
        val customValue = parameters[propKey]
        if (customValue != null) {
            val current = valueFlow.value
            val parsed = parseTypedValue(current, customValue) ?: return false
            setValue(parsed)
            return true
        }
        return super.updateFromParameters(parameters)
    }
}

/**
 * Bridges an Item to a key-value storage (Preferences / DataStore) with Key and Value mapping.
 */
class KeyValueItemViewModel<DomainT, RawV>(
    val binding: com.example.core.item.PropertyBinding<DomainT, RawV, String>,
    initialRawValue: RawV,
    private val scope: CoroutineScope = AppScope.scope,
    override val isVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
    private val onPersist: suspend (key: String, rawValue: RawV) -> Unit = { _, _ -> }
) : MutableItemViewModel<DomainT> {

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
    scope: CoroutineScope = AppScope.scope,
    isVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true)
): HardwareItemViewModel<DomainT, RawV> {
    val property = com.example.core.item.VehicleProperty(
        propertyId = propertyId,
        areaId = areaId,
        mapper = valueMapping
    )
    val viewModel = HardwareItemViewModel(property, storage, scope, isVisibleFlow)
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
    isVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
    onPersist: suspend (key: String, rawValue: RawV) -> Unit = { _, _ -> }
): KeyValueItemViewModel<DomainT, RawV> {
    val binding = com.example.core.item.PropertyBinding(
        itemId = itemId,
        storageKey = storageKey,
        valueMapping = valueMapping
    )
    val viewModel = KeyValueItemViewModel(binding, initialRawValue, scope, isVisibleFlow, onPersist)
    register(itemId, viewModel)
    return viewModel
}

/**
 * In-memory choice ViewModel for tests, previews, and runtime settings.
 * Implements [MutableChoiceItemViewModel<T>] where T is the option value (e.g. String).
 * Exposes [valueFlow] for the selected value, and [optionStates] for per-option states.
 */
class InMemoryChoiceItemViewModel<T>(
    val supportedOptionIds: List<T>,
    initialSelectedId: T = supportedOptionIds.first(),
    private val disabledOptionIdsFlow: StateFlow<Set<T>> = MutableStateFlow(emptySet()),
    private val hiddenOptionIdsFlow: StateFlow<Set<T>> = MutableStateFlow(emptySet()),
    scope: CoroutineScope = AppScope.scope,
    override val isVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true)
) : MutableChoiceItemViewModel<T> {
    private val _selectedIdFlow = MutableStateFlow(initialSelectedId)
    override val valueFlow: StateFlow<T> = _selectedIdFlow

    private fun computeStates(selectedId: T, disabledSet: Set<T>, hiddenSet: Set<T>): List<ValueWithState<T>> =
        supportedOptionIds.map { id ->
            ValueWithState(
                id = id,
                isSelected = (id == selectedId),
                isEnabled = !disabledSet.contains(id),
                isVisible = !hiddenSet.contains(id)
            )
        }

    override val optionStates: StateFlow<List<ValueWithState<T>>> = combine(
        _selectedIdFlow,
        disabledOptionIdsFlow,
        hiddenOptionIdsFlow
    ) { selectedId, disabledSet, hiddenSet ->
        computeStates(selectedId, disabledSet, hiddenSet)
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        computeStates(_selectedIdFlow.value, disabledOptionIdsFlow.value, hiddenOptionIdsFlow.value)
    )

    override fun setValue(newValue: T) {
        if (supportedOptionIds.contains(newValue)) {
            _selectedIdFlow.value = newValue
        }
    }
}

/**
 * Hardware-backed Choice ViewModel bridging a Choice Item to [HardwarePropertyStorage].
 * Implements [MutableChoiceItemViewModel<DomainT>] where DomainT is the option value (e.g. String).
 * Exposes [valueFlow] for the selected value, and [optionStates] for per-option states.
 */
class ChoiceHardwareItemViewModel<DomainT, RawV>(
    val property: com.example.core.item.VehicleProperty<DomainT, RawV>,
    val supportedOptionIds: List<DomainT>,
    private val storage: HardwarePropertyStorage,
    private val disabledOptionIdsFlow: StateFlow<Set<DomainT>> = MutableStateFlow(emptySet()),
    private val hiddenOptionIdsFlow: StateFlow<Set<DomainT>> = MutableStateFlow(emptySet()),
    private val scope: CoroutineScope = AppScope.scope,
    override val isVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true)
) : MutableChoiceItemViewModel<DomainT> {

    private val rawFlow: StateFlow<DomainT> = storage.observe(property)
    override val valueFlow: StateFlow<DomainT> = rawFlow

    private fun computeStates(selectedId: DomainT?, disabledSet: Set<DomainT>, hiddenSet: Set<DomainT>): List<ValueWithState<DomainT>> =
        supportedOptionIds.map { id ->
            ValueWithState(
                id = id,
                isSelected = (id == selectedId),
                isEnabled = !disabledSet.contains(id),
                isVisible = !hiddenSet.contains(id)
            )
        }

    override val optionStates: StateFlow<List<ValueWithState<DomainT>>> = combine(
        rawFlow,
        disabledOptionIdsFlow,
        hiddenOptionIdsFlow
    ) { selectedId, disabledSet, hiddenSet ->
        computeStates(selectedId, disabledSet, hiddenSet)
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        computeStates(rawFlow.value, disabledOptionIdsFlow.value, hiddenOptionIdsFlow.value)
    )

    override fun setValue(newValue: DomainT) {
        if (supportedOptionIds.contains(newValue)) {
            scope.launch {
                storage.write(property, newValue)
            }
        }
    }

    override fun updateFromParameters(parameters: Map<String, String>): Boolean {
        val propKey = property.propertyId.toString()
        val customValue = parameters[propKey]
        if (customValue != null) {
            val matched = supportedOptionIds.findMatchingOptionId(customValue) ?: return false
            setValue(matched)
            return true
        }
        return super.updateFromParameters(parameters)
    }
}





