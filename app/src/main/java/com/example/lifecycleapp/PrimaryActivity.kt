package com.example.lifecycleapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.OnBackPressedCallback
import com.example.core.item.AppConfig
import com.example.feature.home.HomeActivity

/**
 * Wide Mode Split Primary Activity (`Theme.LifecycleApp` - Opaque, static sidebar layout).
 *
 * Responsibilities:
 * 1. Serves as the MAIN/LAUNCHER and Deeplink entry point.
 *    - If in Narrow Mode (`AppConfig.isOverlayMode == true`), immediately redirects to `HomeActivity`
 *      (which in turn spawns [PrimaryOverlayActivity] as a Pinned Overlay) and finishes.
 * 2. In Wide Mode (`AppConfig.isOverlayMode == false`), acts as the permanently visible left 35%
 *    pane of `SplitPairRule` and launches `HomeActivity` + categories into the right 65% `SecondaryTaskFragment`.
 * 3. Uses `FLAG_NOT_FOCUSABLE` and `ensureSecondaryFocus()` via `OverlayBoundsHelper.requestFocusOnSecondary(this)`
 *    so WindowManager focus remains strictly on the Secondary container (including external apps).
 */
class PrimaryActivity : BaseCategoryMenuActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // In Narrow Mode, delegate to HomeActivity (which launches PrimaryOverlayActivity) and exit
        if (AppConfig.isOverlayMode(this)) {
            Log.i(tag, "[$activityName] Narrow Mode entry -> bootstrapping HomeActivity (which spawns PrimaryOverlayActivity)")
            val homeIntent = Intent(this, HomeActivity::class.java).apply {
                action = ACTION_SETTINGS_EMBED
                if (intent?.data != null) {
                    putExtra(HomeActivity.EXTRA_INITIAL_DEEPLINK_URI, intent.data.toString())
                }
                if (intent?.action != null && intent?.action != Intent.ACTION_MAIN) {
                    putExtra(HomeActivity.EXTRA_INITIAL_DEEPLINK_ACTION, intent.action)
                }
                if (intent?.extras != null) {
                    putExtra(HomeActivity.EXTRA_INITIAL_DEEPLINK_EXTRAS, intent.extras)
                }
            }
            startActivity(homeIntent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            finish()
            return
        }

        // Wide Mode: Static opaque sidebar layout (no overlay toggle buttons)
        // Prevent PrimaryActivity from stealing window focus while keeping touch input active,
        // paired with requestFocusOnSecondary() so mFocusedApp always points to Secondary.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        setContentView(R.layout.activity_primary)
        initCategoryMenuViews()

        if (savedInstanceState == null) {
            val homeIntent = Intent(this, HomeActivity::class.java).apply {
                action = ACTION_SETTINGS_EMBED
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            Log.i(tag, "[$activityName] Wide Mode -> launching HomeActivity into root SecondaryTaskFragment via SplitPairRule")
            startActivity(homeIntent)
        }

        if (intent?.data != null || intent?.action == "com.example.lifecycleapp.open") {
            window.decorView.post {
                handleIntent(intent)
            }
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
