package com.example.core.item

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
 * Parses [rawStr] into the strongly typed domain value expected by [current].
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
