package com.example.feature.door

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.util.Log

class DoorSettingsProvider : ContentProvider() {

    companion object {
        private const val TAG = "DoorSettingsProvider"
        const val AUTHORITY = "com.example.carsettings.provider.door"

        const val PATH_CATEGORY = "category"
        const val PATH_ITEMS = "items"

        private const val CODE_CATEGORY = 1
        private const val CODE_ITEMS = 2

        // Category columns
        const val COLUMN_CATEGORY_ID = "category_id"
        const val COLUMN_CATEGORY_TITLE = "category_title"
        const val COLUMN_CATEGORY_SUBTITLE = "category_subtitle"
        const val COLUMN_CATEGORY_ORDER = "category_order"
        const val COLUMN_CATEGORY_TARGET_ACTION = "category_target_action"
        const val COLUMN_CATEGORY_TARGET_ACTIVITY = "category_target_activity"
        const val COLUMN_CATEGORY_ICON_NAME = "category_icon_name"
        const val COLUMN_CATEGORY_TITLE_RES_ID = "category_title_res_id"
        const val COLUMN_CATEGORY_SUBTITLE_RES_ID = "category_subtitle_res_id"
        const val COLUMN_CATEGORY_ICON_RES_ID = "category_icon_res_id"

        // Item columns
        const val COLUMN_ITEM_ID = "item_id"
        const val COLUMN_ITEM_KEY = "item_key"
        const val COLUMN_ITEM_TITLE = "item_title"
        const val COLUMN_ITEM_SUBTITLE = "item_subtitle"
        const val COLUMN_ITEM_TYPE = "item_type"
        const val COLUMN_ITEM_VALUE = "item_value"
        const val COLUMN_ITEM_MIN = "item_min"
        const val COLUMN_ITEM_MAX = "item_max"
        const val COLUMN_ITEM_OPTIONS = "item_options"
        const val COLUMN_ITEM_ENABLED = "item_enabled"
        const val COLUMN_ITEM_TITLE_RES_ID = "item_title_res_id"
        const val COLUMN_ITEM_SUBTITLE_RES_ID = "item_subtitle_res_id"

        // Call method
        const val METHOD_UPDATE_ITEM = "update_item"
        const val EXTRA_KEY = "extra_key"
        const val EXTRA_VALUE = "extra_value"
    }

    private val uriMatcher by lazy {
        UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_CATEGORY, CODE_CATEGORY)
            addURI(AUTHORITY, PATH_ITEMS, CODE_ITEMS)
        }
    }

    override fun onCreate(): Boolean {
        Log.d(TAG, "[$AUTHORITY] DoorSettingsProvider initialized with Item SSOT")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        return when (uriMatcher.match(uri)) {
            CODE_CATEGORY -> {
                val cursor = MatrixCursor(
                    arrayOf(
                        COLUMN_CATEGORY_ID,
                        COLUMN_CATEGORY_TITLE,
                        COLUMN_CATEGORY_SUBTITLE,
                        COLUMN_CATEGORY_ORDER,
                        COLUMN_CATEGORY_TARGET_ACTION,
                        COLUMN_CATEGORY_TARGET_ACTIVITY,
                        COLUMN_CATEGORY_ICON_NAME,
                        COLUMN_CATEGORY_TITLE_RES_ID,
                        COLUMN_CATEGORY_SUBTITLE_RES_ID,
                        COLUMN_CATEGORY_ICON_RES_ID
                    )
                )
                cursor.addRow(
                    arrayOf(
                        "door",
                        "Doors & Locks",
                        "Automatic locking and access controls",
                        40,
                        "com.example.carsettings.door.OPEN",
                        "com.example.lifecycleapp.GenericSettingsActivity",
                        "ic_feature_door",
                        R.string.category_door_title,
                        R.string.category_door_subtitle,
                        R.drawable.ic_feature_door
                    )
                )
                cursor.setNotificationUri(ctx.contentResolver, uri)
                cursor
            }
            CODE_ITEMS -> {
                val cursor = MatrixCursor(
                    arrayOf(
                        COLUMN_ITEM_ID,
                        COLUMN_ITEM_KEY,
                        COLUMN_ITEM_TITLE,
                        COLUMN_ITEM_SUBTITLE,
                        COLUMN_ITEM_TYPE,
                        COLUMN_ITEM_VALUE,
                        COLUMN_ITEM_MIN,
                        COLUMN_ITEM_MAX,
                        COLUMN_ITEM_OPTIONS,
                        COLUMN_ITEM_ENABLED,
                        COLUMN_ITEM_TITLE_RES_ID,
                        COLUMN_ITEM_SUBTITLE_RES_ID
                    )
                )
                // Dynamically iterate over DoorItemRegistry.items (List<Item> SSOT)
                DoorItemRegistry.items.forEachIndexed { index, item ->
                    val uiItem = item as? com.example.common.ui.settings.UiItem
                    val titleResId = uiItem?.titleRes ?: 0
                    val subtitleResId = uiItem?.subtitleRes ?: 0
                    val title = if (titleResId != 0) ctx.getString(titleResId) else item.key
                    val subtitle = if (subtitleResId != 0) ctx.getString(subtitleResId) else ""
                    val serializedValue = (item as? com.example.core.item.ValueItem<*>)?.serializedValue ?: ""

                    cursor.addRow(
                        arrayOf(
                            (index + 1).toString(),
                            item.key,
                            title,
                            subtitle,
                            item.type.name,
                            serializedValue,
                            0,
                            0,
                            "",
                            1,
                            titleResId,
                            subtitleResId
                        )
                    )
                }
                cursor.setNotificationUri(ctx.contentResolver, uri)
                cursor
            }
            else -> null
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == METHOD_UPDATE_ITEM) {
            val key = arg ?: extras?.getString(EXTRA_KEY) ?: extras?.getString("key") ?: return null
            val value = extras?.getString(EXTRA_VALUE) ?: extras?.getString("value") ?: return null
            val targetItem = DoorItemRegistry.findItem(key)
            if (targetItem != null) {
                if (targetItem is com.example.core.item.ToggleItem) {
                    targetItem.onValueChanged(value.toBoolean())
                }
                context?.contentResolver?.notifyChange(Uri.parse("content://$AUTHORITY/$PATH_ITEMS"), null)
                Log.d(TAG, "[$AUTHORITY] Item updated via SSOT: $key = $value")
                return Bundle().apply { putBoolean("success", true) }
            }
        }
        return super.call(method, arg, extras)
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
