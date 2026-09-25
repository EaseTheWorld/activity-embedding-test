package com.example.common.ui.settings

import android.util.Log
import com.example.core.item.ChoiceItemViewModel
import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.MutableItemViewModel
import com.example.core.item.SerializedMutableItemViewModel
import com.example.core.item.ValueWithState

/**
 * Robust, universal type coercion and mutation helper for [ItemViewModelRegistry].
 *
 * Supports mutating setting items from:
 * 1. Deep Link query parameters (e.g. `myapp://navigate/{categoryId}/{itemId}?value={value}`)
 * 2. ADB simulation broadcasts
 * 3. External IPC or test tools
 *
 * Adheres to Interface Segregation Principle (ISP):
 * Only ViewModels implementing [MutableItemViewModel] or [SerializedMutableItemViewModel] can be mutated.
 */
object ItemSetterHelper {
    private const val TAG = "ItemSetterHelper"

    private fun logI(tag: String, msg: String) {
        try { Log.i(tag, msg) } catch (_: Throwable) { println("[$tag] $msg") }
    }

    private fun logD(tag: String, msg: String) {
        try { Log.d(tag, msg) } catch (_: Throwable) { println("[$tag] $msg") }
    }

    private fun logW(tag: String, msg: String) {
        try { Log.w(tag, msg) } catch (_: Throwable) { System.err.println("[$tag] $msg") }
    }

    /**
     * Attempts to mutate the setting item specified by [itemId] to [rawValue].
     *
     * @param registry The [ItemViewModelRegistry] owning the item's ViewModel.
     * @param itemId Unique ID of the setting item (e.g. "auto_lock", "driver_seat_heat", "seat_lumbar").
     * @param rawValue String representation of the new value from deep link or input.
     * @return True if the mutation was successfully applied, false otherwise.
     */
    fun applyValue(registry: ItemViewModelRegistry, itemId: String, rawValue: String): Boolean {
        val vm = registry.getViewModel<Any>(itemId)
        if (vm == null) {
            logW(TAG, "Cannot apply value: no ViewModel registered for itemId '$itemId'")
            return false
        }

        // 1. Dedicated SerializedMutableItemViewModel contract (e.g. SeatLumbarViewModel)
        if (vm is SerializedMutableItemViewModel) {
            val handled = vm.updateFromSerialized(rawValue)
            if (handled) {
                logI(TAG, "Applied serialized value '$rawValue' to item '$itemId'")
                return true
            } else {
                logW(TAG, "Serialized value '$rawValue' rejected by SerializedMutableItemViewModel for item '$itemId'")
                return false
            }
        }

        // ISP Check: ViewModel must support mutations
        if (vm !is MutableItemViewModel<*>) {
            logW(TAG, "Cannot apply value: ViewModel for '$itemId' does not implement MutableItemViewModel (read-only)")
            return false
        }

        @Suppress("UNCHECKED_CAST")
        val mutableVm = vm as MutableItemViewModel<Any>

        // 2. Choice Item (Multi-Option with state)
        if (vm is ChoiceItemViewModel<*>) {
            val options = vm.optionStates.value
            val matchedOption = findMatchingOption(options, rawValue)
            if (matchedOption != null && matchedOption.id != null) {
                @Suppress("UNCHECKED_CAST")
                val typedVm = vm as? MutableItemViewModel<Any>
                if (typedVm != null) {
                    typedVm.setValue(matchedOption.id!!)
                    logI(TAG, "Applied choice option '${matchedOption.id}' to item '$itemId'")
                    return true
                }
            }

            // Fallback: if value type is String, try applying directly
            if (vm.valueFlow.value is String) {
                @Suppress("UNCHECKED_CAST")
                val stringVm = vm as? MutableItemViewModel<String>
                if (stringVm != null) {
                    stringVm.setValue(rawValue)
                    logI(TAG, "Applied raw string choice '$rawValue' to item '$itemId'")
                    return true
                }
            }

            logW(TAG, "Option '$rawValue' could not be resolved for choice item '$itemId'")
            return false
        }

        // 3. Strongly typed primitives based on current valueFlow type
        val current = vm.valueFlow.value
        return when (current) {
            is Boolean -> {
                val parsed = parseBoolean(rawValue)
                @Suppress("UNCHECKED_CAST")
                (mutableVm as MutableItemViewModel<Boolean>).setValue(parsed)
                logI(TAG, "Applied boolean $parsed to item '$itemId'")
                true
            }
            is Int -> {
                val parsed = rawValue.toIntOrNull()
                if (parsed != null) {
                    @Suppress("UNCHECKED_CAST")
                    (mutableVm as MutableItemViewModel<Int>).setValue(parsed)
                    logI(TAG, "Applied int $parsed to item '$itemId'")
                    true
                } else {
                    logW(TAG, "Failed to parse '$rawValue' as Int for item '$itemId'")
                    false
                }
            }
            is Float -> {
                val parsed = rawValue.toFloatOrNull()
                if (parsed != null) {
                    @Suppress("UNCHECKED_CAST")
                    (mutableVm as MutableItemViewModel<Float>).setValue(parsed)
                    logI(TAG, "Applied float $parsed to item '$itemId'")
                    true
                } else {
                    logW(TAG, "Failed to parse '$rawValue' as Float for item '$itemId'")
                    false
                }
            }
            is Double -> {
                val parsed = rawValue.toDoubleOrNull()
                if (parsed != null) {
                    @Suppress("UNCHECKED_CAST")
                    (mutableVm as MutableItemViewModel<Double>).setValue(parsed)
                    logI(TAG, "Applied double $parsed to item '$itemId'")
                    true
                } else {
                    logW(TAG, "Failed to parse '$rawValue' as Double for item '$itemId'")
                    false
                }
            }
            is String -> {
                @Suppress("UNCHECKED_CAST")
                (mutableVm as MutableItemViewModel<String>).setValue(rawValue)
                logI(TAG, "Applied string '$rawValue' to item '$itemId'")
                true
            }
            else -> {
                // 4. Reflection fallback for custom models with updateFromSerialized
                try {
                    val method = vm.javaClass.methods.firstOrNull {
                        it.name == "updateFromSerialized" &&
                                it.parameterTypes.size == 1 &&
                                it.parameterTypes[0] == String::class.java
                    }
                    if (method != null) {
                        val res = method.invoke(vm, rawValue)
                        if (res is Boolean) {
                            if (res) {
                                logI(TAG, "Applied via reflection updateFromSerialized to item '$itemId'")
                                return true
                            } else {
                                logW(TAG, "updateFromSerialized returned false for item '$itemId'")
                                return false
                            }
                        }
                        logI(TAG, "Applied via reflection updateFromSerialized to item '$itemId'")
                        return true
                    }
                } catch (e: Exception) {
                    logW(TAG, "Reflection invocation failed for item '$itemId': $e")
                }

                logW(TAG, "Unsupported value type '${current.javaClass.simpleName}' for item '$itemId'")
                false
            }
        }
    }

