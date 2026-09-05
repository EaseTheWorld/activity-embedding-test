package com.example.feature.sound

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.util.Log

class SoundSettingsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.example.carsettings.provider.sound"
        const val TAG = "SoundSettingsProvider"

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
        const val KEY_MEDIA_VOLUME = "media_volume"
        const val KEY_NAV_VOLUME = "nav_volume"
        const val KEY_SURROUND_SOUND = "surround_sound"
        const val KEY_TOUCH_FEEDBACK = "touch_feedback"

        var mediaVolume: Int = 65
        var navVolume: Int = 80
        var surroundSoundEnabled: Boolean = true
        var touchFeedbackEnabled: Boolean = true
    }

    private val uriMatcher by lazy {
        UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_CATEGORY, CODE_CATEGORY)
            addURI(AUTHORITY, PATH_ITEMS, CODE_ITEMS)
        }
    }

    override fun onCreate(): Boolean {
        Log.d(TAG, "[$AUTHORITY] SoundSettingsProvider initialized")
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
                        "sound",
                        "Sound & Audio",
                        "Volume & acoustic staging via Resource ID",
                        20,
                        "com.example.carsettings.sound.OPEN",
                        "com.example.feature.sound.SoundSettingsActivity",
                        "ic_feature_sound",
                        R.string.category_sound_title,
                        R.string.category_sound_subtitle,
                        R.drawable.ic_feature_sound
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
                cursor.addRow(arrayOf("101", KEY_MEDIA_VOLUME, "Media Volume", "Music and streaming audio", "SLIDER", mediaVolume.toString(), 0, 100, "", 1))
                cursor.addRow(arrayOf("102", KEY_NAV_VOLUME, "Navigation Voice Volume", "Turn-by-turn spoken guidance", "SLIDER", navVolume.toString(), 0, 100, "", 1))
                cursor.addRow(arrayOf("103", KEY_SURROUND_SOUND, "Immersive Surround Sound", "Spatial audio staging", "TOGGLE", surroundSoundEnabled.toString(), 0, 0, "", 1))
                cursor.addRow(arrayOf("104", KEY_TOUCH_FEEDBACK, "Touchscreen Click Sounds", "Auditory tap confirmation", "TOGGLE", touchFeedbackEnabled.toString(), 0, 0, "", 1))
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
                KEY_MEDIA_VOLUME -> {
                    val parsed = value.toIntOrNull()
                    if (parsed != null) {
                        mediaVolume = parsed
                        true
                    } else {
                        false
                    }
                }
                KEY_NAV_VOLUME -> {
                    val parsed = value.toIntOrNull()
                    if (parsed != null) {
                        navVolume = parsed
                        true
                    } else {
                        false
                    }
                }
                KEY_SURROUND_SOUND -> {
                    surroundSoundEnabled = value.toBoolean()
                    true
                }
                KEY_TOUCH_FEEDBACK -> {
                    touchFeedbackEnabled = value.toBoolean()
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
