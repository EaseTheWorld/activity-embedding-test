package com.example.common.ui.settings

import android.content.Intent
import android.util.Log
import com.example.core.item.ChoiceItemViewModel
import com.example.core.item.ItemViewModel
import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.MutableItemViewModel
import com.example.core.item.ParameterizedMutableItemViewModel
import com.example.core.item.SerializedMutableItemViewModel
import com.example.core.item.ValueWithState

/**
 * Robust, universal type coercion and mutation helper for [ItemViewModelRegistry].
 *
 * Supports mutating setting items from:
 * 1. Intent Extras (e.g. `intent.putExtra("value", false)` or `adb shell am start --ez value false`)
 * 2. Parameter maps from multi-extra payloads
 * 3. ADB simulation broadcasts
 * 4. External IPC or test tools
 *
 * Adheres to Interface Segregation Principle (ISP):
 * Only ViewModels implementing [MutableItemViewModel] or [ParameterizedMutableItemViewModel] can be mutated.
 */
object ItemSetterHelper {
    private const val TAG = "ItemSetterHelper"

    /**
     * Intent Extra key representing the mutation payload.
     * Separates the mutation payload (POST body) from the URI destination (GET path).
     */
    const val EXTRA_VALUE = "value"

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
     * Applies key-value parameters (from Intent Extras) to [vm].
     *
     * 1. If [vm] implements [ParameterizedMutableItemViewModel], delegates directly to it.
     * 2. Otherwise falls back to resolving standard "value" key via [applyValue].
     *
     * @param vm Target ViewModel.
     * @param parameters Key-value map extracted from Intent Extras.
     * @return True if mutation was accepted and applied, false otherwise.
     */
    fun applyParameters(vm: ItemViewModel<*>, parameters: Map<String, String>): Boolean {
        if (vm is ParameterizedMutableItemViewModel) {
            val handled = vm.updateFromParameters(parameters)
            if (handled) {
                logI(TAG, "Applied parameters via ParameterizedMutableItemViewModel: $parameters")
                return true
            }
        }

        val rawValue = parameters[EXTRA_VALUE]
        if (rawValue != null) {
            return applyValue(vm, rawValue)
        }

        logW(TAG, "Cannot apply parameters: no matching handler or 'value' key in $parameters")
        return false
    }

    /**
     * Extracts and applies mutation payload from [Intent] Extras.
     *
     * In accordance with Separation of Concerns (SoC):
     * 1. URI represents the navigation destination (GET/READ).
     * 2. Intent Extras represent the mutation payload (POST/WRITE).
     *
     * Consumes the extra upon success ([Intent.removeExtra]) to avoid repeated mutations
     * during configuration changes or backstack navigation.
     *
     * @param registry The [ItemViewModelRegistry] owning the item's ViewModel.
     * @param itemId Unique ID of the setting item (e.g. "auto_lock", "driver_seat_heat", "seat_lumbar").
     * @param intent The incoming Intent carrying extras.
     * @return True if a value was found and successfully applied, false otherwise.
     */
    fun applyFromIntent(registry: ItemViewModelRegistry, itemId: String, intent: Intent): Boolean {
        val extraValue: Any? = intent.extras?.get(EXTRA_VALUE)
        val rawValue: Any = extraValue ?: return false

        val success = applyValue(registry, itemId, rawValue)
        if (success) {
            intent.removeExtra(EXTRA_VALUE)
        }
        return success
    }

