# ADR 0003: Item Contract Segregation (ValueItem vs ContainerItem)

## Status
**Accepted**

## Context
In ADR 0001, we formulated the generic `Item<T>` contract under the fundamental assumption that **every setting item in the vehicle represents a stateful property** with a reactive stream (`valueFlow: StateFlow<T>`), a value mutation entry point (`onValueChanged(newValue: T)`), and a serialized representation (`serializedValue: String`).

However, as the system evolved to support rich, data-driven vehicle cockpit interfaces, this assumption broke down:
1. **Composite & Spatial Layouts (`ContainerItem`)**:
   - Complex UI layouts, such as a single row hosting dual independent controls (e.g. Frunk & Trunk lighting toggles) or a 2D spatial vehicle canvas controlling 4 individual window positions, are structural grouping elements.
   - A container item aggregates child items; it does not have a single scalar state value of its own, nor can it accept a scalar `onValueChanged` event.
2. **Pure Action Triggers (`ActionItem`)**:
   - Push-button actions (e.g. "Reset Driving Stats", "Calibrate Cameras", "Factory Reset") represent trigger commands, not persistent states.
   - Forcing `ActionItem` into `Item<Unit>` introduced artificial boilerplate (`valueFlow = MutableStateFlow(Unit)`, `onValueChanged(Unit)`) violating design purity.
3. **Static Information Elements (Headers, Banners, Dividers)**:
   - Informational and decorative elements in dynamic setting lists need identity (`key`) and visibility (`isVisible`), but possess no state.
4. **Interface Segregation Principle (ISP) & Liskov Substitution Principle (LSP)**:
   - Forcing non-state elements to implement dummy `valueFlow` and no-op `onValueChanged` violated ISP and created runtime traps for IPC and VHAL synchronization layers.

---

## Decisions

### 1. Root Contract Segregation: `Item` vs `ValueItem<T>`

We segregated the root contract into two focused interfaces:

```
               Item (Root Identity & Visibility)
              /    \                  \
             /      \                  \
    ValueItem<T>   ActionItem       ContainerItem
   (Stateful)      (Command)        (Composite Group)
    /    |    \
Toggle Choice Slider
```

#### A. Root Contract (`Item`)
Free of generic type parameters and state requirements:
```kotlin
interface Item {
    val key: String
    val type: ItemType get() = ItemType.CUSTOM
    val isVisible: Flow<Boolean> get() = flowOf(true)
    val isEnabled: Flow<Boolean> get() = flowOf(true)
}
```

#### B. Stateful Contract (`ValueItem<T>`)
Encapsulates reactive observation, mutation, and serialization:
```kotlin
interface ValueItem<T> : Item {
    val valueFlow: StateFlow<T>
    fun onValueChanged(newValue: T)
    val serializedValue: String get() = valueFlow.value.toString()
}

interface ToggleItem : ValueItem<Boolean>
interface ChoiceItem : ValueItem<String>
interface SliderItem : ValueItem<Int>
```

#### C. Command Trigger Contract (`ActionItem`)
Replaces artificial `Unit` state flows with an explicit trigger:
```kotlin
interface ActionItem : Item {
    override val type: ItemType get() = ItemType.ACTION
    fun onClick()
}
```

#### D. Composite Layout Contract (`ContainerItem`)
Enables recursive composition of setting elements:
```kotlin
interface ContainerItem : Item {
    override val type: ItemType get() = ItemType.CONTAINER
    val children: List<Item> get() = emptyList()
}
```

---

### 2. Elimination of Generics at Container and UI Boundaries

Previously, heterogeneous collections throughout the architecture required existential wildcard typing: `List<Item<*>>`.

By eliminating the type parameter from `Item`, collections across `CategoryItemProvider`, `GenericSettingsScreen`, and feature registries now use clean, unified typing:
- **Before**: `val items: List<Item<*>>`
- **After**: `val items: List<Item>`

### 3. Hierarchical Resolution in `CategoryItemProvider`
`CategoryItemProvider.findItem(key: String)` now performs deep, recursive resolution across `ContainerItem.children`, allowing IPC layers and deep-link routers to address nested items transparently:
```kotlin
fun findItem(key: String): Item? {
    fun search(list: List<Item>): Item? {
        for (item in list) {
            if (item.key == key) return item
            if (item is ContainerItem) {
                val nested = search(item.children)
                if (nested != null) return nested
            }
        }
        return null
    }
    return search(items)
}
```

### 4. UI Layer Adaptation (`UiItem` and `UiValueItem<T>`)
The UI contract mirrors this segregation in `:common-ui-settings`:
- `UiItem : Item, ComposableItemRenderer`: provides `titleRes`, `subtitleRes`, `iconRes`, and `@Composable fun Draw()`.
- `UiValueItem<T> : UiItem, ValueItem<T>`: adds `getValueVisual(value: T)`.
- `UiContainerItem : ContainerItem, UiItem`: self-renders composite children.

---

## Consequences

### Positive
- **Architectural Rigor**: Strict adherence to the Interface Segregation Principle (ISP).
- **Clean Type System**: Elimination of `List<Item<*>>` wildcard cascades throughout repositories, screens, and registries.
- **Natural Action Modeling**: `ActionItem.onClick()` cleanly represents triggers without fake `StateFlow<Unit>`.
- **Composite Layout Support**: Native data-driven modeling of complex multi-item rows and spatial vehicle layouts.
- **Safe VHAL & IPC Integration**: Hardware and IPC binders safely target `ValueItem<*>` without risking runtime errors on structural or action items.

### Negative / Trade-offs
- Concrete custom value items implement `ValueItem<T>` instead of `Item<T>`.
- Existing ContentProvider query implementations use `(item as? ValueItem<*>)?.serializedValue` to handle non-state items gracefully.
