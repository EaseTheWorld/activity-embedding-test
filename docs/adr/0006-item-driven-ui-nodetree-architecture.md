# ADR 0006: Android Compose Data-Driven UI (ItemTree) Architecture

## Status
**Accepted**

## Context
As setting interfaces expand to multi-depth hierarchies (e.g., Settings -> Bluetooth -> Advanced), conventional UI definitions encounter several friction points:
1. **Recomposition Inefficiencies**: Mutable or non-immutable tree models cause Compose compiler skip failures.
2. **Key Collision & Manual String Routes**: String route and deeplink maintenance is error-prone, requiring hardcoded strings like `"SettingItems/Bluetooth/Advanced"` or reflection.
3. **DSL vs Property Access Tension**: Building UI with pure DSLs often obscures direct compile-time property access needed by ViewModels or external deeplink callers.
4. **ItemController Mapping Fragility**: String-based mapping annotations (`@StringKey("setting_key")`) are susceptible to typos and duplicate registrations.

---

## Decisions

### 1. 100% Immutable Tree Model (`@Immutable`)
All items implement immutable sealed interfaces:
- `UiItem`: Base item contract with globally unique `key: String`.
- `UiContainerItem`: Container contract with immutable `children: List<UiItem>`.
- Leaf items (`UiToggleItem`, `UiTextItem`, `UiSubScreenEntryItem`) and containers (`UiLinearItem`) are immutable data classes. Compose compiler marks them as `Skippable`.

### 2. Zero-Reflection Route and DeepLink Key Guarantee
- **Screen Route**: Automatically derived from JVM binary class name via `this.javaClass.name.substringAfterLast('.').replace('$', '/')`. Nested Kotlin `object` hierarchies (`SettingItems.Bluetooth.Advanced`) yield compile-time route hierarchies without reflection.
- **Item Key (DeepLink)**: Property delegation (`by screenItem { ... }`) guarantees canonical paths in the format `"${screen.route}/${property.name}"`.

### 3. Separation of Declaration (`val`) and Composition (`DSL`)
- Observable setting items are declared as immutable `val` properties on screen objects.
- Visual hierarchy and layout containers (`column`, `row`, `staticText`) are composed via `@UiTreeDsl TreeBuilderScope` using the unary plus operator (`+item`).

### 4. Type-Safe Direct ItemController Multi-Binding (`Hilt @IntoSet`)
- **Non-AndroidX ViewModel**: `ItemController` is **NOT an AndroidX `ViewModel`**, but a lightweight, lifecycle-aware POJO with its own `controllerScope` (`SupervisorJob() + Dispatchers.Main.immediate`) and `onCleared()` callback.
- **Direct `item` Reference**: Each `ItemController` declares `override val item: UiItem` directly referencing its compile-time declared item (e.g., `SettingItems.Bluetooth.masterToggle`). This eliminates runtime `@AssistedInject` and separate `Factory` boilerplate. Redundant `key` property on `ItemController` is removed in favor of `controller.item.key`.
- **`Set<ItemController>` via `@IntoSet`**: Binds directly into `Set<@JvmSuppressWildcards ItemController>` using Hilt `@Binds @IntoSet`. Unlike `@IntoMap`, this avoids string literals (`@StringKey`) entirely.
- **Startup Integrity & Key Lookup**: `ItemControllerRegistry` injects `Set<ItemController>` and converts it into `Map<String, ItemController>` keyed by `controller.item.key`. Duplicate registrations are detected at startup, and items can be retrieved dynamically by `key: String` (e.g., deeplink handler) or `item: UiItem`.
- **Screen-Level Lifecycle**: `ScreenHostViewModel` (an AndroidX `ViewModel`) retains `ItemControllerRegistry` and triggers `registry.clearAll()` when the hosting screen is cleared.

```mermaid
graph TD
    subgraph TreeDeclaration["ItemScreen & Tree Hierarchy"]
        Screen["SettingItems (Route: 'SettingItems')"]
        SubScreen["SettingItems.Bluetooth (Route: 'SettingItems/Bluetooth')"]
        AdvScreen["SettingItems.Bluetooth.Advanced (Route: 'SettingItems/Bluetooth/Advanced')"]
        Screen --> SubScreen --> AdvScreen
        Toggle["masterToggle<br/>Key: 'SettingItems/Bluetooth/masterToggle'"]
        SubScreen --> Toggle
    end

    subgraph Renderer["Compose Renderer (DrawTree)"]
        Draw["DrawTree(tree) { customItem -> ... }"]
        DrawItem["DrawItem(item)"]
        LocalReg["LocalItemControllerRegistry.current"]
        Draw --> DrawItem
        DrawItem -.->|get(item)| LocalReg
    end

    subgraph ControllerManagement["Hilt Multi-Binding & Registry"]
        Hilt["Hilt Module (@Binds @IntoSet)"]
        Controller["BluetoothMasterToggleController<br/>item: SettingItems.Bluetooth.masterToggle<br/>(Lightweight POJO, not AndroidX ViewModel)"]
        Registry["ItemControllerRegistry<br/>Map&lt;String, ItemController&gt;"]
        HostVM["ScreenHostViewModel (AndroidX ViewModel)<br/>onCleared() -> registry.clearAll()"]
        Hilt -->|Set&lt;ItemController&gt;| Registry
        Controller -->|IntoSet| Hilt
        Registry --> Controller
        HostVM --> Registry
        LocalReg --> Registry
    end
```

### 5. ProGuard / R8 Safety
Added explicit ProGuard keep rule:
```proguard
-keepnames class * extends com.example.uifw.item.ItemScreen
```
Ensures obfuscation does not rewrite class names and invalidate derived routes.