    /**
     * Parses and converts [rawValue] (from Intent Extras, URI queries, or ADB)
     * into the strongly typed value expected by [vm].
     *
     * @return The strongly typed value ready for [MutableItemViewModel.setValue],
     *         or null if parsing/matching failed.
     */
    fun parseValue(vm: ItemViewModel<*>, rawValue: Any): Any? {
        val rawStr = rawValue.toString()

        // 1. Choice Item (Multi-Option with state)
        if (vm is ChoiceItemViewModel<*>) {
            val options = vm.optionStates.value
            val matchedOption = findMatchingOption(options, rawStr)
            if (matchedOption != null && matchedOption.id != null) {
                return matchedOption.id
            }
            if (vm.valueFlow.value is String) {
                return rawStr
            }
            return null
        }

        // 2. Strongly typed primitives based on current valueFlow type
        val current = vm.valueFlow.value
        return when (current) {
            is Boolean -> if (rawValue is Boolean) rawValue else parseBoolean(rawStr)
            is Int -> if (rawValue is Number) rawValue.toInt() else rawStr.toIntOrNull()
            is Float -> if (rawValue is Number) rawValue.toFloat() else rawStr.toFloatOrNull()
            is Double -> if (rawValue is Number) rawValue.toDouble() else rawStr.toDoubleOrNull()
            is String -> rawStr
            else -> null
        }
    }

    /**
     * Attempts to mutate the setting item owned by [vm] to [rawValue].
     * Directly interacts with [vm] without requiring [ItemViewModelRegistry].
     *
     * @param vm The [ItemViewModel] to mutate.
     * @param rawValue Value representation from Intent Extra, deep link, or input.
     * @return True if the mutation was successfully applied, false otherwise.
     */
    fun applyValue(vm: ItemViewModel<*>, rawValue: Any): Boolean {
        val rawStr = rawValue.toString()

        // 1. Dedicated SerializedMutableItemViewModel contract (e.g. SeatLumbarViewModel)
        if (vm is SerializedMutableItemViewModel) {
            val handled = vm.updateFromSerialized(rawStr)
            if (handled) {
                logI(TAG, "Applied serialized value '$rawStr' to item")
                return true
            } else {
                logW(TAG, "Serialized value '$rawStr' rejected by SerializedMutableItemViewModel")
                return false
            }
        }

        // ISP Check: ViewModel must support mutations
        if (vm !is MutableItemViewModel<*>) {
            logW(TAG, "Cannot apply value: ViewModel does not implement MutableItemViewModel (read-only)")
            return false
        }

        @Suppress("UNCHECKED_CAST")
        val mutableVm = vm as MutableItemViewModel<Any>

        val parsed = parseValue(vm, rawValue)
        if (parsed != null) {
            mutableVm.setValue(parsed)
            logI(TAG, "Applied parsed value '$parsed' to item")
            return true
        }

        // Reflection fallback for custom models with updateFromSerialized
        try {
            val method = vm.javaClass.methods.firstOrNull {
                it.name == "updateFromSerialized" &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == String::class.java
            }
            if (method != null) {
                val res = method.invoke(vm, rawStr)
                if (res is Boolean) {
                    if (res) {
                        logI(TAG, "Applied via reflection updateFromSerialized to item")
                        return true
                    } else {
                        logW(TAG, "updateFromSerialized returned false for item")
                        return false
                    }
                }
                logI(TAG, "Applied via reflection updateFromSerialized to item")
                return true
            }
        } catch (e: Exception) {
            logW(TAG, "Reflection invocation failed for item: $e")
        }

        logW(TAG, "Unsupported value type '${vm.valueFlow.value?.javaClass?.simpleName}' for item")
        return false
    }

    /**
     * Attempts to mutate the setting item specified by [itemId] to [rawValue].
     * Supports strongly typed values (Boolean, Int, Float, Double, String) as well
     * as raw strings.
     *
     * @param registry The [ItemViewModelRegistry] owning the item's ViewModel.
     * @param itemId Unique ID of the setting item (e.g. "auto_lock", "driver_seat_heat", "seat_lumbar").
     * @param rawValue Value representation from Intent Extra, deep link, or input.
     * @return True if the mutation was successfully applied, false otherwise.
     */
    fun applyValue(registry: ItemViewModelRegistry, itemId: String, rawValue: Any): Boolean {
        val vm = registry.getViewModel<Any>(itemId)
        if (vm == null) {
            logW(TAG, "Cannot apply value: no ViewModel registered for itemId '$itemId'")
            return false
        }
        return applyValue(vm, rawValue)
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
