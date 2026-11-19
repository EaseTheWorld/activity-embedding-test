package com.example.core.item

/**
 * Implemented by in-process Activities that correspond to a top-level category menu entry.
 *
 * The menu host observes Activity lifecycle callbacks and queries [getCurrentMenuId] to keep
 * menu selection synchronized across stacked category transitions and Back returns.
 */
interface GetCurrentMenuId {
    /**
     * Returns the menu category ID to highlight when this Activity resumes,
     * or `null` to skip menu synchronization (e.g. `BoundaryActivity` during forward launch).
     */
    fun getCurrentMenuId(): String?
}
