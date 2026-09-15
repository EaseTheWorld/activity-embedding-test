# ADR 0005: Elimination of ItemRendererRegistry and Screen-Independent UI Architecture

## Status
**Accepted**

## Context
In ADR 0004, we decoupled UI item metadata from reactive state and established ItemViewModelRegistry as the single source of truth for runtime states. However, an architectural vestige remained in the presentation layer:
- ItemRendererRegistry: A runtime map registry (Map<KClass<out Item>, ItemRenderer<*, *>>) mapping Item classes to ItemRenderer instances using reflection (isAssignableFrom) and unchecked casts.
- In practice, primary category screens ([GenericSettingsScreen.kt](file:///c:/Users/user/Documents/antigravity/kind-curie/common-ui-settings/src/main/java/com/example/common/ui/settings/GenericSettingsScreen.kt), [MainSettingsDashboardScreen.kt](file:///c:/Users/user/Documents/antigravity/kind-curie/common-ui-settings/src/main/java/com/example/common/ui/settings/MainSettingsDashboardScreen.kt)) had already transitioned to direct, declarative Jetpack Compose layouts (when (item) { is UiToggleItem -> ... }).
- Consequently, ItemRendererRegistry lingered only for the Recent category screen, while list renderers (ToggleListRenderer, ChoiceListRenderer, SliderListRenderer) and dual composition locals (LocalListItemRendererRegistry, LocalGridItemRendererRegistry) existed as dead/underutilized code.
- Furthermore, an artificial registry pattern restricted each item to a 1:1 renderer mapping, conflicting with the requirement that **different screens must be free to render the exact same UiItem in completely different visual presentations** (e.g. detailed full-width row with subtitle/badge vs. compact quick-toggle tile vs. 2D vehicle overlay control).

---

## Decisions

### 1. Complete Elimination of ItemRendererRegistry
- Permanently deleted ItemRendererRegistry.kt (~181 lines), removing:
  - ItemRenderer<I, T> interface
  - ItemRendererRegistry class, internal enderers map, and reflection-based isAssignableFrom lookup
  - ToggleListRenderer, ChoiceListRenderer, SliderListRenderer
  - ToggleGridCardRenderer, ChoiceGridCardRenderer, SliderGridCardRenderer
  - LocalListItemRendererRegistry and LocalGridItemRendererRegistry
- Removed unused legacy interface ComposableItemRenderer.kt.
- Removed SeatLumbarRenderer class from eature-seat, retaining pure Composable functions SeatLumbarRow.

### 2. Screen-Independent UI Presentation
- **Screen Autonomy**: Each Screen owns its UI layout and decides how to present any UiItem.
  - Full Category Screens render detailed rows with rich subtitles, icons, badges, and segmented option chips.
  - Recent Screens render compact grid cards via screen-specific composables (RecentItemCard) or configurable slots (itemCardContent).
  - Dashboard Quick Controls render customized quick-action cards (DashboardGridItemCard).
  - Feature Modules are free to render bespoke 2D/3D layouts without conforming to a global registry interface.
- **No Global UI Unification**: Screens are deliberately NOT forced into a rigid, unified card interface. Compose composition and slots provide full layout expressiveness.

`mermaid
graph TD
    subgraph SSOT["Single Source of Truth (State & Business Logic)"]
        VMRegistry["ItemViewModelRegistry<br/>Key: item.id"]
        ItemVM["ItemViewModel&lt;T&gt;<br/>StateFlow&lt;T&gt; + Mutability"]
        VMRegistry -->|Guaranteed by ID| ItemVM
    end

    subgraph Screen1["Screen Context 1: Category Screen"]
        Row["ToggleItemRow()<br/>Detailed row + Subtitle + Badge + Icon"]
    end

    subgraph Screen2["Screen Context 2: Dashboard Quick Controls"]
        DashCard["DashboardGridItemCard()<br/>Compact 2-column quick control"]
    end

    subgraph Screen3["Screen Context 3: Recent Screen"]
        RecentCard["RecentItemCard() / itemCardContent slot<br/>Screen-specific tile layout"]
    end

    Row -.->|resolves by id| VMRegistry
    DashCard -.->|resolves by id| VMRegistry
    RecentCard -.->|resolves by id| VMRegistry
`

### 3. State & ViewModel SSOT Guaranteed by ID
- The sole contract across all screens is the item's unique identifier (item.id).
- Extracted LocalItemViewModelRegistry to a dedicated file:
  al LocalItemViewModelRegistry = staticCompositionLocalOf { ItemViewModelRegistry() }
- Any Screen, regardless of its layout or visual hierarchy, resolves the single-source-of-truth ItemViewModel<T> using iewModelRegistry.getViewModel(item.id).
- Mutations performed in one screen (e.g. toggling a quick-control switch on the Dashboard) propagate reactively and instantaneously to all other screens displaying the same item.

### 4. Direct Compose Pattern and Screen-Level Eligibility
- Replaced registry lookup with compile-time type-safe Kotlin smart casting (when (item)).
- Recent category eligibility is simplified from registry querying to direct predicate checks:
  `kotlin
  fun isEligibleForRecent(
      item: Item,
      predicate: (Item) -> Boolean = { it is UiToggleItem || it is UiChoiceItem || it is UiSliderItem }
  ): Boolean = predicate(item)
  `

---

## Consequences

### Positive
1. **Idiomatic Declarative Jetpack Compose**: Replaced legacy Android View-era type-to-viewholder map registries with pure composable functions and compile-time type safety.
2. **True Screen Presentation Autonomy**: The same UiItem can be rendered in arbitrarily distinct visual styles across different screens without creating duplicate items or artificial wrappers.
3. **Guaranteed Cross-Screen State Consistency**: All UI variants bind to the exact same ItemViewModel<T> by item.id, ensuring bidirectional state synchronization with zero glue code.
4. **Codebase Footprint Reduction**: Deleted ItemRendererRegistry.kt (181 lines), obsolete adapter classes, and legacy ComposableItemRenderer.kt.
5. **Zero Reflection / Casting Overhead**: Eliminated isAssignableFrom reflection lookups and @Suppress("UNCHECKED_CAST") annotations during Compose recomposition.

### Negative / Trade-offs
- Adding support for a new custom item in a specific screen requires that screen (or its caller via composable slots) to handle the custom item type. This is mitigated by composable fallback cards and customizable slots (e.g. itemCardContent in RecentSettingsScreen, customCategoryLayouts in MainSettingsDashboardScreen).