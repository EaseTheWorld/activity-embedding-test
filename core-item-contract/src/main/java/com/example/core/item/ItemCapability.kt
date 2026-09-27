package com.example.core.item

/**
 * Declares the schema and constraints of an accepted parameter for a setting item.
 * Suitable for serialization over IPC (ContentProvider / AppSearch / Intents)
 * and consumption by Voice Recognition / AI Assistant agents with zero shared code.
 */
data class ParameterSpec(
    val name: String,
    val type: String, // "boolean", "choice", "int", "float", "string"
    val min: Int? = null,
    val max: Int? = null,
    val options: List<String>? = null,
    val isRequired: Boolean = false,
    val description: String? = null
) {
    fun toJson(): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"name\":\"").append(escapeJson(name)).append("\",")
        sb.append("\"type\":\"").append(escapeJson(type)).append("\"")
        if (min != null) sb.append(",\"min\":").append(min)
        if (max != null) sb.append(",\"max\":").append(max)
        if (options != null) {
            sb.append(",\"options\":[")
            sb.append(options.joinToString(",") { "\"${escapeJson(it)}\"" })
            sb.append("]")
        }
        if (isRequired) sb.append(",\"required\":true")
        if (description != null) sb.append(",\"description\":\"").append(escapeJson(description)).append("\"")
        sb.append("}")
        return sb.toString()
    }

    companion object {
        fun escapeJson(str: String): String =
            str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")

        fun parseList(json: String?): List<ParameterSpec> {
            if (json.isNullOrBlank()) return emptyList()
            val trimmed = json.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
            val content = trimmed.substring(1, trimmed.length - 1).trim()
            if (content.isEmpty()) return emptyList()

            val list = mutableListOf<ParameterSpec>()
            val objRegex = Regex("\\{([^}]*)\\}")
            for (match in objRegex.findAll(content)) {
                val body = match.groupValues[1]
                val name = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: continue
                val type = Regex("\"type\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: "string"
                val min = Regex("\"min\"\\s*:\\s*(-?\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
                val max = Regex("\"max\"\\s*:\\s*(-?\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
                val required = Regex("\"required\"\\s*:\\s*true").containsMatchIn(body)
                val desc = Regex("\"description\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                val optionsMatch = Regex("\"options\"\\s*:\\s*\\[([^\\]]*)\\]").find(body)
                val options = optionsMatch?.groupValues?.get(1)?.let { optStr ->
                    Regex("\"([^\"]+)\"").findAll(optStr).map { it.groupValues[1] }.toList()
                }
                list.add(
                    ParameterSpec(
                        name = name,
                        type = type,
                        min = min,
                        max = max,
                        options = options,
                        isRequired = required,
                        description = desc
                    )
                )
            }
            return list
        }
    }
}

/**
 * Encapsulates the runtime semantic capability of a setting item.
 * Describes which actions and parameters are accepted, along with
 * natural language synonyms / keywords for voice assistant matching.
 */
data class ItemCapability(
    val itemId: String,
    val actionType: ItemType,
    val acceptedParameters: List<ParameterSpec> = emptyList(),
    val keywords: List<String> = emptyList()
) {
    fun parametersToJson(): String {
        return "[" + acceptedParameters.joinToString(",") { it.toJson() } + "]"
    }
}
