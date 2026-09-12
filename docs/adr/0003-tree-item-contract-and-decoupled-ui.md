# ADR 0003: Tree-Structured Item Contract, Decoupled UI Rendering, and Immutability

## Status
**Accepted**

## Context
In previous architectural iterations (ADR 0001 & ADR 0002), we designed a generic item model where each item implemented `ComposableItemRenderer` with a polymorphic `Draw()` method. When attempting to model composite rows (such as dual switches in one row, or a 2D car image with seat controls), we experimented with container items (`ContainerItem`) and layout items (`SpacerItem`).

However, that approach created unintended complexity:
1. **Pollution of the Item Contract**: Pure UI layout concerns (`Spacer`, `Divider`, decorative headers) had to be wrapped in the `Item` hierarchy, forcing ContentProviders and search engines to maintain whitelist/filter rules.
2. **Inversion of UI Composition**: In Jetpack Compose, the layout container (Row, Column, Box) should naturally position and lay out its children. Forcing items to draw themselves (`item.Draw()`) fought against Compose's declarative layout model.
3. **External Indexing Coupling**: External consumers like `SearchManager` need a clean, lightweight catalog tree of searchable settings without dragging along Compose UI runtime dependencies.

---

## Decisions

### 1. Tree-Structured Canonical `Item`
`Item` is defined as a pure, lightweight tree node with zero Android or Compose dependencies:
```kotlin
open class Item(
    val id: String,
    val children: Set<Item> = emptySet()
)
```
- **Tree Hierarchy**: Any item can have child items (e.g. a category node containing settings items, or a compound group containing sub-items).
- **External Consumption**: External systems (such as `SearchManager` or ContentProviders) traverse the tree via `findById(id)` and `flatten(): Sequence<Item>`.
- **Searchable-Only**: Only actual, searchable settings items belong in the `Item` tree. Non-searchable elements (Spacers, Dividers, decorative cards) **never** become `Item`s.

### 2. `UiItem` Presentation Layer with `@Immutable`
`UiItem` extends `Item` and enriches it with presentation resources:
```kotlin
@Immutable
open class UiItem(
    id: String,
    @get:StringRes val nameResId: Int,
    @get:DrawableRes val iconResId: Int? = null,
    @get:StringRes val descriptionResId: Int? = null,
    children: Set<Item> = emptySet()
) : Item(id, children)
```
- **Recomposition Optimization**: Marked with `@Immutable` so the Jetpack Compose compiler treats `UiItem` as stable, enabling **Smart Recomposition Skipping**.
- **Specialized Subclasses**: `UiToggleItem`, `UiChoiceItem`, `UiSliderItem`, and `UiActionItem` encapsulate domain state flows (`valueFlow`) and change callbacks (`onValueChanged`).

### 3. Decoupled Compose Rendering (No `Draw()` inside Items)
Composables are **NOT** created from or stored inside the menu tree:
- Items contain **no** Compose code (the old `ComposableItemRenderer.Draw()` is removed from items).
- Standard UI rows (`ToggleItemRow`, `ChoiceItemRow`, `SliderItemRow`) exist as independent Composable functions that accept `item: UiToggleItem`, etc. as parameters.
- Screens compose their layouts freely using standard Jetpack Compose components (`Spacer`, `HorizontalDivider`, `Row`, `Column`, `Card`).

```
                    ┌─────────────────────────┐
                    │      Item (Tree)        │
                    │  id, children: Set<Item>│
                    └────────────┬────────────┘
                                 │ extends
                    ┌────────────▼────────────┐
                    │     UiItem (@Immutable) │
                    │  nameResId, iconResId   │
                    └────────────┬────────────┘
                                 │
         ┌───────────────────────┴───────────────────────┐
         ▼                                               ▼
┌───────────────────────────┐               ┌──────────────────────────┐
│       SearchManager       │               │       Jetpack Compose    │
│  Traverses pure Item tree │               │  Normal Composable rows  │
│  Indexes IDs & names      │               │  Take UiItem as param    │
│  (Zero Compose dependency)│               │  Spacers & Dividers pure │
└───────────────────────────┘               └──────────────────────────┘
```

### 4. Handling Compound & Complex Layouts
This decoupling makes complex UI layouts straightforward:
- **Dual Toggle in One Row**: Two independent `UiToggleItem`s exist in the tree (e.g. `frunk_light` and `trunk_light`). A Compose function `DualToggleRow(leftItem, rightItem)` places them side-by-side in a `Row` with a standard Compose `Spacer`.
- **2D Car View with Seat Controls**: A Composable renders the car illustration and positions 4 `UiChoiceItem` controls over it. The car illustration is pure UI layout, not an `Item`.

---

## Consequences

### Positive
- **Clean Architecture**: `Item` is a pure business/catalog metadata tree suitable for search, discovery, and navigation.
- **Compose Idiomatic**: Layouts are composed top-down using standard Jetpack Compose constructs without artificial wrappers.
- **Recomposition Skipping**: With `@Immutable`, Compose skips recomposing unchanged item rows when parent screens update.
- **Zero Pollution**: No fake items (`SpacerItem`, `DividerItem`) in the contract, eliminatiing whitelisting code in providers.

### Trade-offs
- Rendering a generic list requires a `when (item)` dispatcher (or mapping table) rather than polymorphic self-drawing. However, this is standard practice in Compose and isolates UI concerns from the contract layer.
