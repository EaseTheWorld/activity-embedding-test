package com.example.lifecycleapp

import com.example.core.item.ItemViewModelRegistry
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Universal Data-Driven Settings Activity powered by Jetpack Compose.
 * Inherits the common generic settings implementation from :common-ui-settings.
 * Injects [ItemViewModelRegistry] via Hilt with fallback to [VehicleHardwareSimulator].
 */
@AndroidEntryPoint
class GenericSettingsActivity : com.example.common.ui.settings.GenericSettingsActivity() {

    @Inject
    lateinit var injectedViewModelRegistry: ItemViewModelRegistry

    override fun getViewModelRegistry(): ItemViewModelRegistry =
        if (::injectedViewModelRegistry.isInitialized) injectedViewModelRegistry
        else VehicleHardwareSimulator.viewModelRegistry

    override fun onResume() {
        super.onResume()
        window.decorView.postDelayed({
            if (!isFinishing && !isDestroyed) {
                OverlayBoundsHelper.requestFocusOnSecondary(this)
            }
        }, 100L)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        ModeTransitionManager.checkAndHandleTransition(this, newConfig)
    }

    companion object {
        const val EXTRA_CATEGORY_ID = com.example.common.ui.settings.GenericSettingsActivity.EXTRA_CATEGORY_ID
        const val EXTRA_AUTHORITY = com.example.common.ui.settings.GenericSettingsActivity.EXTRA_AUTHORITY
        const val EXTRA_TITLE = com.example.common.ui.settings.GenericSettingsActivity.EXTRA_TITLE
    }
}

