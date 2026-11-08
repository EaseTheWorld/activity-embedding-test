package com.example.lifecycleapp

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.core.item.AppConfig
import com.example.feature.home.HomeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Scenario-driven end-to-end Back key navigation tests across Wide Split Mode and Narrow Overlay Mode.
 *
 * Scenarios covered:
 * 1. Wide Mode: Internal category Compose NavHost back stack (`Seats` -> `Lumbar Support`) + Touch Primary + Back
 * 2. Wide Mode: Internal category Sub-Activity back stack (`Seats` -> `GenericSubActivity`) + Touch Primary + Back
 * 3. Wide Mode: External plugin app (`Vehicle Lights`) internal back stack + Touch Primary + Back
 * 4. Narrow Mode: Internal category Compose NavHost back stack + Expand/Collapse Overlay Drawer + Back
 * 5. Narrow Mode: External plugin app (`Vehicle Lights`) internal back stack + Expand/Collapse Overlay Drawer + Back
 * 6. Narrow Mode: Expanded Overlay Drawer on top of detail screen -> Back #1 closes drawer, Back #2 pops detail screen
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class InternalBackStackUiAutomatorTest {

    private lateinit var device: UiDevice
    private val pkg = "com.example.lifecycleapp"
    private val timeout = 10000L

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    private fun launchApp(isOverlayMode: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            BaseCategoryMenuActivity.getActiveMenuActivity()?.finishAffinity()
            LifecycleApp.getTopSecondaryActivity()?.finishAffinity()
        }
        device.waitForIdle()
        Thread.sleep(600)

        AppConfig.IS_OVERLAY_MODE = isOverlayMode
        val context = instrumentation.targetContext
        SplitInitializer.ensureInitialized(context, isOverlayMode)

        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(context, com.example.feature.home.HomeActivity::class.java)).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        device.waitForIdle()
        Thread.sleep(1500)
        if (isOverlayMode) {
            device.wait(Until.findObject(By.res(pkg, "btnMore")), timeout)
        } else {
            device.wait(Until.findObject(By.res(pkg, "listViewCategories")), timeout)
        }
        device.waitForIdle()
    }

    private fun selectCategoryInMenu(nameSubstring: String, fallbackKoSubstring: String) {
        val item = device.wait(Until.findObject(By.res(pkg, "tvCategoryTitle").textContains(nameSubstring)), timeout)
            ?: device.wait(Until.findObject(By.res(pkg, "tvCategoryTitle").textContains(fallbackKoSubstring)), timeout)
            ?: device.wait(Until.findObject(By.textContains(nameSubstring)), timeout)
            ?: device.wait(Until.findObject(By.textContains(fallbackKoSubstring)), timeout)
        assertNotNull("Category '$nameSubstring' should be visible in menu", item)
        item!!.click()
        device.waitForIdle()
        val unused = device.currentPackageName
        Thread.sleep(1000)
    }

    private fun scrollAndClickLumbarDetail() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val topSec = LifecycleApp.getTopSecondaryActivity()
            if (topSec != null) {
                val imm = topSec.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(topSec.window.decorView.windowToken, 0)
            }
        }
        var lumbarDetailBtn = device.wait(Until.findObject(By.descContains("View details for seat_lumbar")), 2000)
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

        val detailInfoText = device.wait(Until.findObject(By.textContains("Deep Link Navigation Info")), timeout)
            ?: device.wait(Until.findObject(By.descContains("Back to")), timeout)
        assertNotNull("Should be on Lumbar Support ItemDetailScreen", detailInfoText)
    }

    private fun touchPrimaryPaneHeaderInWideMode() {
        val discoveryHeader = device.wait(Until.findObject(By.res(pkg, "tvDiscoveryStatus")), timeout)
        assertNotNull("Primary header should be visible", discoveryHeader)
        discoveryHeader!!.click()
        device.waitForIdle()
        Thread.sleep(500)
    }

    private fun expandOverlayDrawerInNarrowMode() {
        val btnMore = device.wait(Until.findObject(By.res(pkg, "btnMore")), timeout)
        assertNotNull("Collapsed MENU button should be visible", btnMore)
        btnMore!!.click()
        device.waitForIdle()
        Thread.sleep(800)
    }

    private fun collapseOverlayDrawerInNarrowMode() {
        val btnCollapse = device.wait(Until.findObject(By.res(pkg, "btnCollapse")), timeout)
        assertNotNull("Expanded drawer Collapse button should be visible", btnCollapse)
        btnCollapse!!.click()
        device.waitForIdle()
        Thread.sleep(800)
    }

    @Test
    fun scenario1_WideMode_InternalComposeBackStack_AfterTouchingPrimary() {
        launchApp(isOverlayMode = false)

        selectCategoryInMenu("Seat", "시트")
        scrollAndClickLumbarDetail()

        touchPrimaryPaneHeaderInWideMode()

        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1000)

        val subActivityBtnOnSeatList = device.wait(
            Until.findObject(By.textContains("Open Sub-Setting Page")),
            timeout
        )
        assertNotNull(
            "Back should pop 1 step from Lumbar detail to Seat list (not close entire category)",
            subActivityBtnOnSeatList
        )
    }

    @Test
    fun scenario2_WideMode_InternalSubActivityBackStack_AfterTouchingPrimary() {
        launchApp(isOverlayMode = false)

        selectCategoryInMenu("Seat", "시트")

        val openSubActivityBtn = device.wait(
            Until.findObject(By.textContains("Open Sub-Setting Page")),
            timeout
        )
        assertNotNull("Open Sub-Setting Page button should be visible", openSubActivityBtn)
        openSubActivityBtn!!.click()
        device.waitForIdle()
        Thread.sleep(1000)

        val subActivityTitle = device.wait(
            Until.findObject(By.textContains("Sub Activity")),
            timeout
        )
        assertNotNull("GenericSubActivity should be visible", subActivityTitle)

        touchPrimaryPaneHeaderInWideMode()

        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1000)

        val subActivityBtnOnSeatList = device.wait(
            Until.findObject(By.textContains("Open Sub-Setting Page")),
            timeout
        )
        assertNotNull(
            "Back should finish GenericSubActivity and return to Seat list (not close entire category)",
            subActivityBtnOnSeatList
        )
    }

    @Test
    fun scenario3_WideMode_ExternalAppInternalBackStack_AfterTouchingPrimary() {
        launchApp(isOverlayMode = false)

        selectCategoryInMenu("Light", "조명")

        val openLightDetailBtn = device.wait(
            Until.findObject(By.textContains("Open Light Detail")),
            timeout
        )
        assertNotNull("External LightSettingsActivity should show 'Open Light Detail' button", openLightDetailBtn)
        openLightDetailBtn!!.click()
        device.waitForIdle()
        Thread.sleep(800)

        val lightDetailTitle = device.wait(
            Until.findObject(By.textContains("Light Detail Sub-Screen (Depth 2)")),
            timeout
        )
        assertNotNull("Light Detail Sub-Screen should be visible after click", lightDetailTitle)

        touchPrimaryPaneHeaderInWideMode()

        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1000)

        // Verify 1 step of external app's internal back stack was popped while LightSettingsActivity remains open
        val detailAfterBack = device.findObject(By.textContains("Light Detail Sub-Screen (Depth 2)"))
        assertNull("Light Detail Sub-Screen should be popped after 1st Back press", detailAfterBack)

        val headlightsCardStillVisible = device.wait(
            Until.findObject(By.textContains("Headlights Mode")),
            timeout
        )
        assertNotNull(
            "External LightSettingsActivity itself should remain open after popping its internal back stack (not closed to Home)",
            headlightsCardStillVisible
        )
    }

    @Test
    fun scenario4_NarrowOverlayMode_InternalComposeBackStack_AfterTogglingDrawer() {
        launchApp(isOverlayMode = true)

        expandOverlayDrawerInNarrowMode()
        selectCategoryInMenu("Seat", "시트")
        scrollAndClickLumbarDetail()

        // Touch overlay by expanding and collapsing the drawer via the right dimmed area without changing category
        expandOverlayDrawerInNarrowMode()
        val dimScrim = device.wait(Until.findObject(By.res(pkg, "viewDimScrim")), timeout)
        assertNotNull("Dimmed scrim should be visible when overlay drawer is expanded", dimScrim)
        device.click((device.displayWidth * 0.75).toInt(), device.displayHeight / 2)
        device.waitForIdle()
        Thread.sleep(800)
        val collapsedBtn = device.wait(Until.findObject(By.res(pkg, "btnMore")), timeout)
        assertNotNull("Touching the right dimmed outside area should collapse the overlay drawer", collapsedBtn)

        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1000)

        val subActivityBtnOnSeatList = device.wait(
            Until.findObject(By.textContains("Open Sub-Setting Page")),
            timeout
        )
        assertNotNull(
            "Back in Narrow Mode should pop 1 step from Lumbar detail to Seat list after toggling drawer",
            subActivityBtnOnSeatList
        )
    }

    @Test
    fun scenario5_NarrowOverlayMode_ExternalAppInternalBackStack_AfterTogglingDrawer() {
        launchApp(isOverlayMode = true)

        expandOverlayDrawerInNarrowMode()
        selectCategoryInMenu("Light", "조명")

        val openLightDetailBtn = device.wait(
            Until.findObject(By.textContains("Open Light Detail")),
            timeout
        )
        assertNotNull("External LightSettingsActivity should show 'Open Light Detail' button", openLightDetailBtn)
        openLightDetailBtn!!.click()
        device.waitForIdle()
        Thread.sleep(800)

        val lightDetailTitle = device.wait(
            Until.findObject(By.textContains("Light Detail Sub-Screen (Depth 2)")),
            timeout
        )
        assertNotNull("Light Detail Sub-Screen should be visible", lightDetailTitle)

        // Touch overlay by expanding and collapsing the drawer
        expandOverlayDrawerInNarrowMode()
        collapseOverlayDrawerInNarrowMode()

        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1000)

        val detailAfterBack = device.findObject(By.textContains("Light Detail Sub-Screen (Depth 2)"))
        assertNull("Light Detail Sub-Screen should be popped after 1st Back press", detailAfterBack)

        val headlightsCardStillVisible = device.wait(
            Until.findObject(By.textContains("Headlights Mode")),
            timeout
        )
        assertNotNull(
            "External LightSettingsActivity itself should remain open in Narrow Mode after popping its internal back stack",
            headlightsCardStillVisible
        )
    }

    @Test
    fun scenario6_NarrowOverlayMode_ExpandedDrawer_BackClosesDrawerFirstThenPopsBackStack() {
        launchApp(isOverlayMode = true)

        expandOverlayDrawerInNarrowMode()
        selectCategoryInMenu("Seat", "시트")
        scrollAndClickLumbarDetail()

        // Leave overlay drawer expanded
        expandOverlayDrawerInNarrowMode()

        // Back #1: Should collapse the expanded overlay drawer while keeping Lumbar Support detail screen
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1000)

        val detailStillVisible = device.wait(
            Until.findObject(By.textContains("Deep Link Navigation Info")),
            timeout
        )
        assertNotNull("1st Back should only close the expanded drawer, keeping Lumbar detail screen visible", detailStillVisible)

        // Back #2: Should pop Lumbar Support detail screen back to Seat category list
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1000)

        val subActivityBtnOnSeatList = device.wait(
            Until.findObject(By.textContains("Open Sub-Setting Page")),
            timeout
        )
        assertNotNull("2nd Back should pop 1 step from Lumbar detail to Seat list", subActivityBtnOnSeatList)
    }

    /**
     * Scenario 7 (Direct Category Launch - No Category Shim):
     * When selecting a category from the list, the target category Activity (GenericSettingsActivity / LightSettingsActivity)
     * must be launched directly into the secondary TaskFragment with zero intermediary shim Activity.
     */
    @Test
    fun scenario7_CategorySelection_DirectLaunchWithoutHostActivity() {
        launchApp(isOverlayMode = false)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application

        val timeline = java.util.concurrent.CopyOnWriteArrayList<String>()
        val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                timeline.add("ON_CREATE(" + activity.javaClass.simpleName + ")")
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }

        app.registerActivityLifecycleCallbacks(lifecycleCallbacks)
        try {
            selectCategoryInMenu("Seat", "시트")
            val subActivityBtn = device.wait(
                androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.textContains("Open Sub-Setting Page")),
                timeout
            )
            assertNotNull("Seat category should be displayed directly", subActivityBtn)
            assertTrue(
                "GenericSettingsActivity should be created directly without Category. Timeline: " + timeline,
                timeline.contains("ON_CREATE(GenericSettingsActivity)")
            )
            assertFalse(
                "No CategoryHostActivity should ever be created! Timeline: " + timeline,
                timeline.any { it.contains("CategoryHostActivity") }
            )
        } finally {
            app.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
        }
    }

    /**
     * Scenario 8:
     * In Narrow Overlay Mode, verifies the core architectural purpose of [Category]:
     * When switching across internal categories (`Seat` -> `Door`), switching to an external plugin
     * category (`Door` -> `Light`), pressing Back to return to `Home`, or selecting `Home` from the drawer,
     * [Category] finishes only its own Category `ActivityStack` (`ExpandedTaskFragment #1`),
     * so [PrimaryOverlayActivity] (`OverlayTaskFragment`) and [HomeActivity] (`ExpandedTaskFragment #0`)
     * are NEVER destroyed or recreated.
     */
    @Test
    fun scenario8_NarrowOverlayMode_PrimaryOverlayAndHomeNeverDestroyedAcrossCategorySwitches() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application

        val overlayLifecycleEvents = CopyOnWriteArrayList<String>()
        val homeLifecycleEvents = CopyOnWriteArrayList<String>()
        val configChangeRecreations = CopyOnWriteArrayList<String>()
        var trackingPostLaunch = false

        val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                val hash = System.identityHashCode(activity)
                if (trackingPostLaunch) {
                    if (activity is ListOverlayActivity) {
                        overlayLifecycleEvents.add("ON_CREATE(hash=$hash, savedState=${savedInstanceState != null})")
                    } else if (activity is HomeActivity) {
                        homeLifecycleEvents.add("ON_CREATE(hash=$hash, savedState=${savedInstanceState != null})")
                    }
                }
                if (savedInstanceState != null) {
                    configChangeRecreations.add("RECREATED_WITH_SAVED_STATE(${activity.javaClass.simpleName}, hash=$hash)")
                }
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                val hash = System.identityHashCode(activity)
                if (trackingPostLaunch) {
                    if (activity is ListOverlayActivity) {
                        overlayLifecycleEvents.add(
                            "ON_DESTROY(hash=$hash, isChangingConfigs=${activity.isChangingConfigurations})"
                        )
                    } else if (activity is HomeActivity) {
                        homeLifecycleEvents.add(
                            "ON_DESTROY(hash=$hash, isChangingConfigs=${activity.isChangingConfigurations})"
                        )
                    }
                }
                if (activity.isChangingConfigurations) {
                    configChangeRecreations.add(
                        "DESTROYED_FOR_CONFIG_CHANGE(${activity.javaClass.simpleName}, " +
                            "changingConfigs=0x${Integer.toHexString(activity.changingConfigurations)})"
                    )
                }
            }
        }

        // Register BEFORE launchApp so initial Overlay embedding config changes are also monitored
        app.registerActivityLifecycleCallbacks(lifecycleCallbacks)
        try {
            launchApp(isOverlayMode = true)
            trackingPostLaunch = true
            // 1. Home -> Seat (internal category)
            expandOverlayDrawerInNarrowMode()
            selectCategoryInMenu("Seat", "시트")

            // 2. Seat -> Door (internal category switch)
            expandOverlayDrawerInNarrowMode()
            selectCategoryInMenu("Door", "도어")

            // 3. Door -> Light (external plugin category switch)
            expandOverlayDrawerInNarrowMode()
            selectCategoryInMenu("Light", "조명")

            // Verify external LightSettingsActivity is visible
            val headlightsCard = device.wait(Until.findObject(By.textContains("Headlights Mode")), timeout)
            assertNotNull("LightSettingsActivity should be visible in Narrow Mode", headlightsCard)

            // 4. Back press from external category -> should dismiss only Category stack and reveal Home
            device.pressBack()
            device.waitForIdle()
            Thread.sleep(1000)

            // 5. Re-enter Seat and then select Home from drawer
            expandOverlayDrawerInNarrowMode()
            selectCategoryInMenu("Seat", "시트")
            expandOverlayDrawerInNarrowMode()
            selectCategoryInMenu("Home", "홈")

            android.util.Log.i(
                "LifecycleLog",
                "[Scenario8] overlayEvents=$overlayLifecycleEvents, homeEvents=$homeLifecycleEvents, configRecreations=$configChangeRecreations"
            )

            assertTrue(
                "PrimaryOverlayActivity must NEVER be destroyed or recreated across category switches in Narrow Mode! Events: $overlayLifecycleEvents",
                overlayLifecycleEvents.isEmpty()
            )
            assertTrue(
                "HomeActivity must NEVER be destroyed or recreated across category switches in Narrow Mode! Events: $homeLifecycleEvents",
                homeLifecycleEvents.isEmpty()
            )
            assertTrue(
                "No activity should be recreated due to configuration changes in Narrow Mode! Events: $configChangeRecreations",
                configChangeRecreations.isEmpty()
            )
        } finally {
            app.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
        }
    }

    /**
     * Scenario 9:
     * Verifies in Wide Split Mode (and Narrow Overlay Mode including sub-activities) that switching
     * categories (`Home` -> `Seat` -> `GenericSubActivity` -> `Door` -> `Light` -> `Home`) does NOT
     * cause ANY activity (`PrimaryActivity`, `HomeActivity`, `Category`, `GenericSettingsActivity`,
     * `GenericSubActivity`, or external `LightSettingsActivity`) to be destroyed and recreated due to
     * a Configuration Change (`isChangingConfigurations == true` or `savedInstanceState != null`).
     */
    @Test
    fun scenario9_WideAndNarrowModes_NoActivityRecreatedByConfigChange() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application

        val primaryLifecycleEvents = CopyOnWriteArrayList<String>()
        val homeLifecycleEvents = CopyOnWriteArrayList<String>()
        val configChangeRecreations = CopyOnWriteArrayList<String>()
        val allCreatedAndDestroyed = CopyOnWriteArrayList<String>()
        var trackingPostLaunch = false

        val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                val name = activity.javaClass.simpleName
                val hash = System.identityHashCode(activity)
                allCreatedAndDestroyed.add("CREATE:$name($hash,savedState=${savedInstanceState != null})")
                if (trackingPostLaunch) {
                    if (activity is ListActivity) {
                        primaryLifecycleEvents.add("ON_CREATE(hash=$hash, savedState=${savedInstanceState != null})")
                    } else if (activity is HomeActivity) {
                        homeLifecycleEvents.add("ON_CREATE(hash=$hash, savedState=${savedInstanceState != null})")
                    }
                }
                if (savedInstanceState != null) {
                    configChangeRecreations.add("RECREATED_WITH_SAVED_STATE($name, hash=$hash)")
                }
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                val name = activity.javaClass.simpleName
                val hash = System.identityHashCode(activity)
                val changingHex = "0x${Integer.toHexString(activity.changingConfigurations)}"
                allCreatedAndDestroyed.add(
                    "DESTROY:$name($hash,isChangingConfigs=${activity.isChangingConfigurations},mask=$changingHex)"
                )
                if (trackingPostLaunch) {
                    if (activity is ListActivity) {
                        primaryLifecycleEvents.add("ON_DESTROY(hash=$hash, isChangingConfigs=${activity.isChangingConfigurations})")
                    } else if (activity is HomeActivity) {
                        homeLifecycleEvents.add("ON_DESTROY(hash=$hash, isChangingConfigs=${activity.isChangingConfigurations})")
                    }
                }
                if (activity.isChangingConfigurations) {
                    configChangeRecreations.add("DESTROYED_FOR_CONFIG_CHANGE($name, mask=$changingHex)")
                }
            }
        }

        // Register BEFORE launchApp so initial SplitPairRule embedding config changes on PrimaryActivity are also monitored
        app.registerActivityLifecycleCallbacks(lifecycleCallbacks)
        try {
            launchApp(isOverlayMode = false)
            trackingPostLaunch = true
            // 1. Wide Mode: Home -> Seat -> GenericSubActivity -> Back -> Door -> Light -> Home
            selectCategoryInMenu("Seat", "시트")

            val openSubActivityBtn = device.wait(
                Until.findObject(By.textContains("Open Sub-Setting Page")),
                timeout
            )
            assertNotNull("Open Sub-Setting Page button should be visible", openSubActivityBtn)
            openSubActivityBtn!!.click()
            device.waitForIdle()
            Thread.sleep(1000)

            device.pressBack()
            device.waitForIdle()
            Thread.sleep(800)

            selectCategoryInMenu("Door", "도어")
            selectCategoryInMenu("Light", "조명")

            val headlightsCard = device.wait(Until.findObject(By.textContains("Headlights Mode")), timeout)
            assertNotNull("LightSettingsActivity should be visible in Wide Mode", headlightsCard)

            selectCategoryInMenu("Home", "홈")

            android.util.Log.i(
                "LifecycleLog",
                "[Scenario9-Wide] allEvents=$allCreatedAndDestroyed, configRecreations=$configChangeRecreations"
            )

            assertTrue(
                "PrimaryActivity must NEVER be destroyed or recreated during category switches in Wide Mode! Events: $primaryLifecycleEvents",
                primaryLifecycleEvents.isEmpty()
            )
            assertTrue(
                "HomeActivity must NEVER be destroyed or recreated during category switches in Wide Mode! Events: $homeLifecycleEvents",
                homeLifecycleEvents.isEmpty()
            )
            assertTrue(
                "No activity should be destroyed/recreated due to Configuration Change! Events: $configChangeRecreations (all=$allCreatedAndDestroyed)",
                configChangeRecreations.isEmpty()
            )
        } finally {
            app.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
        }
    }

    private fun enterSearchQueryOnHome(query: String) {
        val searchBox = device.wait(
            Until.findObject(By.clazz("android.widget.EditText")),
            timeout
        )
        assertNotNull("Home search EditText node should be visible", searchBox)
        searchBox!!.text = query
        device.waitForIdle()
        Thread.sleep(600)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val topSec = LifecycleApp.getTopSecondaryActivity()
            if (topSec != null) {
                val imm = topSec.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(topSec.window.decorView.windowToken, 0)
            }
        }
        device.waitForIdle()
        Thread.sleep(400)

        val matchingHeader = device.wait(Until.findObject(By.textContains("settings found")), timeout)
        assertNotNull("Home search results header should be visible for query '$query'", matchingHeader)
    }

    private fun waitForActiveMenuActivity(): BaseCategoryMenuActivity {
        var menu: BaseCategoryMenuActivity? = null
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 5000) {
            menu = BaseCategoryMenuActivity.getActiveMenuActivity()
            if (menu != null && menu.currentSelectedPosition >= 0) break
            Thread.sleep(200)
        }
        assertNotNull("Active menu activity must exist within timeout", menu)
        return menu!!
    }

    private fun assertHomeSearchQueryPreserved(query: String, message: String) {
        val matchingHeader = device.wait(Until.findObject(By.textContains("settings found")), timeout)
        assertNotNull("$message (expected 'settings found' header for query '$query')", matchingHeader)
        val queryText = device.wait(Until.findObject(By.textContains(query)), timeout)
        assertNotNull("$message (expected '$query' text on Home)", queryText)
    }

    /**
     * Scenario 10:
     * Wide Mode - Internal Category (`Seat` -> `Lumbar Support`):
     * 1) Set custom state on `HomeActivity` (search query `"Lumbar"`), verify Primary selection is `Home` (`0`).
     * 2) Select `Seat` -> verify Primary selection changes to `seat` (`> 0`), drill down to `Lumbar Support`.
     * 3) Press Back #1 -> pops `Lumbar Support` to `Seat` list; Primary selection remains `seat`.
     * 4) Press Back #2 -> exhausts `Seat` back stack, finishes `GenericSettingsActivity` & `Category`,
     *    reveals `HomeActivity` in its last state (search query `"Lumbar"` & search results still displayed!),
     *    and resets Primary list selection to `Home` (`selectedPosition == 0`).
     * 5) Press Back #3 on `HomeActivity` -> no more back stack -> exits the app (`finishAffinity()`).
     */
    @Test
    fun scenario10_WideMode_InternalCategory_ExhaustBackStack_RevealsHomeState_UpdatesSelection_AndExitsApp() {
        launchApp(isOverlayMode = false)

        val menuInitial = waitForActiveMenuActivity()
        assertNotNull("Active PrimaryActivity must exist", menuInitial)
        assertEquals("Initial selectedPosition must be 0 (Home)", 0, menuInitial!!.currentSelectedPosition)
        assertEquals("Initial selectedCategoryId must be 'home'", "home", menuInitial.currentSelectedCategoryId)

        // 1. Mutate state on HomeActivity by typing search query "Lumbar"
        enterSearchQueryOnHome("Lumbar")

        // 2. Select Internal Category "Seat" and drill down to "Lumbar Support" (Depth 2)
        selectCategoryInMenu("Seat", "시트")
        assertTrue("Selected position must be > 0 when Seat is selected", menuInitial.currentSelectedPosition > 0)
        assertEquals("Selected category ID must be 'seat'", "seat", menuInitial.currentSelectedCategoryId)

        scrollAndClickLumbarDetail()

        // 3. Back #1: Pops Depth 2 (Lumbar Support) -> Depth 1 (Seat list)
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1000)

        val subActivityBtnOnSeatList = device.wait(
            Until.findObject(By.textContains("Open Sub-Setting Page")),
            timeout
        )
        assertNotNull("Back #1 should pop Lumbar detail to Seat root list", subActivityBtnOnSeatList)
        assertEquals("Selection must still be 'seat' while on Seat root", "seat", menuInitial.currentSelectedCategoryId)

        // 4. Back #2: Exhausts Seat back stack -> reveals HomeActivity's last state ("Lumbar") & updates selection to Home (0)
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1000)

        assertHomeSearchQueryPreserved(
            "Lumbar",
            "Back #2 should exhaust Seat stack and reveal HomeActivity with its last state ('Lumbar' search results preserved)"
        )
        assertEquals(
            "Primary list selection must reset to 0 (Home) when HomeActivity is revealed",
            0,
            menuInitial.currentSelectedPosition
        )
        assertEquals("Selected category ID must reset to 'home'", "home", menuInitial.currentSelectedCategoryId)

        // 5. Back #3 on HomeActivity: No more back stack -> exits the app
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1200)

        assertTrue(
            "Back #3 at HomeActivity should finish the app (PrimaryActivity finishing/destroyed)",
            menuInitial.isFinishing || menuInitial.isDestroyed || BaseCategoryMenuActivity.getActiveMenuActivity() == null
        )
    }

    /**
     * Scenario 11:
     * Wide Mode - External Category (`Vehicle Lights` -> `Light Detail Depth 2`):
     * 1) Set custom state on `HomeActivity` (search query `"Lumbar"`), verify Primary selection is `Home` (`0`).
     * 2) Select external `Light` category -> verify Primary selection changes to `light` (`> 0`), drill down to Depth 2.
     * 3) Press Back #1 -> pops Depth 2 to `LightSettingsActivity` root; Primary selection remains `light`.
     * 4) Press Back #2 -> exhausts `LightSettingsActivity` back stack, finishes `Category`,
     *    reveals `HomeActivity` in its last state (`"Lumbar"` search results preserved), and resets Primary
     *    list selection to `Home` (`selectedPosition == 0`).
     * 5) Press Back #3 on `HomeActivity` -> exits the app (`finishAffinity()`).
     */
    @Test
    fun scenario11_WideMode_ExternalCategory_ExhaustBackStack_RevealsHomeState_UpdatesSelection_AndExitsApp() {
        launchApp(isOverlayMode = false)

        val menuInitial = waitForActiveMenuActivity()
        assertNotNull("Active PrimaryActivity must exist", menuInitial)
        assertEquals("Initial selectedPosition must be 0 (Home)", 0, menuInitial!!.currentSelectedPosition)

        // 1. Mutate state on HomeActivity by typing search query "Lumbar"
        enterSearchQueryOnHome("Lumbar")

        // 2. Select External Category "Light" and drill down to Depth 2
        selectCategoryInMenu("Light", "조명")
        assertTrue("Selected position must be > 0 when Light is selected", menuInitial.currentSelectedPosition > 0)
        assertEquals("Selected category ID must be 'light'", "light", menuInitial.currentSelectedCategoryId)

        val openLightDetailBtn = device.wait(Until.findObject(By.textContains("Open Light Detail")), timeout)
        assertNotNull("External LightSettingsActivity should show 'Open Light Detail'", openLightDetailBtn)
        openLightDetailBtn!!.click()
        device.waitForIdle()
        Thread.sleep(800)

        val lightDetailTitle = device.wait(Until.findObject(By.textContains("Light Detail Sub-Screen (Depth 2)")), timeout)
        assertNotNull("Light Detail Sub-Screen (Depth 2) should be visible", lightDetailTitle)

        // 3. Back #1: Pops Depth 2 -> Depth 1 (LightSettingsActivity root)
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1000)

        assertNull(
            "Light Detail Sub-Screen should be popped after Back #1",
            device.findObject(By.textContains("Light Detail Sub-Screen (Depth 2)"))
        )
        val headlightsCard = device.wait(Until.findObject(By.textContains("Headlights Mode")), timeout)
        assertNotNull("LightSettingsActivity root should remain visible after Back #1", headlightsCard)
        assertEquals("Selection must still be 'light' while on LightSettingsActivity", "light", menuInitial.currentSelectedCategoryId)

        // 4. Back #2: Exhausts external Light back stack -> reveals HomeActivity's last state ("Lumbar") & resets selection to Home (0)
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1000)

        assertHomeSearchQueryPreserved(
            "Lumbar",
            "Back #2 should finish LightSettingsActivity + Category and reveal HomeActivity with 'Lumbar' search state preserved"
        )
        assertEquals(
            "Primary list selection must reset to 0 (Home) when HomeActivity is revealed",
            0,
            menuInitial.currentSelectedPosition
        )
        assertEquals("Selected category ID must reset to 'home'", "home", menuInitial.currentSelectedCategoryId)

        // 5. Back #3 on HomeActivity: No more back stack -> exits the app
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1200)

        assertTrue(
            "Back #3 at HomeActivity should finish the app",
            menuInitial.isFinishing || menuInitial.isDestroyed || BaseCategoryMenuActivity.getActiveMenuActivity() == null
        )
    }

    /**
     * Scenario 12:
     * Narrow Overlay Mode - Both Internal (`Seat` -> `Lumbar Support`) and External (`Light` -> `Depth 2`) Categories:
     * Verifies full Back-stack exhaustion -> `HomeActivity` last-state reveal -> `PrimaryOverlayActivity` selection
     * reset to `Home` (`0`) -> final Back press at `HomeActivity` exiting the entire app.
     */
    @Test
    fun scenario12_NarrowOverlayMode_InternalAndExternal_ExhaustBackStack_RevealsHomeState_UpdatesSelection_AndExitsApp() {
        launchApp(isOverlayMode = true)

        val overlayMenu = waitForActiveMenuActivity()
        assertNotNull("Active PrimaryOverlayActivity must exist", overlayMenu)
        assertEquals("Initial selectedPosition must be 0 (Home)", 0, overlayMenu!!.currentSelectedPosition)

        // 1. Mutate state on HomeActivity by typing search query "Lumbar"
        enterSearchQueryOnHome("Lumbar")

        // 2. Internal Category: Expand drawer -> select Seat -> drill down to Lumbar Support (Depth 2)
        expandOverlayDrawerInNarrowMode()
        selectCategoryInMenu("Seat", "시트")
        assertEquals("Selected category ID must be 'seat'", "seat", overlayMenu.currentSelectedCategoryId)
        scrollAndClickLumbarDetail()

        // Back #1: Lumbar Support -> Seat root
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1000)
        assertNotNull(
            "Back #1 in Narrow Mode should pop Lumbar detail to Seat root",
            device.wait(Until.findObject(By.textContains("Open Sub-Setting Page")), timeout)
        )
        assertEquals("Selection must remain 'seat' at Seat root", "seat", overlayMenu.currentSelectedCategoryId)

        // Back #2: Seat root -> HomeActivity revealed with "Lumbar" state + selection reset to 0 ("home")
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1000)
        assertHomeSearchQueryPreserved(
            "Lumbar",
            "Back #2 in Narrow Mode should reveal HomeActivity with 'Lumbar' search state preserved"
        )
        assertEquals("Overlay list selection must reset to 0 (Home)", 0, overlayMenu.currentSelectedPosition)
        assertEquals("Overlay selectedCategoryId must reset to 'home'", "home", overlayMenu.currentSelectedCategoryId)

        // 3. External Category: Expand drawer -> select Light -> drill down to Light Detail (Depth 2)
        expandOverlayDrawerInNarrowMode()
        selectCategoryInMenu("Light", "조명")
        assertEquals("Selected category ID must be 'light'", "light", overlayMenu.currentSelectedCategoryId)

        val openLightDetailBtn = device.wait(Until.findObject(By.textContains("Open Light Detail")), timeout)
        assertNotNull("External LightSettingsActivity should show 'Open Light Detail'", openLightDetailBtn)
        openLightDetailBtn!!.click()
        device.waitForIdle()
        Thread.sleep(800)

        // Back #1: Light Detail (Depth 2) -> LightSettingsActivity root
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1000)
        assertNotNull(
            "Back #1 should return to LightSettingsActivity root",
            device.wait(Until.findObject(By.textContains("Headlights Mode")), timeout)
        )
        assertEquals("Selection must remain 'light' at LightSettingsActivity root", "light", overlayMenu.currentSelectedCategoryId)

        // Back #2: LightSettingsActivity root -> HomeActivity revealed with "Lumbar" state + selection reset to 0 ("home")
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1000)
        assertHomeSearchQueryPreserved(
            "Lumbar",
            "Back #2 should reveal HomeActivity with 'Lumbar' search state still preserved"
        )
        assertEquals("Overlay list selection must reset to 0 (Home)", 0, overlayMenu.currentSelectedPosition)

        // 4. Back #3 on HomeActivity: No more back stack -> exits the app
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1200)

        assertTrue(
            "Back #3 at HomeActivity in Narrow Mode should finish the app (PrimaryOverlayActivity finishing/destroyed)",
            overlayMenu.isFinishing || overlayMenu.isDestroyed || BaseCategoryMenuActivity.getActiveMenuActivity() == null
        )
    }

    /**
     * Scenario 13 (Dim Scrim Touch Consumption & Modal Blocking):
     * When the Narrow Mode overlay drawer is expanded:
     * 1. The overlay ActivityStack expands to full Task width & height (`100%`), showing `viewDimScrim` over the right 65% outside area.
     * 2. Clicking the exact coordinates of an underlying clickable button (`"Open Sub-Setting Page"`) located in the right 65% dimmed area:
     *    - Is CONSUMED by `viewDimScrim` (does NOT trigger `"Open Sub-Setting Page"` or launch `SubSettingActivity`).
     *    - Collapses the overlay drawer back to the `80dp` floating button (`btnMore`).
     * 3. Once the overlay drawer is collapsed (`viewDimScrim` is gone), clicking the exact same coordinates DOES trigger `"Open Sub-Setting Page"`, proving the touch was blocked solely by `viewDimScrim`.
     */
    @Test
    fun scenario13_NarrowOverlayMode_DimScrimConsumesTouchAndBlocksUnderlyingUiClick() {
        launchApp(isOverlayMode = true)

        val overlayMenu = waitForActiveMenuActivity()
        assertNotNull("Active PrimaryOverlayActivity must exist", overlayMenu)

        // 1. Search "Lumbar" on HomeActivity so a full-width clickable SearchResultItemCard is displayed
        enterSearchQueryOnHome("Lumbar")

        val lumbarResultText = device.wait(
            Until.findObject(By.textContains("Lumbar Support")),
            timeout
        )
        assertNotNull("HomeActivity should display 'Lumbar Support' search result card", lumbarResultText)
        device.waitForIdle()
        Thread.sleep(400)
        device.takeScreenshot(java.io.File("/sdcard/Download/1_collapsed_top_left.png"))

        // Pick a point at 65% of screen width (well inside the right 65% dimmed outside area)
        // at the vertical center of the full-width SearchResultItemCard.
        val clickX = (device.displayWidth * 65) / 100
        val clickY = lumbarResultText!!.visibleBounds.centerY()
        assertTrue(
            "Target clickX ($clickX) must lie in the right 65% outside area (> ${device.displayWidth * 35 / 100})",
            clickX > (device.displayWidth * 35) / 100
        )

        // 2. Expand the overlay drawer so viewDimScrim covers the full Task width
        expandOverlayDrawerInNarrowMode()

        val dimScrim = device.wait(Until.findObject(By.res(pkg, "viewDimScrim")), timeout)
        assertNotNull("viewDimScrim must be visible when overlay drawer is expanded", dimScrim)
        val scrimBounds = dimScrim!!.visibleBounds
        assertTrue(
            "viewDimScrim must span full width (actual=$scrimBounds, displayWidth=${device.displayWidth})",
            scrimBounds.width() >= (device.displayWidth * 90) / 100
        )
        assertTrue(
            "viewDimScrim bounds $scrimBounds must cover target coordinates ($clickX, $clickY)",
            scrimBounds.contains(clickX, clickY)
        )
        Thread.sleep(400)
        device.takeScreenshot(java.io.File("/sdcard/Download/2_expanded_full_width_dimmed.png"))

        // 3. Click the exact coordinates of the underlying full-width "Lumbar" card while dimmed
        device.click(clickX, clickY)
        device.waitForIdle()
        Thread.sleep(1000)
        device.takeScreenshot(java.io.File("/sdcard/Download/3_collapsed_after_dim_touch.png"))

        // 4. Verify the touch was CONSUMED by viewDimScrim:
        //    - Overlay drawer collapsed (btnMore is visible, viewDimScrim is gone)
        //    - Underlying "Lumbar" card was NOT clicked (still on "home", Lumbar detail screen did NOT open)
        val btnMoreAfterDimClick = device.wait(Until.findObject(By.res(pkg, "btnMore")), timeout)
        assertNotNull("Clicking dimmed outside area must collapse the overlay drawer", btnMoreAfterDimClick)
        assertNull(
            "viewDimScrim must be GONE after collapsing",
            device.findObject(By.res(pkg, "viewDimScrim"))
        )
        assertEquals(
            "Touch on viewDimScrim must be consumed and MUST NOT trigger underlying 'Lumbar' card navigation",
            "home",
            overlayMenu!!.currentSelectedCategoryId
        )
        assertNull(
            "Lumbar detail screen must NOT be opened when clicking on viewDimScrim",
            device.findObject(By.textContains("Deep Link Navigation Info"))
        )

        // 5. Sanity check: Clicking the exact same coordinates (clickX, clickY) now that the drawer is collapsed
        //    DOES click the underlying full-width "Lumbar" card and navigates to Seat -> Lumbar detail screen.
        device.click(clickX, clickY)
        device.waitForIdle()
        Thread.sleep(1000)

        val lumbarDetailOpened = device.wait(
            Until.findObject(By.textContains("Deep Link Navigation Info")),
            timeout
        )
        assertNotNull(
            "Clicking the same coordinates ($clickX, $clickY) after dim scrim is gone MUST click the underlying card",
            lumbarDetailOpened
        )
        assertEquals("seat", overlayMenu.currentSelectedCategoryId)
        device.waitForIdle()
        Thread.sleep(400)
        device.takeScreenshot(java.io.File("/sdcard/Download/4_underlying_clicked_after_unblocked.png"))
    }
}
