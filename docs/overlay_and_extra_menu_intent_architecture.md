# Overlay Mode & `EXTRA_MENU_INTENT` Architecture

## 1. Overview

This document describes how the Settings app handles **Narrow (Overlay) Mode** vs **Wide (Split) Mode**, how category screens render and launch the category drawer via the **`EXTRA_MENU_INTENT`** round-trip echo contract, how **`HomeActivity`** detects Back-key returns from categories (including external APKs), and how state survives **Background -> Process Death**.

---

## 2. Wide (Split) Mode vs Narrow (Overlay) Mode

Mode branching is controlled by `AppConfig.OVERLAY_MODE_WIDTH_DP_BREAKPOINT` (`840dp`) in [`AppConfig.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/core-item-contract/src/main/java/com/example/core/item/AppConfig.kt):

| Aspect | Wide Mode (`width >= 840dp`) | Narrow / Overlay Mode (`width < 840dp`) |
|---|---|---|
| Primary Menu Host | `ListActivity` persistent left 35% Split pane | `ListWithTranslucentActivity` translucent modal drawer launched on demand |
| Category Main Header | Hidden (`shouldShowCategoryHeader == false`) because the left sidebar is always visible | Shown (`shouldShowCategoryHeader == true`) with an inline top-left circular Menu icon + Category Title |
| Menu Launch Trigger | N/A (sidebar always visible) | Top-left inline Menu button launches `EXTRA_MENU_INTENT` |

`SplitInitializer` and `ModeTransitionManager` handle Activity Embedding split rules and dynamic configuration transitions when window width crosses the `840dp` breakpoint.

---

## 3. `EXTRA_MENU_INTENT` Round-Trip Echo Contract

### Why `EXTRA_MENU_INTENT`?
In Narrow Mode, `ListWithTranslucentActivity` is not permanently mounted in a split pane—it is only launched when the user taps the top-left menu button on a category screen. To highlight the currently active category inside the drawer (and survive Process Death), the drawer needs `extra_category_id` (`AppConfig.EXTRA_CATEGORY_ID`).

Instead of passing a raw `ComponentName` (`EXTRA_MENU_COMPONENT`) and expecting every internal or external category Activity to remember to attach `extra_category_id` when opening the drawer, the host pre-builds the complete drawer `Intent` and passes it in `AppConfig.EXTRA_MENU_INTENT` (`"extra_menu_intent"`):

```
Host (ListActivity / BaseCategoryMenuActivity / DeepLinkResolver / HomeActivity)
  │
  │  menuIntent = Intent(ListWithTranslucentActivity).apply {
  │      putExtra(AppConfig.EXTRA_CATEGORY_ID, targetCategoryId)
  │  }
  │  targetIntent.putExtra(AppConfig.EXTRA_MENU_INTENT, menuIntent)
  ▼
Category Activity (HomeActivity / GenericSettingsActivity / LightSettingsActivity)
  │
  │  User taps top-left Menu button
  ▼
startActivity(Intent(menuIntent)) + overridePendingTransition(0, 0)
  │
  ▼
ListWithTranslucentActivity
  └─ Reads extra_category_id from its launching Intent, highlights the row,
     and persists activeCategoryId in onSaveInstanceState
```

### Host Callers
- [`ListActivity.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/ListActivity.kt): Builds `menuIntent` with `extra_category_id = "home"` when launching `HomeActivity`.
- [`BaseCategoryMenuActivity.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/BaseCategoryMenuActivity.kt): Uses `buildMenuIntent(category.id)` for every category launch, `"home"` `CLEAR_TOP` navigation, and deep link fallbacks.
- [`DeepLinkResolver.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/feature-home/src/main/java/com/example/feature/home/DeepLinkResolver.kt) & [`DeepLinkRouterActivity.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/app/src/main/java/com/example/lifecycleapp/DeepLinkRouterActivity.kt): Populate `EXTRA_MENU_INTENT` with the resolved `categoryId` for both cold and warm deep links.
- [`HomeActivity.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/feature-home/src/main/java/com/example/feature/home/HomeActivity.kt) (`launchTargetSetting`) & [`GenericSettingsActivity.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/common-ui-settings/src/main/java/com/example/common/ui/settings/GenericSettingsActivity.kt) (`launchExternalCategory`): Clone `menuIntentState.value` and override `EXTRA_CATEGORY_ID` to the destination category ID before launching another category.

### Category Consumers
- [`CategoryMenuSupport.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/common-ui-settings/src/main/java/com/example/common/ui/settings/CategoryMenuSupport.kt): Provides `extractMenuIntent(intent)` and `launchMenuDrawer(activity, menuIntent)` plus the shared `CategoryMainHeader` Composable.
- [`HomeActivity.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/feature-home/src/main/java/com/example/feature/home/HomeActivity.kt) & [`GenericSettingsActivity.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/common-ui-settings/src/main/java/com/example/common/ui/settings/GenericSettingsActivity.kt): Extract `EXTRA_MENU_INTENT` into `menuIntentState` and pass `{ launchMenuDrawer(activity, menuIntent) }` directly to `HomeScreen` / `SettingsNavHost`.
- [`LightSettingsActivity.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/plugin-light/src/main/java/com/example/feature/light/LightSettingsActivity.kt) (external APK `com.example.carsettings.light`): Reads `"extra_menu_intent"` directly as a `Parcelable` `Intent` (no `core-item-contract` or `common-ui-settings` dependency required) and starts `Intent(targetMenu)` on `btnMenu` click.

> [!NOTE]
> **Simplicity Design Choice**: Category activities echo the `EXTRA_MENU_INTENT` they received as-is without rewriting `extra_category_id` during intra-Activity Composable navigation (such as Dashboard -> Doors inside `GenericSettingsActivity`).

---

## 4. Back Navigation Detection (`StartActivityForResult` + `CategoryResultTracker`)

When a category is launched from `HomeActivity`, the user can return to `HomeActivity` in two ways:
1. **Back key** from a category Activity (including external APKs like `LightSettingsActivity` that finish without calling `setResult()`).
2. **Switching to `Home`** via `CLEAR_TOP | SINGLE_TOP` from the sidebar (`ListActivity`) or drawer (`ListWithTranslucentActivity`).

[`HomeActivity.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/feature-home/src/main/java/com/example/feature/home/HomeActivity.kt) launches categories via `registerForActivityResult(ActivityResultContracts.StartActivityForResult())` and coordinates state through `CategoryResultTracker` in [`HomeNavigationBridge.kt`](file:///c:/Users/user/Documents/antigravity/kind-curie/feature-home/src/main/java/com/example/feature/home/HomeNavigationBridge.kt):

- `onLaunchStarted()`: Marks `isCategoryRunning = true`.
- `onPrepareSwitch()`: Called before switching from one category to another (`CLEAR_TOP` or replacing top); increments `suppressedResultCount` and resets `isCategoryRunning = false`.
- `onActivityResult()`:
  - If `suppressedResultCount > 0`, decrements `suppressedResultCount` and ignores the result (covers both **early arrival** before the new launch and **late arrival** after the new category starts).
  - Otherwise, if `isCategoryRunning` is `true`, resets `isCategoryRunning = false` and returns `true` so `HomeActivity` calls `HomeNavigationBridge.notifyHomeRevealed()` and `ListActivity` resets its selection to `Home (0)`.

---

## 5. Background -> Process Death State Preservation

Android automatically restores each Activity's launching `Intent` (and its extras) across Process Death. To avoid duplicating `Intent` extras into `savedInstanceState`:
- **`GenericSettingsActivity.kt`** no longer calls `intent.replaceExtras(Bundle())` when consuming deep-link mutation parameters. Instead, it skips host UI extras (`EXTRA_MENU_INTENT`, `EXTRA_CATEGORY_HEADER_BREAKPOINT_DP`, `EXTRA_SHOW_CATEGORY_HEADER`) and calls `intent.removeExtra(key)` only for consumed setting-mutation keys.
- **`onSaveInstanceState`** is used strictly for **runtime state** not present in the original `Intent`:
  - `HomeActivity`: `KEY_IS_CATEGORY_RUNNING`, `KEY_SUPPRESSED_RESULT_COUNT` (`CategoryResultTracker`)
  - `ListWithTranslucentActivity`: `KEY_ACTIVE_CATEGORY_ID`
  - `LightSettingsActivity`: `KEY_IS_DETAIL_OPEN`
