package com.example.lifecycleapp

import com.example.feature.home.HomeNavigationBridge
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.example.feature.home.CategoryStackManager
import com.example.feature.home.HomeActivity

/**
 * Wide Mode Split Secondary Menu Activity (`Theme.LifecycleApp` - Opaque, static sidebar layout).
 *
 * Responsibilities:
 * 1. Launched by [HomeActivity] in Wide Mode (`AppConfig.isOverlayMode == false`).
 * 2. Positioned at the left 35% pane via `SplitPairRule` with `LayoutDirection.RIGHT_TO_LEFT`
 *    paired with [HomeActivity] (Primary, right 65%).
 * 3. Launches Category activities into the right 65% pane via `SplitPairRule(ListActivity <-> Category)`.
 * 4. Uses `FLAG_NOT_FOCUSABLE` and `ensureSecondaryFocus()` via `OverlayBoundsHelper.requestFocusOnSecondary(this)`
 *    so WindowManager focus remains strictly on the right content container (Home/Category).
 */
class ListActivity : BaseCategoryMenuActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SplitInitializer.ensureInitialized(this)

        // Wide Mode: Static opaque sidebar layout
        // Prevent ListActivity from stealing window focus while keeping touch input active
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        setContentView(R.layout.activity_list)
        initCategoryMenuViews()
        CategoryStackManager.trackSplitInfo(lifecycleScope, this, isNarrowMode = false)

        val initialCategoryId = intent?.getStringExtra(HomeActivity.EXTRA_INITIAL_CATEGORY_ID)
        val initialTargetIntent: android.content.Intent? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(HomeActivity.EXTRA_TARGET_INTENT, android.content.Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(HomeActivity.EXTRA_TARGET_INTENT)
        }
        if (!initialCategoryId.isNullOrEmpty()) {
            val matchedIndex = categories.indexOfFirst { it.id.equals(initialCategoryId, ignoreCase = true) }
            if (matchedIndex >= 0) {
                categoryAdapter?.selectedPosition = matchedIndex
                categoryAdapter?.notifyDataSetChanged()
                listViewCategories.smoothScrollToPosition(matchedIndex)
            }
            if (initialTargetIntent != null && savedInstanceState == null) {
                window.decorView.post {
                    if (!isFinishing && !isDestroyed) {
                        HomeNavigationBridge.launchCategory(this, initialTargetIntent)
                    }
                }
            }
        } else if (intent?.data != null || intent?.action == "com.example.lifecycleapp.open") {
            handleIntent(intent)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val topSecondary = LifecycleApp.getTopSecondaryActivity()
                if (topSecondary != null) {
                    Log.d(tag, "[$activityName] Forwarding Back key to top secondary activity: ${topSecondary.javaClass.simpleName}")
                    topSecondary.onBackPressedDispatcher.onBackPressed()
                } else {
                    Log.d(tag, "[$activityName] No in-process secondary activity found, fallback to navigateBackToHomeOrFinish()")
                    navigateBackToHomeOrFinish()
                }
            }
        })
    }

    private fun ensureSecondaryFocus() {
        if (isFinishing) return
        OverlayBoundsHelper.requestFocusOnSecondary(this)
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
