package com.example.common.ui.settings

import androidx.appcompat.app.AppCompatActivity
import com.example.core.item.GetCurrentMenuId

/**
 * Common base class for all in-process content Activities participating in stacked
 * category navigation (category roots, internal sub-activities, and `BoundaryActivity`).
 *
 * Implements [GetCurrentMenuId] so the menu host (`ListActivity`) can observe all content
 * Activities uniformly via `Application.ActivityLifecycleCallbacks.onActivityResumed`.
 */
abstract class BaseMenuContentActivity : AppCompatActivity(), GetCurrentMenuId
