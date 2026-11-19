# Stacked Category History & `BoundaryActivity` Architecture

## 1. Overview

In this architecture, switching categories pushes each category onto the Task back stack instead of replacing categories via `CLEAR_TOP` over `HomeActivity`. Pressing Back unwinds through every visited category (and any sub-activities) in exact reverse order while restoring each category's last UI state and keeping the host menu selection synchronized—including transitions across external-process APKs (such as `LightSettingsActivity` in `com.example.carsettings.light`).

Example flow in Wide Split Mode:
1. Launch app -> `[Home (root)]` (menu selects `Home`)
2. Select `Seats & Comfort` -> open `GenericSubActivity` -> `[Home, Seats, GenericSubActivity]` (menu selects `Seats`)
3. Select `Vehicle Lights` (external APK) -> `[Home, Seats, GenericSubActivity, Light]` (menu selects `Vehicle Lights`)
4. Select `Doors & Locks` -> wraps with `BoundaryActivity("light")` -> `[Home, Seats, GenericSubActivity, Light, BoundaryActivity("light"), Doors]` (menu selects `Doors & Locks`)
5. Press Back repeatedly:
   - `Doors` finishes -> `BoundaryActivity` restores `Vehicle Lights` menu selection and finishes -> `Light` appears (no flicker)
   - `Light` finishes -> `GenericSubActivity` resumes -> `LifecycleApp` syncs menu to `Seats`
   - `GenericSubActivity` finishes -> `Seats` resumes -> menu remains `Seats`
   - `Seats` finishes -> `Home (root)` resumes -> menu selects `Home`
   - Back on `Home (root)` -> `finishAffinity()` exits app cleanly

---

## 2. Core Components

