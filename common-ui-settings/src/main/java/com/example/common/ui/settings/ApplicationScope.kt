package com.example.common.ui.settings

import com.example.core.item.ItemViewModel
import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.MutableChoiceItemViewModel
import com.example.core.item.MutableItemViewModel
import com.example.core.item.ParameterizedMutableItemViewModel
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
) : MutableItemViewModel<T>, ParameterizedMutableItemViewModel {
    private val _valueFlow = MutableStateFlow(initialValue)
    override val valueFlow: StateFlow<T> = _valueFlow.asStateFlow()

    override fun setValue(newValue: T) {
        _valueFlow.value = newValue
    }

    override fun updateFromParameters(parameters: Map<String, String>): Boolean {
        val rawValue = parameters["value"] ?: return false
        val current = valueFlow.value
        val parsed = parseTypedValue(current, rawValue) ?: return false
        setValue(parsed)
        return true
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
) : MutableItemViewModel<T>, ParameterizedMutableItemViewModel {

    override val valueFlow: StateFlow<T> = repository.valueFlow

    override fun setValue(newValue: T) {
        scope.launch {
            repository.save(newValue)
        }
    }

    override fun updateFromParameters(parameters: Map<String, String>): Boolean {
        val rawValue = parameters["value"] ?: return false
        val current = valueFlow.value
        val parsed = parseTypedValue(current, rawValue) ?: return false
        setValue(parsed)
        return true
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
) : MutableItemViewModel<DomainT>, ParameterizedMutableItemViewModel {

    override val valueFlow: StateFlow<DomainT> = storage.observe(property)

    override fun setValue(newValue: DomainT) {
        scope.launch {
            storage.write(property, newValue)
        }
    }

    override fun updateFromParameters(parameters: Map<String, String>): Boolean {
        val rawValue = parameters["value"] ?: parameters[property.propertyId.toString()] ?: return false
        val current = valueFlow.value
        val parsed = parseTypedValue(current, rawValue) ?: return false
        setValue(parsed)
        return true
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
) : MutableChoiceItemViewModel<T>, ParameterizedMutableItemViewModel {
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

    override fun updateFromParameters(parameters: Map<String, String>): Boolean {
        val rawValue = parameters["value"] ?: return false
        val matched = supportedOptionIds.findMatchingOptionId(rawValue) ?: return false
        setValue(matched)
        return true
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
) : MutableChoiceItemViewModel<DomainT>, ParameterizedMutableItemViewModel {

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
        val rawValue = parameters["value"] ?: parameters[property.propertyId.toString()] ?: return false
        val matched = supportedOptionIds.findMatchingOptionId(rawValue) ?: return false
        setValue(matched)
        return true
    }
}

/**
 * Resolves matching option from a list of supported option IDs.
 * Supports:
 * 1. Exact string match ("LEVEL 2")
 * 2. Case-insensitive match ("level 2")
 * 3. Underscore-space normalized match ("LEVEL_2" <-> "LEVEL 2")
 * 4. Suffix match ("2" -> "LEVEL 2")
 * 5. 0-based index match ("2" -> 3rd option if not matching ID suffix)
 */
fun <T> List<T>.findMatchingOptionId(raw: String): T? {
    val trimmed = raw.trim()
    if (isEmpty()) return null

    // 1. Exact string match
    firstOrNull { it.toString() == trimmed }?.let { return it }

    // 2. Case-insensitive match
    firstOrNull { it.toString().equals(trimmed, ignoreCase = true) }?.let { return it }

    // 3. Underscore / space normalized match
    val normalized = trimmed.replace('_', ' ')
    firstOrNull {
        it.toString().replace('_', ' ').equals(normalized, ignoreCase = true)
    }?.let { return it }

    // 4. Suffix match (e.g. "2" matches "LEVEL 2" or "LEVEL_2")
    firstOrNull {
        val s = it.toString()
        s.endsWith(" $trimmed", ignoreCase = true) || s.endsWith("_$trimmed", ignoreCase = true)
    }?.let { return it }

    // 5. Index match (e.g. "0", "1", "2")
    val idx = trimmed.toIntOrNull()
    if (idx != null && idx in indices) {
        return this[idx]
    }

    return null
}

/**
 * Resilient boolean parser treating common affirmative / negative strings.
 */
fun parseLenientBoolean(raw: String): Boolean? {
    val clean = raw.trim().lowercase()
    return when (clean) {
        "true", "1", "on", "yes", "enable", "enabled" -> true
        "false", "0", "off", "no", "disable", "disabled" -> false
        else -> clean.toBooleanStrictOrNull()
    }
}

/**
 * Parses [rawStr] to match the type of [current].
 */
@Suppress("UNCHECKED_CAST")
fun <T> parseTypedValue(current: T, rawStr: String): T? {
    return when (current) {
        is Boolean -> parseLenientBoolean(rawStr) as? T
        is Int -> (rawStr.toIntOrNull() ?: rawStr.toDoubleOrNull()?.toInt()) as? T
        is Float -> rawStr.toFloatOrNull() as? T
        is Double -> rawStr.toDoubleOrNull() as? T
        is String -> rawStr as? T
        else -> null
    }
}





