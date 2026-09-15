package com.example.feature.light

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.util.Log

class LightSettingsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.example.carsettings.provider.light"
        const val TAG = "LightSettingsProvider"

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
        const val METHOD_SET_VISIBILITY = "set_visibility"
        const val EXTRA_KEY = "extra_key"
        const val EXTRA_VALUE = "extra_value"
        const val EXTRA_VISIBLE = "visible"

        // Dynamic visibility condition
        var isCategoryVisible: Boolean = true

        // Keys & in-memory state
        const val KEY_HEADLIGHTS = "headlights"
        const val KEY_AMBIENT_LIGHT = "ambient_light"
        const val KEY_FRUNK_LIGHT = "frunk_light"
        const val KEY_TRUNK_LIGHT = "trunk_light"
        const val KEY_AUTO_HIGH_BEAM = "auto_high_beam"

        var headlightsMode: String = "AUTO"
        var ambientLightEnabled: Boolean = true
        var frunkLightEnabled: Boolean = false
        var trunkLightEnabled: Boolean = false
        var autoHighBeamEnabled: Boolean = true
    }

    private val uriMatcher by lazy {
        UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_CATEGORY, CODE_CATEGORY)
            addURI(AUTHORITY, PATH_ITEMS, CODE_ITEMS)
        }
    }

    override fun onCreate(): Boolean {
        Log.d(TAG, "[$AUTHORITY] LightSettingsProvider initialized")
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
                if (isCategoryVisible) {
                    cursor.addRow(
                        arrayOf(
                            "light",
                            "Vehicle Lights",
                            "Exterior & interior illumination via Resource ID",
                            10,
                            "com.example.carsettings.light.OPEN",
                            "com.example.feature.light.LightSettingsActivity",
                            "ic_feature_light",
                            R.string.category_light_title,
                            R.string.category_light_subtitle,
                            R.drawable.ic_feature_light
                        )
                    )
                }
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
                cursor.addRow(arrayOf("1", KEY_HEADLIGHTS, "Headlights", "Automatic sensor activation", "CHOICE", headlightsMode, 0, 0, "OFF,PARKING,ON,AUTO", 1))
                cursor.addRow(arrayOf("2", KEY_AMBIENT_LIGHT, "Ambient Lighting", "Interior footwell glow", "TOGGLE", ambientLightEnabled.toString(), 0, 0, "", 1))
                cursor.addRow(arrayOf("3", KEY_FRUNK_LIGHT, "Front Trunk Light", "Front cargo illumination", "TOGGLE", frunkLightEnabled.toString(), 0, 0, "", 1))
                cursor.addRow(arrayOf("4", KEY_TRUNK_LIGHT, "Rear Trunk Light", "Rear luggage illumination", "TOGGLE", trunkLightEnabled.toString(), 0, 0, "", 1))
                cursor.addRow(arrayOf("5", KEY_AUTO_HIGH_BEAM, "Auto High Beam", "Automatic anti-glare dipping", "TOGGLE", autoHighBeamEnabled.toString(), 0, 0, "", 1))
                cursor.setNotificationUri(context?.contentResolver, uri)
                cursor
            }
            else -> null
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == METHOD_SET_VISIBILITY) {
            val visible = extras?.getBoolean(EXTRA_VISIBLE, true)
                ?: extras?.getString(EXTRA_VISIBLE)?.toBoolean()
                ?: true
            isCategoryVisible = visible
            val categoryUri = Uri.parse("content://$AUTHORITY/$PATH_CATEGORY")
            context?.contentResolver?.notifyChange(categoryUri, null)
            Log.d(TAG, "[$AUTHORITY] set_visibility: isCategoryVisible = $visible, notified change on $categoryUri")
            return Bundle().apply {
                putBoolean("success", true)
                putBoolean(EXTRA_VISIBLE, visible)
            }
        }
        if (method == METHOD_UPDATE_ITEM) {
            val key = extras?.getString(EXTRA_KEY) ?: return null
            val value = extras.getString(EXTRA_VALUE) ?: return null
            val success = when (key) {
                KEY_HEADLIGHTS -> { headlightsMode = value; true }
                KEY_AMBIENT_LIGHT -> { ambientLightEnabled = value.toBoolean(); true }
                KEY_FRUNK_LIGHT -> { frunkLightEnabled = value.toBoolean(); true }
                KEY_TRUNK_LIGHT -> { trunkLightEnabled = value.toBoolean(); true }
                KEY_AUTO_HIGH_BEAM -> { autoHighBeamEnabled = value.toBoolean(); true }
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
