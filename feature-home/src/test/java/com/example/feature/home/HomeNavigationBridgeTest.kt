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
}
