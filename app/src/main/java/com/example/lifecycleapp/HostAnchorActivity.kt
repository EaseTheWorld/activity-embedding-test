package com.example.lifecycleapp

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.window.embedding.ActivityStack
import androidx.window.embedding.EmbeddingBounds
import androidx.window.embedding.OverlayAttributes
import androidx.window.embedding.OverlayCreateParams
import androidx.window.embedding.SplitController
import androidx.window.embedding.setLaunchingActivityStack
import androidx.window.embedding.setOverlayCreateParams
import com.example.core.item.AppConfig
import com.example.feature.home.HomeActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Host & Left-Pane Anchor Activity ("Dummy Primary").
 *
 * Architectural Role:
 * 1. Acts as the single MAIN/LAUNCHER and Deeplink entry point of the application.
 * 2. Serves as the Primary TaskFragment anchor in SplitPairRule so that ALL content
 *    activities (including HomeActivity, GenericSettingsActivity, and external plugin activities)
 *    are consistently placed inside the SecondaryTaskFragment.
 *    - Wide mode (`!isOverlayMode`): Occupies left 35% (`AppConfig.PRIMARY_DRAWER_RATIO`), pushing
 *      SecondaryTaskFragment to start at x = 35%.
 *    - Narrow mode (`isOverlayMode`): Uses `SplitType.SPLIT_TYPE_EXPAND`, allowing
 *      SecondaryTaskFragment to occupy 100% full screen starting at x = 0%.
 * 3. Launches the real navigation UI (`PrimaryActivity`) as an always-on-top Overlay TaskFragment
 *    whose bounds are synchronized with `AppConfig.PRIMARY_DRAWER_RATIO`.
 */
class HostAnchorActivity : BaseLoggingActivity() {

    companion object {
        const val TAG_PRIMARY_OVERLAY = "tag_primary_overlay"
        var instance: HostAnchorActivity? = null
        var secondaryActivityStack: ActivityStack? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        // Observe SplitInfo to track the SecondaryTaskFragment stack token
        lifecycleScope.launch {
            try {
                SplitController.getInstance(this@HostAnchorActivity)
                    .splitInfoList(this@HostAnchorActivity)
                    .collectLatest { splitInfoList ->
                        val currentSplit = splitInfoList.firstOrNull {
                            it.primaryActivityStack.contains(this@HostAnchorActivity)
                        }
                        secondaryActivityStack = currentSplit?.secondaryActivityStack
                        Log.d(tag, "[$activityName] splitInfoList update: secondaryStack=$secondaryActivityStack")
                    }
            } catch (e: Exception) {
                Log.w(tag, "[$activityName] Failed to observe splitInfoList: $e")
            }
        }

        if (savedInstanceState == null) {
            SplitInitializer.isOverlayDrawerExpanded = !AppConfig.isOverlayMode(this)
            // 1. Launch PrimaryActivity as the unified Overlay FIRST while HostAnchorActivity is not yet in a split!
            // (WM Extensions forbids launching an overlay container from an activity already in a split)
            launchPrimaryOverlay(intent)
            // 2. Launch HomeActivity into SecondaryTaskFragment via SplitPairRule after overlay creation
            window.decorView.post {
                launchInitialSecondary()
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent != null && (intent.data != null || intent.action == "com.example.lifecycleapp.open")) {
            Log.d(tag, "[$activityName] Forwarding new deeplink intent to PrimaryActivity overlay: $intent")
            val forwardIntent = Intent(this, PrimaryActivity::class.java).apply {
                action = intent.action
                data = intent.data
                if (intent.extras != null) putExtras(intent.extras!!)
                putExtra(PrimaryActivity.EXTRA_IS_OVERLAY, true)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(forwardIntent)
        }
    }

    private fun launchInitialSecondary() {
        val homeIntent = Intent(this, HomeActivity::class.java).apply {
            action = ACTION_SETTINGS_EMBED
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        Log.i(tag, "[$activityName] Launching HomeActivity into SecondaryTaskFragment")
        startActivity(homeIntent)
    }

    private fun launchPrimaryOverlay(launchIntent: Intent?) {
        try {
            val isNarrowOverlay = AppConfig.isOverlayMode(this)
            val bounds = SplitInitializer.computeOverlayBounds(this, isExpanded = !isNarrowOverlay)
            val overlayParams = OverlayCreateParams.Builder()
                .setTag(TAG_PRIMARY_OVERLAY)
                .setOverlayAttributes(OverlayAttributes(bounds))
                .build()

            val options = ActivityOptions.makeBasic().toBundle()
            options.setOverlayCreateParams(this, overlayParams)
            // Crucial: Set shouldAssociateWithLaunchingActivity = false so WM Extensions creates an
            // Always-On-Top Pinned Overlay (setTaskFragmentPinned=true) and allows HostAnchorActivity
            // to still form a SplitPairRule with SecondaryTaskFragment underneath!
            options.putBoolean(
                "androidx.window.extensions.embedding.shouldAssociateWithLaunchingActivity",
                false
            )

            val menuIntent = Intent(this, PrimaryActivity::class.java).apply {
                putExtra(PrimaryActivity.EXTRA_IS_OVERLAY, true)
                // Forward any initial deeplink to PrimaryActivity so it can select and launch the category
                if (launchIntent?.data != null || launchIntent?.action == "com.example.lifecycleapp.open") {
                    action = launchIntent.action
                    data = launchIntent.data
                    if (launchIntent.extras != null) putExtras(launchIntent.extras!!)
                }
            }
            Log.i(tag, "[$activityName] Launching PrimaryActivity as Unified Always-On-Top Overlay (isNarrowOverlay=$isNarrowOverlay, bounds=$bounds)")
            startActivity(menuIntent, options)
        } catch (e: Exception) {
            Log.e(tag, "[$activityName] Failed to launch PrimaryActivity overlay: $e", e)
        }
    }

    /**
     * Launches a category intent directly into the SecondaryTaskFragment.
     */
    fun launchInSecondary(targetIntent: Intent) {
        targetIntent.action = ACTION_SETTINGS_EMBED
        val optionsBundle = Bundle()
        secondaryActivityStack?.let { stack ->
            if (!stack.isEmpty) {
                optionsBundle.setLaunchingActivityStack(this, stack)
                Log.d(tag, "[$activityName] Targeting SecondaryActivityStack: $stack")
            }
        }
        startActivity(targetIntent, if (optionsBundle.isEmpty) null else optionsBundle)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }
}
