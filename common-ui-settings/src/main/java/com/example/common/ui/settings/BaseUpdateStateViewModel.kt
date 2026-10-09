package com.example.common.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Base ViewModel for the Pragmatic / UpdateState MVI pattern.
 *
 * Eliminates the Mutation sealed class hierarchy by inlining state transitions
 * as pure lambdas: `updateState { copy(...) }`.
 */
abstract class BaseUpdateStateViewModel<INTENT, STATE, EFFECT>(
    initialState: STATE
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<STATE> = _uiState.asStateFlow()

    private val _effectChannel = Channel<EFFECT>(Channel.BUFFERED)
    val effectFlow: Flow<EFFECT> = _effectChannel.receiveAsFlow()

    /**
     * Inlined pure reducer: atomically transforms the current state via [reducer].
     */
    protected fun updateState(reducer: STATE.() -> STATE) {
        _uiState.update { it.reducer() }
    }

    /**
     * Emits a one-off side effect to be consumed by the UI.
     */
    protected fun sendEffect(effect: EFFECT) {
        _effectChannel.trySend(effect)
    }

    /**
     * Handles incoming user intents imperatively.
     */
    abstract fun sendIntent(intent: INTENT)

    /**
     * Lifecycle-aware stream collector helper:
     * Collects [flow] only while [uiState] has active subscribers (plus [stopTimeoutMillis] grace period).
     * When the UI goes to background and stops collecting, this cancels the upstream cold flow!
     */
    protected fun <T> launchWhileSubscribed(
        flow: Flow<T>,
        stopTimeoutMillis: Long = 5_000L,
        onEach: suspend (T) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.subscriptionCount
                .map { count -> count > 0 }
                .distinctUntilChanged()
                .flatMapLatest { hasSubscribers ->
                    if (hasSubscribers) {
                        flowOf(true)
                    } else {
                        flow {
                            delay(stopTimeoutMillis)
                            emit(false)
                        }
                    }
                }
                .distinctUntilChanged()
                .collectLatest { isActive ->
                    if (isActive) {
                        flow.collect { item ->
                            onEach(item)
                        }
                    }
                }
        }
    }
}
