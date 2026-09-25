package com.example.core.item

/**
 * Contract for ViewModels that can deserialize raw string values
 * (e.g. from Deep Links `?value=...`, IPC bundles, or external configs).
 */
interface SerializedMutableItemViewModel {
    /**
     * Updates internal domain state from serialized string format.
     * @param raw Raw serialized string.
     * @return True if parsing and mutation succeeded, false otherwise.
     */
    fun updateFromSerialized(raw: String): Boolean
}
