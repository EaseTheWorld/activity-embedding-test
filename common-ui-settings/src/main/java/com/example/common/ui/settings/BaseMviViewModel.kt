package com.example.common.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

/**
 * Universal Base MVI ViewModel.
 *
 * Implements the unidirectional data flow with lifecycle-aware unsubscription:
 *   Intent -> toMutation() -> merge(intentMutations, externalMutations) -> scan(reduce) -> uiState
 *
 * @param INTENT User actions/triggers from the UI.
 * @param MUTATION Internal atomic state updates (results of sync or async work).
 * @param STATE Single immutable UI state.
 * @param EFFECT One-off events (Navigation, Toast, Dialogs, Snackbars).
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseMviViewModel<INTENT, MUTATION, STATE, EFFECT>(
    private val initialState: STATE,
    private val stopTimeoutMillis: Long = 5_000L
) : ViewModel() {

    // 1. Intent stream (Input from UI)
    private val _intentFlow = MutableSharedFlow<INTENT>(extraBufferCapacity = 64)

    // 2. One-off Side Effect channel (Output to UI, e.g. Navigation)
    private val _effectChannel = Channel<EFFECT>(Channel.BUFFERED)
    val effectFlow: Flow<EFFECT> = _effectChannel.receiveAsFlow()

    // 3. Optional external/stream mutations (e.g. database, sensors, VHAL)
    protected open val externalMutations: Flow<MUTATION> = emptyFlow()

    /**
     * Dispatches a user intent to the ViewModel.
     * Can be called directly from Composable event callbacks.
     */
    fun sendIntent(intent: INTENT) {
        _intentFlow.tryEmit(intent)
    }

    /**
     * Emits a one-off side effect to be consumed by the UI.
     */
    protected fun sendEffect(effect: EFFECT) {
        _effectChannel.trySend(effect)
    }

    /**
     * Transforms a user [intent] into a stream of [MUTATION]s.
     * - Use `flow { emit(...) }` for asynchronous/suspending operations.
     * - Use `flowOf(...)` for immediate in-memory mutations.
     * - Return [emptyFlow] if the intent produces no state changes (e.g. side-effect only).
     */
    protected abstract fun toMutation(intent: INTENT): Flow<MUTATION>

    /**
     * Pure reducer function: calculates the next state from the previous state and a mutation.
     * MUST be 100% pure, synchronous, and side-effect free.
     */
    protected abstract fun reduce(previousState: STATE, mutation: MUTATION): STATE

    /**
     * Reactive StateFlow pipeline.
     * Initialized lazily to ensure subclass properties (e.g. [externalMutations]) are fully constructed.
     * Subscribes when the UI collects with lifecycle (e.g. `collectAsStateWithLifecycle`)
     * and automatically cancels upstream cold flows after [stopTimeoutMillis] when not collected.
     */
    val uiState: StateFlow<STATE> by lazy {
        merge(
            _intentFlow.flatMapMerge { toMutation(it) },
            externalMutations
        )
        .scan(initialState) { state, mutation ->
            reduce(state, mutation)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis),
            initialValue = initialState
        )
    }
}

/**
 * Convenience base class for screens that do not use side effects.
 */
abstract class SimpleMviViewModel<INTENT, MUTATION, STATE>(
    initialState: STATE,
    stopTimeoutMillis: Long = 5_000L
) : BaseMviViewModel<INTENT, MUTATION, STATE, Nothing>(initialState, stopTimeoutMillis)
