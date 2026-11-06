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

### 2. Dual-Organizer Desynchronization Prevention
Attempting to control WCT directly via ATMS (`woc.applyTransaction(wct)`) bypasses `SplitController`. Because `SplitController` caches Task container state (`mTaskContainers`, `mSplitContainers`), out-of-band transactions can cause internal desynchronization.
Calling `SplitController.finishActivityStacks(setOf(token))` ensures:
- `SplitController` updates its internal container hierarchy.
- The WCT (`deleteTaskFragment`) is dispatched to `system_server` through the authorized organizer path.
- Both internal and external categories are dismissed cleanly and atomically.

---

## Decisions

```mermaid
flowchart TD
    subgraph HostProcess["Host Process (com.example.lifecycleapp)"]
        BaseMenu["BaseCategoryMenuActivity / PrimaryOverlayActivity"]
        HomeAct["HomeActivity (Bottom Container)"]
        Bridge["HomeNavigationBridge"]
        Helper["OverlayBoundsHelper.dismissCategoryContainer(taskId)"]
        SplitCtrl["SplitController (TaskFragmentOrganizer)"]
        TaskContainers["mTaskContainers[taskId]"]
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
    Bridge -->|2. notifyDismissCategoryStacks| Helper
    Helper -->|Lookup non-home containers| TaskContainers
    TaskContainers -.->|Holds Token| Helper
    Helper -->|finishActivityStacks(tokens)| SplitCtrl
    SplitCtrl -->|WCT deleteTaskFragment| WOC
    WOC -->|Dismiss TaskFragment| LightAct
    TaskRec -->|Reveals underneath| HomeAct
```

### 1. Complete Removal of `CategoryHostActivity`
- Permanently deleted `CategoryHostActivity.kt`.
- Removed its declaration from `feature-home/src/main/AndroidManifest.xml`.
- Removed all host wrapper logic from `ModeTransitionManager.kt`.
- [HomeNavigationBridge.kt](file:///c:/Users/user/Documents/antigravity/kind-curie/feature-home/src/main/java/com/example/feature/home/HomeNavigationBridge.kt) and [HomeActivity.kt](file:///c:/Users/user/Documents/antigravity/kind-curie/feature-home/src/main/java/com/example/feature/home/HomeActivity.kt) launch category intents directly.

### 2. Implementation of `dismissCategoryContainer` with Home Protection
- In [OverlayBoundsHelper.kt](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/OverlayBoundsHelper.kt):
  - In **Wide (Split) Mode**: Targets the secondary split container via `taskContainer.getTopNonFinishingSplitContainer()?.getSecondaryContainer()`.
  - In **Narrow (Overlay) Mode**: Enumerate all content containers via `taskContainer.getTaskFragmentContainers()`.
  - Protects `HomeActivity` (`!isHomeContainer(container)`) and `PrimaryOverlayActivity` (`!isOverlay`) so that their lifecycle states are strictly preserved.
  - Retrieves `token = container.getTaskFragmentToken() as IBinder`.
  - Executes `splitController.finishActivityStacks(targetTokens)`.

### 3. Natural Back-to-Home Resolution & Menu Synchronization
- With the category container sitting directly above `HomeActivity`, pressing the Back key on any category root immediately reveals `HomeActivity` in a single step with 0ms transition delay.
- `HomeActivity.onResume()` invokes `HomeNavigationBridge.notifyHomeRevealed()`, which triggers [BaseCategoryMenuActivity.kt](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/BaseCategoryMenuActivity.kt) to update selection back to **Home (position 0)**.

---

## Verification & Proof

### Actual Logcat Proof on Android 17 (API 37)
Captured during `scenario9` (Wide Mode: Seat -> Door -> Light [external] -> Home):
```log
11-06 00:53:15.241 15462 15462 D OverlayBoundsHelper: [dismissCategoryContainer] Inspecting taskContainer for taskId=69
11-06 00:53:15.241 15462 15462 D OverlayBoundsHelper: [dismissCategoryContainer][Wide] SecondaryContainer found: token=android.os.Binder@68f7759, topAct=null (null indicates external process activity!), isHome=false
11-06 00:53:15.255 15462 15462 D OverlayBoundsHelper: Dismissed category TaskFragments: count=1, tokens=[android.os.Binder@68f7759] (taskId=69)
```
- `topAct=null`: Confirms that the target is an external process activity without an in-process `Activity` instance.
- `token=android.os.Binder@68f7759`: Confirms that `TaskFragmentContainer` retains the valid Binder token received from AOSP `system_server`.
- `Dismissed category TaskFragments`: Confirms atomic dismissal via `SplitController.finishActivityStacks`.

### Automated Test Coverage
All scenarios in [InternalBackStackUiAutomatorTest.kt](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/androidTest/java/com/example/lifecycleapp/InternalBackStackUiAutomatorTest.kt) pass:
- `scenario7_CategorySelection_DirectLaunchWithoutHostActivity`: Direct launch without host.
- `scenario8_NarrowOverlayMode_PrimaryOverlayAndHomeNeverDestroyedAcrossCategorySwitches`: Category switching without destroying Home or Overlay.
- `scenario9_WideAndNarrowModes_NoActivityRecreatedByConfigChange`: Full lifecycle immunity during transitions.
- `scenario10_WideMode_InternalCategory_ExhaustBackStack_RevealsHomeState_UpdatesSelection_AndExitsApp`: Back stack exhaustion to Home in Wide mode.
- `scenario11_WideMode_ExternalCategory_ExhaustBackStack_RevealsHomeState_UpdatesSelection_AndExitsApp`: External category Back stack exhaustion in Wide mode.
- `scenario12_NarrowOverlayMode_InternalAndExternal_ExhaustBackStack_RevealsHomeState_UpdatesSelection_AndExitsApp`: Internal & external category Back stack exhaustion in Narrow mode.
