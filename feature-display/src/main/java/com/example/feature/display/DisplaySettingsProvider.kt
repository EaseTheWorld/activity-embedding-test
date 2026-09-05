package com.example.feature.display

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.util.Log

class DisplaySettingsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.example.carsettings.provider.display"
        const val TAG = "DisplaySettingsProvider"

        const val PATH_CATEGORY = "category"
        const val PATH_ITEMS = "items"

        const val CODE_CATEGORY = 1
        const val CODE_ITEMS = 2

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

        // Call method
        const val METHOD_UPDATE_ITEM = "update_item"
        const val EXTRA_KEY = "extra_key"
        const val EXTRA_VALUE = "extra_value"

        // Keys & in-memory state
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_CLEAN_SCREEN_MODE = "clean_screen_mode"

        var brightness: Int = 85
        var themeMode: String = "DARK"
        var cleanScreenMode: Boolean = false
    }

    private val uriMatcher by lazy {
        UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_CATEGORY, CODE_CATEGORY)
            addURI(AUTHORITY, PATH_ITEMS, CODE_ITEMS)
        }
    }

    override fun onCreate(): Boolean {
        Log.d(TAG, "[$AUTHORITY] DisplaySettingsProvider initialized")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
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
                        "display",
                        "Center Display",
                        "Luminance & theme controls via Resource ID",
                        30,
                        "com.example.carsettings.display.OPEN",
                        "com.example.feature.display.DisplaySettingsActivity",
                        "ic_feature_display",
                        R.string.category_display_title,
                        R.string.category_display_subtitle,
                        R.drawable.ic_feature_display
                    )
                )
                cursor.setNotificationUri(context?.contentResolver, uri)
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
                        COLUMN_ITEM_ENABLED
                    )
                )
                cursor.addRow(arrayOf("201", KEY_BRIGHTNESS, "Touchscreen Brightness", "Center console luminance", "SLIDER", brightness.toString(), 10, 100, "", 1))
                cursor.addRow(arrayOf("202", KEY_THEME_MODE, "Appearance Theme", "Daytime or night theme mode", "CHOICE", themeMode, 0, 0, "AUTO,LIGHT,DARK", 1))
                cursor.addRow(arrayOf("203", KEY_CLEAN_SCREEN_MODE, "Screen Clean Mode", "Touch lockout for cleaning", "TOGGLE", cleanScreenMode.toString(), 0, 0, "", 1))
                cursor.setNotificationUri(context?.contentResolver, uri)
                cursor
            }
            else -> null
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == METHOD_UPDATE_ITEM) {
            val key = extras?.getString(EXTRA_KEY) ?: return null
            val value = extras.getString(EXTRA_VALUE) ?: return null
            val success = when (key) {
                KEY_BRIGHTNESS -> {
                    val parsed = value.toIntOrNull()
                    if (parsed != null) {
                        brightness = parsed
                        true
                    } else {
                        false
                    }
                }
                KEY_THEME_MODE -> {
                    themeMode = value
                    true
                }
                KEY_CLEAN_SCREEN_MODE -> {
                    cleanScreenMode = value.toBoolean()
                    true
                }
                else -> false
            }
            if (success) {
                context?.contentResolver?.notifyChange(Uri.parse("content://$AUTHORITY/$PATH_ITEMS"), null)
                Log.d(TAG, "[$AUTHORITY] Setting updated: $key = $value")
            }
            return Bundle().apply { putBoolean("success", success) }
        }
        return super.call(method, arg, extras)
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
