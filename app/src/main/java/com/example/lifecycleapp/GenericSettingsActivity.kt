package com.example.lifecycleapp

import com.example.core.item.ItemViewModelRegistry

/**
 * Universal Data-Driven Settings Activity powered by Jetpack Compose.
 * Inherits the common generic settings implementation from :common-ui-settings.
 * Retained in this package for backwards-compatible ComponentName resolution.
 */
class GenericSettingsActivity : com.example.common.ui.settings.GenericSettingsActivity() {

    override fun getViewModelRegistry(): ItemViewModelRegistry =
        VehicleHardwareSimulator.viewModelRegistry

    companion object {
        const val EXTRA_CATEGORY_ID = com.example.common.ui.settings.GenericSettingsActivity.EXTRA_CATEGORY_ID
        const val EXTRA_AUTHORITY = com.example.common.ui.settings.GenericSettingsActivity.EXTRA_AUTHORITY
        const val EXTRA_TITLE = com.example.common.ui.settings.GenericSettingsActivity.EXTRA_TITLE
    }
}

