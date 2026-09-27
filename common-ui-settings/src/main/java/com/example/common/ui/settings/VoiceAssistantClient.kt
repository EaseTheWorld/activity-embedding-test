package com.example.common.ui.settings

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.example.core.item.ParameterSpec

/**
 * Representation of a Setting Item capability discovered via ContentProvider.
 * Contains all metadata needed for an external voice assistant or AI agent
 * to match natural speech, extract parameters, and dispatch control actions.
 */
data class VoiceDiscoveredItem(
    val itemKey: String,
    val title: String,
    val subtitle: String,
    val itemType: String,
    val currentValue: String,
    val parameters: List<ParameterSpec>,
    val keywords: List<String>,
    val targetAction: String,
    val targetActivity: String,
    val authority: String
)

/**
 * Parsed voice command result ready for headless RPC or UI Deep Link execution.
 */
data class VoiceCommandResult(
    val targetItem: VoiceDiscoveredItem,
    val matchedParameters: Map<String, String>,
    val rawUtterance: String
)

/**
 * Client simulator demonstrating how an external Voice Recognition / AI Assistant App
 * discovers setting capabilities via ContentProvider (Zero Shared Code),
 * matches natural language utterances, and dispatches actions (Headless RPC vs UI Deep Link).
 */
class VoiceAssistantClient(
    private val contentResolver: ContentResolver? = null
) {
    companion object {
        private const val TAG = "VoiceAssistantClient"

        const val COLUMN_ITEM_KEY = "item_key"
        const val COLUMN_ITEM_TITLE = "item_title"
        const val COLUMN_ITEM_SUBTITLE = "item_subtitle"
        const val COLUMN_ITEM_TYPE = "item_type"
        const val COLUMN_ITEM_VALUE = "item_value"
        const val COLUMN_ITEM_PARAMETERS = "item_parameters"
        const val COLUMN_ITEM_KEYWORDS = "item_keywords"
        const val COLUMN_TARGET_ACTION = "target_action"
        const val COLUMN_TARGET_ACTIVITY = "target_activity"
    }

    private val _inventory = mutableListOf<VoiceDiscoveredItem>()
    val inventory: List<VoiceDiscoveredItem> get() = _inventory

    /**
     * Ingests items from a ContentProvider cursor into the voice inventory.
     */
    fun indexItems(cursor: Cursor, authority: String): List<VoiceDiscoveredItem> {
        val discovered = mutableListOf<VoiceDiscoveredItem>()
        if (cursor.moveToFirst()) {
            val keyIdx = cursor.getColumnIndex(COLUMN_ITEM_KEY)
            val titleIdx = cursor.getColumnIndex(COLUMN_ITEM_TITLE)
            val subIdx = cursor.getColumnIndex(COLUMN_ITEM_SUBTITLE)
            val typeIdx = cursor.getColumnIndex(COLUMN_ITEM_TYPE)
            val valIdx = cursor.getColumnIndex(COLUMN_ITEM_VALUE)
            val paramIdx = cursor.getColumnIndex(COLUMN_ITEM_PARAMETERS)
            val kwIdx = cursor.getColumnIndex(COLUMN_ITEM_KEYWORDS)
            val actIdx = cursor.getColumnIndex(COLUMN_TARGET_ACTION)
            val compIdx = cursor.getColumnIndex(COLUMN_TARGET_ACTIVITY)

            do {
                val key = if (keyIdx >= 0) cursor.getString(keyIdx) else continue
                val title = if (titleIdx >= 0) cursor.getString(titleIdx) else key
                val subtitle = if (subIdx >= 0) cursor.getString(subIdx) else ""
                val itemType = if (typeIdx >= 0) cursor.getString(typeIdx) else "CUSTOM"
                val currVal = if (valIdx >= 0) cursor.getString(valIdx) else ""
                val paramJson = if (paramIdx >= 0) cursor.getString(paramIdx) else ""
                val kwStr = if (kwIdx >= 0) cursor.getString(kwIdx) else ""
                val targetAction = if (actIdx >= 0) cursor.getString(actIdx) else ""
                val targetActivity = if (compIdx >= 0) cursor.getString(compIdx) else ""

                val parameters = ParameterSpec.parseList(paramJson)
                val keywords = kwStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                val item = VoiceDiscoveredItem(
                    itemKey = key,
                    title = title,
                    subtitle = subtitle,
                    itemType = itemType,
                    currentValue = currVal,
                    parameters = parameters,
                    keywords = keywords,
                    targetAction = targetAction,
                    targetActivity = targetActivity,
                    authority = authority
                )
                discovered.add(item)
            } while (cursor.moveToNext())
        }
        _inventory.addAll(discovered)
        Log.d(TAG, "Indexed ${discovered.size} items from $authority into VoiceAssistantClient")
        return discovered
    }

    /**
     * Registers discovered items directly into inventory (e.g. from tests or in-memory discovery).
     */
    fun registerDiscoveredItems(items: Collection<VoiceDiscoveredItem>) {
        _inventory.addAll(items)
    }

    /**
     * Clears all indexed inventory items.
     */
    fun clearInventory() {
        _inventory.clear()
    }

    /**
     * Resolves a natural language voice utterance to an item capability and parameter map.
     */
    fun resolveVoiceCommand(utterance: String): VoiceCommandResult? {
        val clean = utterance.replace("\\s+".toRegex(), " ").trim()
        val lower = clean.lowercase()
        val noSpaces = lower.replace(" ", "")

        // 1. Find matching item by keywords, title, or key (space-tolerant for Korean)
        val matchedItem = _inventory.firstOrNull { item ->
            item.keywords.any { kw ->
                val kwLower = kw.lowercase()
                val kwNoSpace = kwLower.replace(" ", "")
                lower.contains(kwLower) || noSpaces.contains(kwNoSpace)
            } ||
                lower.contains(item.title.lowercase()) ||
                noSpaces.contains(item.title.lowercase().replace(" ", "")) ||
                lower.contains(item.itemKey.lowercase())
        } ?: return null

        val params = mutableMapOf<String, String>()

        // 2. Extract parameters based on item capabilities
        for (spec in matchedItem.parameters) {
            when (spec.type.lowercase()) {
                "boolean" -> {
                    if (lower.contains("켜") || lower.contains("on") || lower.contains("잠가") || lower.contains("활성")) {
                        params[spec.name] = "true"
                    } else if (lower.contains("꺼") || lower.contains("off") || lower.contains("풀어") || lower.contains("해제") || lower.contains("비활성")) {
                        params[spec.name] = "false"
                    }
                }
                "choice" -> {
                    // Try to match specific options
                    spec.options?.forEach { opt ->
                        val optClean = opt.lowercase().replace("level", "").replace(" ", "").trim()
                        if (lower.contains(opt.lowercase()) || (optClean.isNotEmpty() && lower.contains("${optClean}단"))) {
                            params[spec.name] = opt
                        }
                    }
                    if (!params.containsKey(spec.name)) {
                        // Heuristic for levels like 1단, 2단, 3단
                        val levelMatch = Regex("([1-9])단").find(lower)
                        if (levelMatch != null) {
                            val num = levelMatch.groupValues[1]
                            val matchedOpt = spec.options?.firstOrNull { it.contains(num) }
                            if (matchedOpt != null) {
                                params[spec.name] = matchedOpt
                            }
                        } else if (lower.contains("off") || lower.contains("꺼")) {
                            val offOpt = spec.options?.firstOrNull { it.equals("off", ignoreCase = true) }
                            if (offOpt != null) {
                                params[spec.name] = offOpt
                            }
                        }
                    }
                }
                "int" -> {
                    // Look for key-specific number or general number
                    val keyRegex = Regex("${spec.name}\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
                    val heightRegex = if (spec.name == "height") Regex("높이\\s*(\\d+)") else null
                    val depthRegex = if (spec.name == "depth") Regex("깊이\\s*(\\d+)") else null

                    val num = keyRegex.find(lower)?.groupValues?.get(1)
                        ?: heightRegex?.find(lower)?.groupValues?.get(1)
                        ?: depthRegex?.find(lower)?.groupValues?.get(1)

                    if (num != null) {
                        params[spec.name] = num
                    }
                }
                "string" -> {
                    // Fallback
                }
            }
        }

        return VoiceCommandResult(
            targetItem = matchedItem,
            matchedParameters = params,
            rawUtterance = utterance
        )
    }

    /**
     * Constructs a UI Deep Link Intent to navigate the user to the setting item with visual focus.
     */
    fun createDeepLinkIntent(command: VoiceCommandResult): Intent {
        val intent = Intent(command.targetItem.targetAction)
        intent.putExtra("target_item", command.targetItem.itemKey)
        command.matchedParameters.forEach { (k, v) ->
            intent.putExtra(k, v)
        }
        return intent
    }

    /**
     * Extracts pure key-value deep link parameters (suitable for Intent Extras or URI queries).
     */
    fun createDeepLinkParameters(command: VoiceCommandResult): Map<String, String> {
        val map = mutableMapOf<String, String>()
        map["target_item"] = command.targetItem.itemKey
        map.putAll(command.matchedParameters)
        return map
    }
}
