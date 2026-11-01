package com.example.lifecycleapp

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.core.item.AppConfig
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.Collections
import java.util.WeakHashMap

/**
 * End-to-end tests for MAIN/LAUNCHER and DeepLink Intent handling across Cold Start and Warm Start
 * in both Wide Split Mode and Narrow Overlay Mode.
 *
 * All intents are resolved implicitly via PackageManager (`ACTION_MAIN` + `CATEGORY_LAUNCHER` or
 * `ACTION_VIEW` + `myapp://navigate/...`) without hardcoding internal Activity class names
 * (such as `PrimaryActivity` or a future `DeepLinkRouterActivity`).
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LaunchAndDeepLinkUiAutomatorTest {

    private lateinit var device: UiDevice
    private val pkg = "com.example.lifecycleapp"
    private val lightPkg = "com.example.carsettings.light"
    private val timeout = 5000L

    private val trackedActivities = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Activity, Boolean>()))

    private val trackerCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            trackedActivities.add(activity)
        }
        override fun onActivityStarted(activity: Activity) {
            trackedActivities.add(activity)
        }
        override fun onActivityResumed(activity: Activity) {
            trackedActivities.add(activity)
        }
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {
            trackedActivities.remove(activity)
        }
    }

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        val app = instrumentation.targetContext.applicationContext as Application
        app.registerActivityLifecycleCallbacks(trackerCallbacks)
    }

    @After
    fun tearDown() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        app.unregisterActivityLifecycleCallbacks(trackerCallbacks)
    }

    /**
     * Completely finishes all in-process and external plugin activities so the next launch
     * is a true Cold Start of the task, and initializes the target [isOverlayMode] rules.
     */
    private fun prepareColdStart(isOverlayMode: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val snapshot = synchronized(trackedActivities) { trackedActivities.toList() }
        if (snapshot.isNotEmpty()) {
            instrumentation.runOnMainSync {
                for (act in snapshot) {
                    if (!act.isFinishing && !act.isDestroyed) {
                        act.finishAffinity()
                    }
                }
            }
        }
        device.executeShellCommand("am force-stop $lightPkg")
        device.waitForIdle()
        Thread.sleep(1000)

        AppConfig.IS_OVERLAY_MODE = isOverlayMode
        SplitInitializer().create(instrumentation.targetContext)
    }

    /**
     * Builds the standard Launcher Intent (`ACTION_MAIN` + `CATEGORY_LAUNCHER` +
     * `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`) without hardcoding any Activity class.
     */
    private fun createLauncherIntent(): Intent {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        assertNotNull("PackageManager.getLaunchIntentForPackage($pkg) must not be null", launchIntent)
        return launchIntent!!.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
    }

    /**
     * Builds an implicit DeepLink Intent (`ACTION_VIEW` + `myapp://navigate/...`) without hardcoding any Activity class.
     */
    private fun createDeepLinkIntent(uriString: String, extras: Bundle? = null): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (extras != null) {
                putExtras(extras)
            }
        }
    }

    private fun startIntentAndWait(intent: Intent, waitMs: Long = 1800L) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(intent)
        device.waitForIdle()
        Thread.sleep(waitMs)
    }

    private fun sendAppToBackground() {
        device.pressHome()
        device.waitForIdle()
        Thread.sleep(1000)
    }

    private fun selectCategoryInMenu(nameSubstring: String, fallbackKoSubstring: String) {
        val item = device.wait(Until.findObject(By.textContains(nameSubstring)), timeout)
            ?: device.wait(Until.findObject(By.textContains(fallbackKoSubstring)), timeout)
        assertNotNull("Category '$nameSubstring' should be visible in menu", item)
        item!!.click()
        device.waitForIdle()
        Thread.sleep(1000)
    }

    private fun scrollAndClickLumbarDetail() {
        var lumbarDetailBtn = device.findObject(By.descContains("View details for seat_lumbar"))
        var attempts = 0
        while (lumbarDetailBtn == null && attempts < 5) {
            device.swipe(1800, 1300, 1800, 300, 15)
            device.waitForIdle()
            Thread.sleep(500)
            lumbarDetailBtn = device.findObject(By.descContains("View details for seat_lumbar"))
            attempts++
        }
        assertNotNull("Lumbar detail arrow button should be visible after scrolling", lumbarDetailBtn)
        lumbarDetailBtn!!.click()
        device.waitForIdle()
        Thread.sleep(1000)
        assertLumbarDetailVisible("Should be on Lumbar Support ItemDetailScreen")
    }

    private fun expandOverlayDrawerInNarrowMode() {
        val btnMore = device.wait(Until.findObject(By.res(pkg, "btnMore")), timeout)
        assertNotNull("Collapsed MENU button should be visible in Narrow Mode", btnMore)
        btnMore!!.click()
        device.waitForIdle()
        Thread.sleep(800)
    }

    private fun assertLumbarDetailVisible(message: String) {
        val detailInfoText = device.wait(Until.findObject(By.textContains("Deep Link Navigation Info")), timeout)
            ?: device.wait(Until.findObject(By.descContains("Back to")), timeout)
        assertNotNull(message, detailInfoText)
    }

    private fun assertSeatCategoryListVisible(message: String) {
        val subActivityBtnOnSeatList = device.wait(
            Until.findObject(By.textContains("Open Sub-Setting Page")),
            timeout
        )
        assertNotNull(message, subActivityBtnOnSeatList)
    }

    @Test
    fun scenario1_WideMode_ColdStart_Launcher_And_WarmStart_LauncherFromBackground() {
        prepareColdStart(isOverlayMode = false)

        // 1. Cold start via MAIN/LAUNCHER
        startIntentAndWait(createLauncherIntent())

        val discoveryHeader = device.wait(Until.findObject(By.res(pkg, "tvDiscoveryStatus")), timeout)
        assertNotNull("Wide Mode Primary menu should be visible after Launcher cold start", discoveryHeader)

        // 2. Navigate to Seat -> Lumbar Support detail screen
        selectCategoryInMenu("Seat", "시트")
        scrollAndClickLumbarDetail()

        // 3. Send app to background via Home button
        sendAppToBackground()

        // 4. Warm start via MAIN/LAUNCHER from background -> must resume exact state (Lumbar Support)
        startIntentAndWait(createLauncherIntent())
        assertLumbarDetailVisible(
            "Launching from Launcher while in background (after Launcher start) must bring existing Lumbar Support screen to foreground without resetting to Home"
        )
    }

    @Test
    fun scenario2_WideMode_ColdStart_DeepLink_And_WarmStart_LauncherFromBackground() {
        prepareColdStart(isOverlayMode = false)

        // 1. Cold start directly via DeepLink to Seat -> Lumbar Support
        startIntentAndWait(createDeepLinkIntent("myapp://navigate/seat/seat_lumbar"))
        assertLumbarDetailVisible("Cold start DeepLink should open Seat -> Lumbar Support detail screen in Wide Mode")

        // 2. Send app to background via Home button
        sendAppToBackground()

        // 3. Warm start via MAIN/LAUNCHER from background -> must resume exact state (Lumbar Support)
        startIntentAndWait(createLauncherIntent())
        assertLumbarDetailVisible(
            "Launching from Launcher while in background (after DeepLink cold start) must bring existing Lumbar Support screen to foreground without resetting"
        )
    }

    @Test
    fun scenario3_WideMode_WarmStart_DeepLink_InForegroundAndBackground() {
        prepareColdStart(isOverlayMode = false)

        // 1. Cold start via MAIN/LAUNCHER and select Door category
        startIntentAndWait(createLauncherIntent())
        selectCategoryInMenu("Door", "도어")

        // 2. Warm start via DeepLink while in foreground -> should switch to Seat -> Lumbar Support
        startIntentAndWait(createDeepLinkIntent("myapp://navigate/seat/seat_lumbar"))
        assertLumbarDetailVisible("Foreground Warm Start DeepLink should switch from Door to Seat -> Lumbar Support")

        // 3. Send app to background, then Warm start via DeepLink to External Light category with value=PARKING
        sendAppToBackground()
        val extras = Bundle().apply { putString("value", "PARKING") }
        startIntentAndWait(createDeepLinkIntent("myapp://navigate/light/headlights", extras))

        val parkingStatus = device.wait(Until.findObject(By.textContains("Current: PARKING")), timeout)
        assertNotNull(
            "Background Warm Start DeepLink to external Light category with value=PARKING should bring app to foreground and update status",
            parkingStatus
        )
    }

    @Test
    fun scenario4_NarrowOverlayMode_ColdStart_Launcher_And_WarmStart_LauncherFromBackground() {
        prepareColdStart(isOverlayMode = true)

        // 1. Cold start via MAIN/LAUNCHER in Narrow Overlay Mode
        startIntentAndWait(createLauncherIntent())

        // 2. Expand drawer -> navigate to Seat -> Lumbar Support detail screen
        expandOverlayDrawerInNarrowMode()
        selectCategoryInMenu("Seat", "시트")
        scrollAndClickLumbarDetail()

        // 3. Send app to background via Home button
        sendAppToBackground()

        // 4. Warm start via MAIN/LAUNCHER from background -> must resume exact state (Lumbar Support + floating MENU button)
        startIntentAndWait(createLauncherIntent())
        assertLumbarDetailVisible(
            "Narrow Mode: Launching from Launcher while in background (after Launcher start) must preserve Lumbar Support detail screen"
        )
        val btnMore = device.wait(Until.findObject(By.res(pkg, "btnMore")), timeout)
        assertNotNull("Narrow Mode: Collapsed floating MENU button must remain visible after returning from background", btnMore)
    }

    @Test
    fun scenario5_NarrowOverlayMode_ColdStart_DeepLink_And_WarmStart_LauncherFromBackground() {
        prepareColdStart(isOverlayMode = true)

        // 1. Cold start via DeepLink in Narrow Overlay Mode
        startIntentAndWait(createDeepLinkIntent("myapp://navigate/seat/seat_lumbar"))
        assertLumbarDetailVisible("Narrow Mode: Cold start DeepLink should open Seat -> Lumbar Support detail screen")
        val btnMoreInitial = device.wait(Until.findObject(By.res(pkg, "btnMore")), timeout)
        assertNotNull("Narrow Mode: Collapsed floating MENU button must be visible after DeepLink cold start", btnMoreInitial)

        // 2. Send app to background via Home button
        sendAppToBackground()

        // 3. Warm start via MAIN/LAUNCHER from background -> must resume exact state (Lumbar Support)
        startIntentAndWait(createLauncherIntent())
        assertLumbarDetailVisible(
            "Narrow Mode: Launching from Launcher while in background (after DeepLink cold start) must preserve Lumbar Support detail screen"
        )
        val btnMoreAfterResume = device.wait(Until.findObject(By.res(pkg, "btnMore")), timeout)
        assertNotNull("Narrow Mode: Collapsed floating MENU button must remain visible after Launcher warm start", btnMoreAfterResume)
    }

    @Test
    fun scenario6_NarrowOverlayMode_WarmStart_DeepLink_InForegroundAndBackground() {
        prepareColdStart(isOverlayMode = true)

        // 1. Cold start via MAIN/LAUNCHER and select Door category
        startIntentAndWait(createLauncherIntent())
        expandOverlayDrawerInNarrowMode()
        selectCategoryInMenu("Door", "도어")

        // 2. Warm start via DeepLink while in foreground -> should switch to Seat -> Lumbar Support
        startIntentAndWait(createDeepLinkIntent("myapp://navigate/seat/seat_lumbar"))
        assertLumbarDetailVisible("Narrow Mode: Foreground Warm Start DeepLink should switch from Door to Seat -> Lumbar Support")

        // 3. Send app to background, then Warm start via DeepLink to External Light category with value=ON
        sendAppToBackground()
        val extras = Bundle().apply { putString("value", "ON") }
        startIntentAndWait(createDeepLinkIntent("myapp://navigate/light/headlights", extras))

        val onStatus = device.wait(Until.findObject(By.textContains("Current: ON")), timeout)
        assertNotNull(
            "Narrow Mode: Background Warm Start DeepLink to external Light category with value=ON should bring app to foreground and update status",
            onStatus
        )
        val btnMore = device.wait(Until.findObject(By.res(pkg, "btnMore")), timeout)
        assertNotNull("Narrow Mode: Collapsed floating MENU button must remain visible after DeepLink warm start", btnMore)
    }
}
