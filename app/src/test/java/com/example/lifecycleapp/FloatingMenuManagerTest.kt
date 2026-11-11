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
    fun `isTaskForegroundActive requires both taskVisible and taskFocused`() {
        // Active foreground (internal or external plugin Activity): visible + focused
        assertTrue(
            FloatingMenuManager.isTaskForegroundActive(
                taskVisible = true,
                taskFocused = true
            )
        )

        // Unfocused visible task (e.g. unfocused split pane or live tile)
        assertFalse(
            FloatingMenuManager.isTaskForegroundActive(
                taskVisible = true,
                taskFocused = false
            )
        )

        // Home key or fullscreen third-party app (Settings): task not visible
        assertFalse(
            FloatingMenuManager.isTaskForegroundActive(
                taskVisible = false,
                taskFocused = false
            )
        )

        // Focused flag without task visibility
        assertFalse(
            FloatingMenuManager.isTaskForegroundActive(
                taskVisible = false,
                taskFocused = true
            )
        )
    }
}
