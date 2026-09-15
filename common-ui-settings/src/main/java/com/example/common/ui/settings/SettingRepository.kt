package com.example.common.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Clean Architecture persistence abstraction for Setting Items.
 * Decouples ItemViewModel from underlying persistence engines (DataStore, SharedPreferences, Room).
 */
interface SettingRepository<T> {
    val valueFlow: StateFlow<T>
    suspend fun save(value: T)
}

/**
 * In-memory reference implementation of [SettingRepository].
 * Used for testing and default fallback.
 */
class InMemorySettingRepository<T>(
    initialValue: T,
    private val onPersist: suspend (T) -> Unit = {}
) : SettingRepository<T> {
    private val _valueFlow = MutableStateFlow(initialValue)
    override val valueFlow: StateFlow<T> = _valueFlow.asStateFlow()

    override suspend fun save(value: T) {
        _valueFlow.value = value
        onPersist(value)
    }
}