### 2.1 `GetCurrentMenuId` Contract
Defined in [`GetCurrentMenuId.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/core-item-contract/src/main/java/com/example/core/item/GetCurrentMenuId.kt):
- Implemented by in-process content Activities associated with a category menu item:
  - [`HomeActivity`](file:///c:/Users/user/Documents/antigravity/kind-curie/feature-home/src/main/java/com/example/feature/home/HomeActivity.kt): returns `"home"`. Distinguishes initial root Home (`EXTRA_IS_ROOT_HOME = true`, calls `finishAffinity()` and `notifyAppExit()` on Back) from stacked Home instances (`EXTRA_IS_ROOT_HOME = false`, calls `finish()` on Back).
  - [`GenericSettingsActivity`](file:///c:/Users/user/Documents/antigravity/kind-curie/common-ui-settings/src/main/java/com/example/common/ui/settings/GenericSettingsActivity.kt): returns `resolveCategoryId(intent)` (from deep link URI `myapp://navigate/{categoryId}`, `EXTRA_CATEGORY_ID`, `"category_id"`, or intent action). Configured with `android:launchMode="standard"` in [`AndroidManifest.xml`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/AndroidManifest.xml) so switching between internal categories pushes separate stacked instances.
  - [`GenericSubActivity`](file:///c:/Users/user/Documents/antigravity/kind-curie/common-ui-settings/src/main/java/com/example/common/ui/settings/GenericSubActivity.kt): captures its parent category's menu ID (`EXTRA_CATEGORY_ID` or `CategoryMenuTracker.activeMenuId`) and persists it in `savedInstanceState`.
  - [`BoundaryActivity`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/BoundaryActivity.kt): returns `EXTRA_BOUNDARY_MENU_ID` of the external category directly beneath it.

### 2.2 `CategoryMenuTracker` & `LifecycleApp` Lifecycle Observer
- [`CategoryMenuTracker.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/common-ui-settings/src/main/java/com/example/common/ui/settings/CategoryMenuTracker.kt) holds:
  - `activeMenuId`: currently selected menu category ID.
  - `inheritedMenuIds`: `WeakHashMap` fallback for any internal sub-activity that does not implement `GetCurrentMenuId`.
- [`LifecycleApp.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/LifecycleApp.kt) registers `Application.ActivityLifecycleCallbacks`:
  - Skips menu/router/relay chrome (`BaseCategoryMenuActivity`, `BoundaryActivity`, `DeepLinkRouterActivity`).
  - On `onActivityCreated`: records inherited fallback menu ID for internal sub-activities.
  - On `onActivityResumed`: reads `GetCurrentMenuId.getCurrentMenuId()` (or inherited fallback) and updates `activeMenuId` -> notifies menu listeners (`BaseCategoryMenuActivity.selectCategoryInList`).
  - On `onActivityDestroyed`: cleans up `inheritedMenuIds`.

### 2.3 `BoundaryActivity` & External Category Detection (`ActivityManager.getAppTasks`)
- Because Android `ActivityLifecycleCallbacks` only fire inside the host process, returning via Back from a child category back onto an external-process Activity (either an external category like `LightSettingsActivity` or an external sub-activity launched by an internal category) would otherwise go unnoticed by `ListActivity`.
- Right before launching a new category, [`BaseCategoryMenuActivity.isTopActivityExternal()`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/BaseCategoryMenuActivity.kt) queries `ActivityManager.appTasks` for the matching `taskId` and checks `taskInfo.topActivity.packageName != packageName`:
  - **When `false`** (top Activity is in the host package): starts `targetIntent` directly (calling `CategoryMenuTracker.onExternalCategoryLaunched(categoryId)` if `targetPackage != packageName`).
  - **When `true`** (top Activity is in an external package — whether an external category or an external sub-activity launched from an internal category): starts [`BoundaryActivity`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/BoundaryActivity.kt) with `EXTRA_BOUNDARY_MENU_ID`, `EXTRA_NEXT_TARGET_INTENT`, `EXTRA_NEXT_CATEGORY_ID`, and `EXTRA_NEXT_IS_EXTERNAL`.
- [`BoundaryActivity`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/BoundaryActivity.kt) launches `EXTRA_NEXT_TARGET_INTENT` via `registerForActivityResult(StartActivityForResult())` in `onCreate` (guarded by `KEY_HAS_LAUNCHED_NEXT` for Process Death safety). When the child finishes via Back, the result callback runs before `onResume()`, calls `CategoryMenuTracker.onReturnedToExternalBoundary(boundaryMenuId)`, and immediately calls `finish()` with `overridePendingTransition(0, 0)`.

### 2.4 Non-Menu Entry Points & Process Death
- **Home Quick Access / Search** ([`HomeActivity.launchTargetSetting`](file:///c:/Users/user/Documents/antigravity/kind-curie/feature-home/src/main/java/com/example/feature/home/HomeActivity.kt)) & **Dashboard External Launch** ([`GenericSettingsActivity.launchExternalCategory`](file:///c:/Users/user/Documents/antigravity/kind-curie/common-ui-settings/src/main/java/com/example/common/ui/settings/GenericSettingsActivity.kt)): launch directly on top without `BoundaryActivity` (caller is already an internal host Activity) and call `CategoryMenuTracker.onExternalCategoryLaunched(...)` when targeting an external package.
- **Warm Deep Links** ([`DeepLinkRouterActivity.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/DeepLinkRouterActivity.kt)): route through `ListActivity` (`FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_SINGLE_TOP`) -> `handleIntent()` -> `dispatchCategoryIntent()` so deep links participate cleanly in stacked history.
- **Process Death**: [`ListActivity.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/ListActivity.kt) persists `KEY_ACTIVE_MENU_ID` in `onSaveInstanceState` and restores `CategoryMenuTracker` state on `onCreate`, while `BoundaryActivity` persists `KEY_HAS_LAUNCHED_NEXT` so recreating `BoundaryActivity` does not re-launch the child category. Because `isTopActivityExternal()` queries `ActivityManager.appTasks` directly from the OS, no external-top flag needs to be persisted.

---

## 3. Wide Split Mode Flicker Verification

Inspected `BoundaryActivity` transitions (`Vehicle Lights -> BoundaryActivity -> Doors & Locks` and `Doors & Locks -> Back -> BoundaryActivity -> Vehicle Lights`) via `adb shell screenrecord` + `ffmpeg` frame extraction on `emulator-5554`:
- `BoundaryActivity` uses `Theme.LifecycleApp.Translucent` with no layout inflation (`setContentView` is never called) and `overridePendingTransition(0, 0)`.
- Calling `finish()` inside `nextLauncher`'s `ActivityResult` callback finishes `BoundaryActivity` before rendering a resumed frame to SurfaceFlinger.
- Frame-by-frame analysis across `Doors -> Back -> Light` showed direct transition between consecutive SurfaceFlinger frames with zero intermediate black, white, or blank frames.
