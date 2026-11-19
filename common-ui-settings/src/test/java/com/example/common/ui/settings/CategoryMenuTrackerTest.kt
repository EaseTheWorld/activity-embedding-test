package com.example.common.ui.settings

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CategoryMenuTrackerTest {

    private val notifications = mutableListOf<String>()
    private val listener: (String) -> Unit = { notifications.add(it) }

    @Before
    fun setUp() {
        CategoryMenuTracker.resetForTests()
        notifications.clear()
        CategoryMenuTracker.registerListener(listener)
    }

    @After
    fun tearDown() {
        CategoryMenuTracker.unregisterListener(listener)
        CategoryMenuTracker.resetForTests()
    }

    @Test
    fun `stacked internal and external transitions preserve exact menu selection on Back`() {
        val homeAct = Any()
        val seatAct = Any()
        val seatSubAct = Any()
        val doorAct = Any()

        // 1. Home starts
        CategoryMenuTracker.onInternalActivityCreated(homeAct, "home")
        CategoryMenuTracker.onInternalActivityResumed(homeAct, "home")
        assertEquals("home", CategoryMenuTracker.activeMenuId)

        // 2. Menu selects Seat (internal)
        CategoryMenuTracker.onInternalActivityCreated(seatAct, "seat")
        CategoryMenuTracker.onInternalActivityResumed(seatAct, "seat")
        assertEquals("seat", CategoryMenuTracker.activeMenuId)

        // 3. Inside Seat, launch Sub-Activity that inherits menu ID without implementing GetCurrentMenuId
        CategoryMenuTracker.onInternalActivityCreated(seatSubAct, explicitMenuId = null)
        CategoryMenuTracker.onInternalActivityResumed(seatSubAct, directMenuId = null)
        assertEquals("seat", CategoryMenuTracker.activeMenuId)

        // 4. Switch from Sub-Activity -> Light (external)
        CategoryMenuTracker.onExternalCategoryLaunched("light")
        assertEquals("light", CategoryMenuTracker.activeMenuId)

        // 5. While on Light (external), switch -> Door (internal) wrapped by Boundary("light")
        CategoryMenuTracker.onInternalActivityCreated(doorAct, "door")
        CategoryMenuTracker.onInternalActivityResumed(doorAct, "door")
        assertEquals("door", CategoryMenuTracker.activeMenuId)

        // 6. Back from Door -> Boundary("light") restores "light"
        CategoryMenuTracker.onReturnedToExternalBoundary("light")
        assertEquals("light", CategoryMenuTracker.activeMenuId)

        // 7. Back from Light -> Seat Sub-Activity resumes -> inherits "seat"
        CategoryMenuTracker.onInternalActivityResumed(seatSubAct, directMenuId = null)
        assertEquals("seat", CategoryMenuTracker.activeMenuId)

        // 8. Back from Sub-Activity -> Seat resumes
        CategoryMenuTracker.onInternalActivityDestroyed(seatSubAct)
        CategoryMenuTracker.onInternalActivityResumed(seatAct, "seat")
        assertEquals("seat", CategoryMenuTracker.activeMenuId)

        // 9. Back from Seat -> Home resumes
        CategoryMenuTracker.onInternalActivityDestroyed(seatAct)
        CategoryMenuTracker.onInternalActivityResumed(homeAct, "home")
        assertEquals("home", CategoryMenuTracker.activeMenuId)

        assertEquals(
            listOf("home", "seat", "seat", "light", "door", "light", "seat", "seat", "home"),
            notifications
        )
    }

    @Test
    fun `switching between two external categories preserves boundary chain and process death state`() {
        // Light1 (external) -> Boundary("light1") -> Light2 (external)
        CategoryMenuTracker.onExternalCategoryLaunched("light1")
        CategoryMenuTracker.onExternalCategoryLaunched("light2")
        assertEquals("light2", CategoryMenuTracker.activeMenuId)

        // Simulate Process Death while Light2 (external) is on top
        val savedMenuId = CategoryMenuTracker.activeMenuId
        CategoryMenuTracker.resetForTests()
        CategoryMenuTracker.registerListener(listener)

        CategoryMenuTracker.restoreState(savedMenuId)
        assertEquals("light2", CategoryMenuTracker.activeMenuId)

        // Back from Light2 triggers BoundaryActivity("light1")
        CategoryMenuTracker.onReturnedToExternalBoundary("light1")
        assertEquals("light1", CategoryMenuTracker.activeMenuId)
    }
}
