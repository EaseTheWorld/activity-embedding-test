package com.example.lifecycleapp

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import com.example.core.item.AppConfig
import com.example.feature.home.HomeActivity
import com.example.feature.home.HomeNavigationBridge
import java.lang.ref.WeakReference

/**
 * Task Root Activity for the application.
 *
 * Responsibilities:
 * 1. Wide Mode (`AppConfig.isOverlayMode == false`):
 *    - Serves as the Task Root and primary left 35% sidebar.
 *    - Pairs with Home/Category in the right 65% secondary container via [SplitInitializer].
 * 2. Narrow Mode (`AppConfig.isOverlayMode == true`):
 *    - Serves as the pristine Task Root beneath [HomeActivity].
 *    - Launches [HomeActivity] with the menu drawer [ComponentName] extra so each category main screen
 *      renders its top-left circular menu button + category title directly inside its layout.
 */
class ListActivity : BaseCategoryMenuActivity() {

    companion object {
        private var activeInstanceRef: WeakReference<ListActivity>? = null

        fun getActiveInstance(): ListActivity? =
            activeInstanceRef?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeInstanceRef = WeakReference(this)
        val isNarrow = AppConfig.isOverlayMode(this)
        SplitInitializer.ensureInitialized(this, isNarrow)

        if (!isNarrow) {
            // Wide Mode: Static sidebar layout on the left 35%
            setContentView(R.layout.activity_list)
            initCategoryMenuViews()
        }

        val initialCategoryId = intent?.getStringExtra(HomeActivity.EXTRA_INITIAL_CATEGORY_ID)
        val initialTargetIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(HomeActivity.EXTRA_TARGET_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(HomeActivity.EXTRA_TARGET_INTENT)
        }

        if (savedInstanceState == null) {
            val menuComponent = ComponentName(this, ListWithTranslucentActivity::class.java)
            val homeIntent = Intent(this, HomeActivity::class.java).apply {
                action = ACTION_SETTINGS_EMBED
                putExtra(AppConfig.EXTRA_MENU_COMPONENT, menuComponent)
                putExtra(
                    AppConfig.EXTRA_CATEGORY_HEADER_BREAKPOINT_DP,
                    AppConfig.OVERLAY_MODE_WIDTH_DP_BREAKPOINT
                )
                if (initialCategoryId != null) {
                    putExtra(HomeActivity.EXTRA_INITIAL_CATEGORY_ID, initialCategoryId)
                }
                if (initialTargetIntent != null) {
                    putExtra(HomeActivity.EXTRA_TARGET_INTENT, initialTargetIntent)
                }
            }
            startActivity(homeIntent)
            overridePendingTransition(0, 0)
        }

        if (!initialCategoryId.isNullOrEmpty() && !isNarrow) {
            val matchedIndex = categories.indexOfFirst { it.id.equals(initialCategoryId, ignoreCase = true) }
            if (matchedIndex >= 0) {
                categoryAdapter?.selectedPosition = matchedIndex
                categoryAdapter?.notifyDataSetChanged()
                listViewCategories.smoothScrollToPosition(matchedIndex)
            }
        } else if (intent?.data != null || intent?.action == "com.example.lifecycleapp.open") {
            handleIntent(intent)
        }
    }

    /**
     * Ensures the left 35% sidebar layout is inflated and bound when transitioning to Wide Mode
     * without restarting the activity.
     */
    fun ensureWideContentView() {
        if (categoryAdapter == null) {
            setContentView(R.layout.activity_list)
            initCategoryMenuViews()
            val initialCategoryId = intent?.getStringExtra(HomeActivity.EXTRA_INITIAL_CATEGORY_ID)
            if (!initialCategoryId.isNullOrEmpty()) {
                val matchedIndex = categories.indexOfFirst { it.id.equals(initialCategoryId, ignoreCase = true) }
                if (matchedIndex >= 0) {
                    categoryAdapter?.selectedPosition = matchedIndex
                    categoryAdapter?.notifyDataSetChanged()
                    listViewCategories.smoothScrollToPosition(matchedIndex)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        ModeTransitionManager.checkAndHandleTransition(this, newConfig)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstanceRef?.get() === this) {
            activeInstanceRef = null
        }
    }

    private fun ensureSecondaryFocus() {
        if (isFinishing) return
        OverlayBoundsHelper.requestFocusOnSecondary(this)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.decorView.post {
            if (!isFinishing && !isDestroyed) {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            }
        }

        window.decorView.postDelayed({
            if (!isFinishing) {
                OverlayBoundsHelper.requestFocusOnSecondary(this)
            }
        }, 50L)
        window.decorView.postDelayed({
            if (!isFinishing) {
                OverlayBoundsHelper.requestFocusOnSecondary(this)
            }
        }, 300L)
    }

    override fun onBeforeCategoryLaunch() {
        ensureSecondaryFocus()
    }

    override fun onHomeRevealed() {
        super.onHomeRevealed()
        ensureSecondaryFocus()
    }

    override fun onResume() {
        super.onResume()
        ensureSecondaryFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isFinishing) {
            ensureSecondaryFocus()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        val handled = super.dispatchTouchEvent(ev)
        if (ev != null && (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL)) {
            ensureSecondaryFocus()
        }
        return handled
    }
}
