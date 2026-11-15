package com.example.feature.home

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeNavigationBridgeTest {

    @Before
    @After
    fun resetBridge() {
        HomeNavigationBridge.onHomeRevealed = null
        HomeNavigationBridge.onCategorySelected = null
    }

    @Test
    fun `notifyCategorySelected invokes registered listener with categoryId`() {
        var selectedCategory: String? = null
        HomeNavigationBridge.onCategorySelected = { categoryId ->
            selectedCategory = categoryId
        }

        HomeNavigationBridge.notifyCategorySelected("seat")
        assertEquals("seat", selectedCategory)

        HomeNavigationBridge.notifyCategorySelected("light")
        assertEquals("light", selectedCategory)
    }

    @Test
    fun `notifyHomeRevealed invokes registered listener`() {
        var callbackCount = 0
        HomeNavigationBridge.onHomeRevealed = {
            callbackCount++
        }

        HomeNavigationBridge.notifyHomeRevealed()
        assertEquals(1, callbackCount)

        HomeNavigationBridge.notifyHomeRevealed()
        assertEquals(2, callbackCount)
    }

    @Test
    fun `isLaunchingTarget flag prevents false reveal notification during category switch`() {
        var homeRevealedCount = 0
        HomeNavigationBridge.onHomeRevealed = {
            homeRevealedCount++
        }

        var isLaunchingTarget = false

        fun simulateOnResume() {
            if (isLaunchingTarget) {
                // Target is launching on top -> do NOT notify
                return
            }
            HomeNavigationBridge.notifyHomeRevealed()
        }

        fun simulateOnPause() {
            isLaunchingTarget = false
        }

        // Case 1: Switching from Seats to Doors
        // HomeActivity receives onNewIntent with target Doors
        isLaunchingTarget = true
        // HomeActivity passes through onResume during trampoline
        simulateOnResume()
        assertEquals(0, homeRevealedCount) // MUST NOT notify

        // Doors opens, HomeActivity pauses
        simulateOnPause()
        assertFalse(isLaunchingTarget)

        // Case 2: User presses Back on Doors -> Doors finishes -> HomeActivity resumes
        simulateOnResume()
        assertEquals(1, homeRevealedCount) // MUST notify!
    }

    @Test
    fun `CategoryResultTracker notifies only on genuine return regardless of CLEAR_TOP callback order or dropped results`() {
        val tracker = CategoryResultTracker()

        // 1. Launch Doors from Home (no active category before)
        tracker.onPrepareSwitch()
        val genDoors = tracker.nextLaunchGen()
        assertTrue(tracker.isCategoryRunning)
        assertEquals(genDoors, tracker.activeLaunchGen)

        // 2a. Switch Doors -> Seats when onActivityResult(Doors) arrives BEFORE onNewIntent(Seats) (Early Arrival)
        tracker.onPrepareSwitch()
        assertFalse(tracker.isCategoryRunning)
        assertFalse(tracker.onResultDelivered(genDoors))
        val genSeats = tracker.nextLaunchGen()
        assertTrue(tracker.isCategoryRunning)

        // 2b. Switch Seats -> Light when onNewIntent(Light) arrives BEFORE onActivityResult(Seats) (Late Arrival)
        tracker.onPrepareSwitch()
        val genLight = tracker.nextLaunchGen()
        assertTrue(tracker.isCategoryRunning)
        assertEquals(genLight, tracker.activeLaunchGen)
        assertFalse(tracker.onResultDelivered(genSeats))
        assertTrue(tracker.isCategoryRunning)

        // 2c. Switch Light -> Sound where Light's result is DROPPED (never arrives at all)
        tracker.onPrepareSwitch()
        val genSound = tracker.nextLaunchGen()
        assertTrue(tracker.isCategoryRunning)

        // 3. User presses Back on Sound -> genuine Home reveal still works without counter drift!
        assertTrue(tracker.onResultDelivered(genSound))
        assertFalse(tracker.isCategoryRunning)
        // Duplicate delivery of the same generation is also ignored
        assertFalse(tracker.onResultDelivered(genSound))
    }

    @Test
    fun `ActivityResultRegistry with generation keys cleans up completed generations and restores across process death`() {
        val nextRc = java.util.concurrent.atomic.AtomicInteger(100)
        val keyToRc = mutableMapOf<String, Int>()
        val rcToKey = mutableMapOf<Int, String>()
        val launchedKeys = mutableSetOf<String>()
        val pendingResults = mutableMapOf<String, Int>()
        val pendingUnregisters = mutableListOf<() -> Unit>()

        fun drainPendingUnregisters() {
            val copy = pendingUnregisters.toList()
            pendingUnregisters.clear()
            copy.forEach { it.invoke() }
        }

        // Mirrors androidx.activity.result.ActivityResultRegistry's exact bytecode order:
        // doDispatch() calls callback.onActivityResult() FIRST, and removes `key` from
        // `mLaunchedKeys` AFTER `onActivityResult()` returns!
        class SimulatedRegistry {
            val callbacks = mutableMapOf<String, (Int) -> Unit>()

            fun register(key: String, callback: (Int) -> Unit): () -> Unit {
                keyToRc.getOrPut(key) {
                    nextRc.getAndIncrement().also { rcToKey[it] = key }
                }
                callbacks[key] = callback
                pendingResults.remove(key)?.let { pendingResultCode ->
                    launchedKeys.remove(key)
                    callback(pendingResultCode)
                }
                return {
                    if (!launchedKeys.contains(key)) {
                        val removedRc = keyToRc.remove(key)
                        if (removedRc != null) rcToKey.remove(removedRc)
                    }
                    callbacks.remove(key)
                    pendingResults.remove(key)
                }
            }

            fun launch(key: String): Int {
                val rc = keyToRc[key] ?: error("Unregistered key $key")
                launchedKeys.add(key)
                return rc
            }

            fun dispatchResult(rc: Int, resultCode: Int = 0) {
                val key = rcToKey[rc] ?: return
                val cb = callbacks[key]
                if (cb != null && launchedKeys.contains(key)) {
                    cb(resultCode)
                    // Bytecode offset 57 in ActivityResultRegistry.doDispatch:
                    // mLaunchedKeys.remove(key) happens AFTER callback returns!
                    launchedKeys.remove(key)
                } else {
                    pendingResults[key] = resultCode
                }
            }
        }

        val registry = SimulatedRegistry()
        val tracker = CategoryResultTracker()
        var homeRevealCount = 0

        fun launchCategory(): Int {
            drainPendingUnregisters()
            val gen = tracker.nextLaunchGen()
            val key = CategoryResultTracker.keyOf(gen)
            lateinit var unregister: () -> Unit
            unregister = registry.register(key) {
                pendingUnregisters.add(unregister)
                if (tracker.onResultDelivered(gen)) {
                    homeRevealCount++
                }
            }
            return registry.launch(key)
        }

        // Perform 20 rapid switches (alternating early and late result arrival)
        var prevRc = launchCategory()
        for (i in 1..20) {
            drainPendingUnregisters()
            tracker.onPrepareSwitch()
            if (i % 2 == 0) {
                // Early arrival: old category finishes before new category launches
                registry.dispatchResult(prevRc)
                drainPendingUnregisters()
                prevRc = launchCategory()
            } else {
                // Late arrival: new category launches before old category finishes
                val newRc = launchCategory()
                registry.dispatchResult(prevRc)
                drainPendingUnregisters()
                prevRc = newRc
            }
        }

        // Only the 1 currently active category generation remains in the registry!
        assertEquals(0, homeRevealCount)
        assertEquals(1, keyToRc.size)
        assertEquals(1, rcToKey.size)
        assertEquals(1, launchedKeys.size)
        assertEquals(0, pendingResults.size)

        // Simulate Process Death while the last category (prevRc) is active on top:
        val savedCurrentGen = tracker.currentGen
        val savedActiveGen = tracker.activeLaunchGen
        registry.callbacks.clear() // Process died -> in-memory callbacks are gone

        // Restore in new process onCreate(savedInstanceState):
        val restoredTracker = CategoryResultTracker(
            currentGen = savedCurrentGen,
            activeLaunchGen = savedActiveGen
        )
        assertTrue(restoredTracker.isCategoryRunning)
        val restoredKey = CategoryResultTracker.keyOf(restoredTracker.activeLaunchGen)
        lateinit var restoredUnregister: () -> Unit
        restoredUnregister = registry.register(restoredKey) {
            pendingUnregisters.add(restoredUnregister)
            if (restoredTracker.onResultDelivered(restoredTracker.activeLaunchGen)) {
                homeRevealCount++
            }
        }

        // User presses Back on the active category after Process Death restoration:
        registry.dispatchResult(prevRc)
        drainPendingUnregisters()
        assertEquals(1, homeRevealCount)
        assertFalse(restoredTracker.isCategoryRunning)
        assertEquals(0, keyToRc.size)
        assertEquals(0, pendingResults.size)
    }
}