    /**
     * Case-insensitive, resilient boolean parser.
     * Treats "true", "1", "on", "yes", "enable" as true.
     * Treats "false", "0", "off", "no", "disable" as false.
     */
    fun parseBoolean(raw: String): Boolean {
        val clean = raw.trim().lowercase()
        return when (clean) {
            "true", "1", "on", "yes", "enable", "enabled" -> true
            "false", "0", "off", "no", "disable", "disabled" -> false
            else -> clean.toBoolean()
        }
    }

    /**
     * Resolves matching option from a list of [ValueWithState].
     * Supports:
     * 1. Exact string match (`"LEVEL 2"`)
     * 2. Case-insensitive match (`"level 2"`)
     * 3. Underscore-space normalized match (`"LEVEL_2"` <-> `"LEVEL 2"`)
     * 4. Suffix / number match (`"2"` -> `"LEVEL 2"`)
     * 5. 0-based index match (`"2"` -> 3rd option if not matching ID suffix)
     */
    fun findMatchingOption(options: List<ValueWithState<*>>, raw: String): ValueWithState<*>? {
        val trimmed = raw.trim()
        if (options.isEmpty()) return null

        // 1. Exact string match
        options.firstOrNull { it.id?.toString() == trimmed }?.let { return it }

        // 2. Case-insensitive match
        options.firstOrNull { it.id?.toString().equals(trimmed, ignoreCase = true) }?.let { return it }

        // 3. Underscore / space normalized match
        val normalized = trimmed.replace('_', ' ')
        options.firstOrNull {
            it.id?.toString()?.replace('_', ' ').equals(normalized, ignoreCase = true)
        }?.let { return it }

        // 4. Suffix match (e.g. "2" matches "LEVEL 2" or "LEVEL_2")
        options.firstOrNull {
            val idStr = it.id?.toString() ?: ""
            idStr.endsWith(" $trimmed", ignoreCase = true) ||
                    idStr.endsWith("_$trimmed", ignoreCase = true)
        }?.let { return it }

        // 5. Index match (e.g. "0", "1", "2")
        val idx = trimmed.toIntOrNull()
        if (idx != null && idx in options.indices) {
            return options[idx]
        }

        return null
    }
}
