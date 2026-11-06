# ADR 0006: Elimination of CategoryHostActivity and Direct TaskFragment Orchestration

## Status
**Accepted**

## Context
In the previous architecture, [CategoryHostActivity.kt](file:///c:/Users/user/Documents/antigravity/kind-curie/feature-home/src/main/java/com/example/feature/home/CategoryHostActivity.kt) was introduced as an intermediary shim activity between [HomeActivity.kt](file:///c:/Users/user/Documents/antigravity/kind-curie/feature-home/src/main/java/com/example/feature/home/HomeActivity.kt) and category screens.

### Why CategoryHostActivity was originally introduced:
1. **External Process Activity Control Concern**:
   External plugins (e.g. `LightSettingsActivity` in `com.example.carsettings.light`) execute in a distinct Linux process (`pid`) and UID. In standard Android application development, cross-process `Activity` references cannot be held in memory, making standard Java `activity.finish()` impossible. It was assumed that hosting them inside or over an in-process host activity would allow dismissing the category container from the host app.
2. **`CLEAR_TOP` Sweeps PrimaryOverlayActivity**:
   An initial alternative was using `onNewIntent` with `FLAG_ACTIVITY_CLEAR_TOP` on `HomeActivity` to reset the category stack. However, in AOSP `Task.java`, `clearTop()` unconditionally finishes all activities/TaskFragments above the target activity within that Task. Because `PrimaryOverlayActivity` is a TaskFragment in the same Task, `CLEAR_TOP` destroyed the overlay drawer as well.
3. **The Problems Introduced by CategoryHostActivity**:
   - Added unnecessary Activity lifecycle transitions and latency.
   - Required two Back presses or custom interceptors to navigate back to `HomeActivity`.
   - Increased state management complexity between `PrimaryActivity`, `PrimaryOverlayActivity`, `CategoryHostActivity`, and target category activities.

---

## Technical Discovery & Realization

### 1. TaskFragment Ownership & Token Availability
When an external activity (such as `LightSettingsActivity`) is embedded via `SplitPairRule` or `ActivityFilter`:
- It runs inside the host application's Task (`com.example.lifecycleapp`).
- The organizer of this Task is the WindowManager Extension's `SplitController` (`TaskFragmentOrganizer`) running in the host app process.
- `system_server` (ATMS) communicates with `SplitController` via Binder callbacks (`onTaskFragmentAppeared`, `onTaskFragmentInfoChanged`), supplying the `TaskFragmentInfo` and its unique identifier: **`fragmentToken: IBinder`**.
- While `container.getTopNonFinishingActivity()` returns `null` for cross-process activities (confirming no in-process Activity instance exists), **`container.getTaskFragmentToken()` returns the genuine IBinder token issued by AOSP system_server**.

### 2. Zero-Reflection Token Tracking via Public Jetpack WindowManager APIs
Rather than using internal reflection or dual-organizer ATMS WCT calls:
- `androidx.window.embedding.SplitController.getInstance(activity).splitInfoList(activity)` provides real-time reactive flow updates of active splits and their corresponding `ActivityStack` objects.
- In **Wide (Split) Mode**: `PrimaryActivity` observes `splitInfoList`, where secondary stacks represent the active category container.
- In **Narrow (Overlay) Mode**: `SplitInitializer` registers a `SplitPairRule` (`categorySplitRule`) between `HomeActivity` and `ACTION_SETTINGS_EMBED` with `SplitAttributes.SplitType.SPLIT_TYPE_EXPAND`. This allows full-width expanded display over `HomeActivity` while simultaneously emitting standard `SplitInfo` to `HomeActivity` via public `splitInfoList`.
- Each public `ActivityStack` encapsulates the genuine `IBinder` TaskFragment token. For external process activities (like `LightSettingsActivity`), `activityStack.contains(activity)` evaluates to false (as `activitiesInProcess` is empty), but the underlying `token: IBinder` is fully intact and authenticated by system_server.
- `androidx.window.embedding.ActivityEmbeddingController.getInstance(context).finishActivityStacks(setOf(stack))` accepts `Set<ActivityStack>` directly and dispatches `finishActivityStacksWithTokens` through the official WindowManager Extension organizer path.
- **Zero reflection (0%)** and **zero ATMS bypass** are achieved across both internal and external processes.

---

## Decisions

```mermaid
flowchart TD
    subgraph HostProcess["Host Process (com.example.lifecycleapp)"]
        BaseMenu["BaseCategoryMenuActivity / PrimaryOverlayActivity"]
        HomeAct["HomeActivity (Bottom Container)"]
        Bridge["HomeNavigationBridge"]
        CatMgr["CategoryStackManager (splitInfoList Observer)"]
        AEC["ActivityEmbeddingController (Public Jetpack API)"]
        SplitCtrl["SplitController (TaskFragmentOrganizer)"]
    end

    subgraph SystemServer["AOSP system_server (ATMS)"]
        WOC["WindowOrganizerController"]
        TaskRec["Task (id: taskId)"]
    end

    subgraph ExternalProcess["External Process (com.example.carsettings.light)"]
        LightAct["LightSettingsActivity (Embedded TaskFragment)"]
    end

    BaseMenu -->|Select Category| Bridge
    Bridge -->|1. Direct Launch Intent| HomeAct
    HomeAct -->|startActivity| SplitCtrl
    SplitCtrl -->|splitInfoList Flow| CatMgr
    CatMgr -->|Holds active ActivityStack with Token| CatMgr
    Bridge -->|2. notifyDismissCategoryStacks| CatMgr
    CatMgr -->|finishActivityStacks(setOf stack)| AEC
    AEC -->|finishActivityStacksWithTokens| SplitCtrl
    SplitCtrl -->|WCT deleteTaskFragment| WOC
    WOC -->|Dismiss TaskFragment| LightAct
    TaskRec -->|Reveals underneath| HomeAct
```

### 1. Complete Removal of `CategoryHostActivity`
- Permanently deleted `CategoryHostActivity.kt`.
- Removed its declaration from `feature-home/src/main/AndroidManifest.xml`.
- Removed all host wrapper logic from `ModeTransitionManager.kt`.
- [HomeNavigationBridge.kt](file:///c:/Users/user/Documents/antigravity/kind-curie/feature-home/src/main/java/com/example/feature/home/HomeNavigationBridge.kt) and [HomeActivity.kt](file:///c:/Users/user/Documents/antigravity/kind-curie/feature-home/src/main/java/com/example/feature/home/HomeActivity.kt) launch category intents directly.

### 2. Introduction of `CategoryStackManager` (100% Public API, 0% Reflection)
- Introduced [CategoryStackManager.kt](file:///c:/Users/user/Documents/antigravity/kind-curie/feature-home/src/main/java/com/example/feature/home/CategoryStackManager.kt):
  - In **Wide Mode**: Tracks `activeCategoryStack` by collecting `SplitController.getInstance(activity).splitInfoList(activity)` on `PrimaryActivity`. The secondary stack is identified as the category stack.
  - In **Narrow Mode**: Tracks `activeCategoryStack` by collecting `splitInfoList` on `HomeActivity` paired with `SPLIT_TYPE_EXPAND` category split rules.
  - Identifies category splits cleanly via `!isHomeStack(split.secondaryActivityStack)` where `isHomeStack` checks `HomeActivity.instance?.let { stack.contains(it) } == true`.
  - In [OverlayBoundsHelper.kt](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/OverlayBoundsHelper.kt) and [LifecycleApp.kt](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/LifecycleApp.kt), all reflection on internal `mTaskContainers` / `mLock` / `getTopNonFinishingSplitContainer` was completely removed and replaced with:
    ```kotlin
    CategoryStackManager.dismissCurrentCategory(context)
    ```
  - `dismissCurrentCategory` executes `ActivityEmbeddingController.getInstance(context).finishActivityStacks(setOf(stack))`.

### 3. Natural Back-to-Home Resolution & Menu Synchronization
- With the category container sitting directly above `HomeActivity`, pressing the Back key on any category root immediately reveals `HomeActivity` in a single step with 0ms transition delay.
- `HomeActivity.onResume()` invokes `HomeNavigationBridge.notifyHomeRevealed()`, which triggers [BaseCategoryMenuActivity.kt](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/BaseCategoryMenuActivity.kt) to update selection back to **Home (position 0)**.

---

## Verification & Proof

### Actual Logcat Proof on Android 17 (API 37)
Captured during `CategoryStackManager` tracking and dismissal:
```log
D CategoryStackManager: [CategoryStackManager] New active category stack recorded: ActivityStack{token=android.os.Binder@...}
D CategoryStackManager: [dismissCurrentCategory] Invoking ActivityEmbeddingController.finishActivityStacks with stack ActivityStack{token=android.os.Binder@...}
```
- Completely operates through public Jetpack WindowManager classes (`SplitController`, `ActivityStack`, `ActivityEmbeddingController`).
- Validated with both in-process categories (`SeatSettingsActivity`, `DoorSettingsActivity`) and external process categories (`LightSettingsActivity`).

### Automated Test Coverage
All scenarios in [InternalBackStackUiAutomatorTest.kt](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/androidTest/java/com/example/lifecycleapp/InternalBackStackUiAutomatorTest.kt) pass:
- `scenario7_CategorySelection_DirectLaunchWithoutHostActivity`: Direct launch without host.
- `scenario8_NarrowOverlayMode_PrimaryOverlayAndHomeNeverDestroyedAcrossCategorySwitches`: Category switching without destroying Home or Overlay.
- `scenario9_WideAndNarrowModes_NoActivityRecreatedByConfigChange`: Full lifecycle immunity during transitions.
- `scenario10_WideMode_InternalCategory_ExhaustBackStack_RevealsHomeState_UpdatesSelection_AndExitsApp`: Back stack exhaustion to Home in Wide mode.
- `scenario11_WideMode_ExternalCategory_ExhaustBackStack_RevealsHomeState_UpdatesSelection_AndExitsApp`: External category Back stack exhaustion in Wide mode.
- `scenario12_NarrowOverlayMode_InternalAndExternal_ExhaustBackStack_RevealsHomeState_UpdatesSelection_AndExitsApp`: Internal & external category Back stack exhaustion in Narrow mode.
