package com.example.core.item

/**
 * Contract for ViewModels that can mutate domain state from key-value parameters
 * (e.g. from Intent Extras or IPC Bundles).
 *
 * Enables Zero-Knowledge Pipelines where intermediate routing and navigation layers
 * do not need to know the parameter keys, count, or concrete types.
 */
interface ParameterizedMutableItemViewModel {
    /**
     * Mutates internal domain state from key-value parameters.
     * @param parameters Key-value parameters extracted from Intent Extras.
     * @return True if parsing and mutation succeeded, false otherwise.
     */
    fun updateFromParameters(parameters: Map<String, String>): Boolean
}
