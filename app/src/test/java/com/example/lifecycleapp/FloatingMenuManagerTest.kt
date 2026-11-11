package com.example.lifecycleapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Option A Floating Edge Handle visibility state transitions.
 */
class FloatingMenuManagerTest {

    @Test
    fun `computeShouldShow is true only when attached, not suppressed, and app task visible`() {
        assertTrue(FloatingMenuManager.computeShouldShow(attached = true, suppressed = false, appForeground = true))

        assertFalse(FloatingMenuManager.computeShouldShow(attached = false, suppressed = false, appForeground = true))
        assertFalse(FloatingMenuManager.computeShouldShow(attached = true, suppressed = true, appForeground = true))
        assertFalse(FloatingMenuManager.computeShouldShow(attached = true, suppressed = false, appForeground = false))
        assertFalse(FloatingMenuManager.computeShouldShow(attached = true, suppressed = true, appForeground = false))
        assertFalse(FloatingMenuManager.computeShouldShow(attached = false, suppressed = true, appForeground = false))
    }

    @Test
    fun `external plugin navigation keeps app task visible and suppresses only while drawer expanded`() {
        val attached = true
        var suppressed = false
        var appTaskVisible = true

        // 1. Narrow Mode Home / Door category: handle visible
        assertTrue(FloatingMenuManager.computeShouldShow(attached, suppressed, appTaskVisible))

        // 2. Navigate to external LightSettingsActivity (same Task via taskAffinity=""): task remains visible
        appTaskVisible = true
        assertTrue(FloatingMenuManager.computeShouldShow(attached, suppressed, appTaskVisible))

        // 3. Hardware Home / other app while on external LightSettingsActivity: event-driven visibility -> false
        appTaskVisible = false
        assertFalse(FloatingMenuManager.computeShouldShow(attached, suppressed, appTaskVisible))

        // 4. Return to app task on external LightSettingsActivity: visibility -> true
        appTaskVisible = true
        assertTrue(FloatingMenuManager.computeShouldShow(attached, suppressed, appTaskVisible))

        // 5. Open ListWithTranslucentActivity drawer: suppressed -> false
        suppressed = true
        assertFalse(FloatingMenuManager.computeShouldShow(attached, suppressed, appTaskVisible))

        // 6. Close drawer: restored -> true
        suppressed = false
        assertTrue(FloatingMenuManager.computeShouldShow(attached, suppressed, appTaskVisible))
    }

    @Test
    fun `isTaskForegroundActive hides during Quickstep Recents live tile and background`() {
        // Active foreground (internal or external plugin Activity): visible + focused
        assertTrue(
            FloatingMenuManager.isTaskForegroundActive(
                taskVisible = true,
                taskFocused = true,
                isHomeUidTop = false,
                hasTaskStackListener = false
            )
        )

        // Quickstep Recents live tile on Android 14-17: task stays isVisible=true
        // (and on Android 17 may retain isFocused=true) while Home Launcher is TOP (`isHomeUidTop=true`)
        assertFalse(
            FloatingMenuManager.isTaskForegroundActive(
                taskVisible = true,
                taskFocused = false,
                isHomeUidTop = true,
                hasTaskStackListener = false
            )
        )
        assertFalse(
            FloatingMenuManager.isTaskForegroundActive(
                taskVisible = true,
                taskFocused = true,
                isHomeUidTop = true,
                hasTaskStackListener = false
            )
        )

        // Exiting Recents back to external plugin: Home leaves TOP, task regains focus
        assertTrue(
            FloatingMenuManager.isTaskForegroundActive(
                taskVisible = true,
                taskFocused = true,
                isHomeUidTop = false,
                hasTaskStackListener = false
            )
        )

        // Home key or fullscreen third-party app (Settings): task not visible
        assertFalse(
            FloatingMenuManager.isTaskForegroundActive(
                taskVisible = false,
                taskFocused = false,
                isHomeUidTop = true,
                hasTaskStackListener = false
            )
        )
        assertFalse(
            FloatingMenuManager.isTaskForegroundActive(
                taskVisible = false,
                taskFocused = false,
                isHomeUidTop = false,
                hasTaskStackListener = false
            )
        )

        // Split-screen under IUidObserver fallback keeps visible side pane active when Home is not TOP
        assertTrue(
            FloatingMenuManager.isTaskForegroundActive(
                taskVisible = true,
                taskFocused = false,
                isHomeUidTop = false,
                hasTaskStackListener = false
            )
        )

        // Split-screen under TaskStackListener follows exact task focus changes
        assertFalse(
            FloatingMenuManager.isTaskForegroundActive(
                taskVisible = true,
                taskFocused = false,
                isHomeUidTop = false,
                hasTaskStackListener = true
            )
        )

        // Defensive null-field fallback
        assertTrue(
            FloatingMenuManager.isTaskForegroundActive(
                taskVisible = true,
                taskFocused = null,
                isHomeUidTop = false,
                hasTaskStackListener = false
            )
        )
        assertFalse(
            FloatingMenuManager.isTaskForegroundActive(
                taskVisible = true,
                taskFocused = null,
                isHomeUidTop = true,
                hasTaskStackListener = false
            )
        )
    }
}
