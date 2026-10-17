# ADR 0006: Android Compose Data-Driven UI (NodeTree) Architecture

## Status
**Accepted**

## Context
As setting interfaces expand to multi-depth hierarchies (e.g., Settings -> Bluetooth -> Advanced), conventional UI definitions encounter several friction points:
1. **Recomposition Inefficiencies**: Mutable or non-immutable tree models cause Compose compiler skip failures.
2. **Key Collision & Manual String Routes**: String route and deeplink maintenance is error-prone, requiring hardcoded strings like `"SettingNodes/Bluetooth/Advanced"` or reflection.
3. **DSL vs Property Access Tension**: Building UI with pure DSLs often obscures direct compile-time property access needed by ViewModels or external deeplink callers.
4. **ViewModel Mapping Fragility**: String-based mapping annotations (`@StringKey("setting_key")`) are susceptible to typos and duplicate registrations.

---

## Decisions

### 1. 100% Immutable Tree Model (`@Immutable`)
All nodes implement immutable sealed interfaces:
- `UiNode`: Base node contract with globally unique `key: String`.
- `UiContainerNode`: Container contract with immutable `children: List<UiNode>`.
- Leaf nodes (`UiToggleNode`, `UiTextNode`, `UiSubScreenEntryNode`) and containers (`UiLinearNode`) are immutable data classes. Compose compiler marks them as `Skippable`.

### 2. Zero-Reflection Route and DeepLink Key Guarantee
- **Screen Route**: Automatically derived from JVM binary class name via `this.javaClass.name.substringAfterLast('.').replace('$', '/')`. Nested Kotlin `object` hierarchies (`SettingNodes.Bluetooth.Advanced`) yield compile-time route hierarchies without reflection.
- **Node Key (DeepLink)**: Property delegation (`by screenNode { ... }`) guarantees canonical paths in the format `"${screen.route}/${property.name}"`.

### 3. Separation of Declaration (`val`) and Composition (`DSL`)
- Observable setting items are declared as immutable `val` properties on screen objects.
- Visual hierarchy and layout containers (`column`, `row`, `staticText`) are composed via `@UiTreeDsl TreeBuilderScope` using the unary plus operator (`+node`).

### 4. Type-Safe ViewModel Multi-Binding (`Hilt @IntoSet`)
- ViewModels expose standardized contracts (`ToggleController`, `TextController`).
- Factories implement `NodeViewModelFactory<out VM : NodeViewModel>`, referencing `targetNode.key` directly in code.
- Multi-bound via Dagger Hilt `@IntoSet` into `NodeViewModelRegistry`. Duplicate factory registrations are detected and rejected at initialization time.

```mermaid
graph TD
    subgraph TreeDeclaration["NodeScreen & Tree Hierarchy"]
        Screen["SettingNodes (Route: 'SettingNodes')"]
        SubScreen["SettingNodes.Bluetooth (Route: 'SettingNodes/Bluetooth')"]
        AdvScreen["SettingNodes.Bluetooth.Advanced (Route: 'SettingNodes/Bluetooth/Advanced')"]
        Screen --> SubScreen --> AdvScreen
        Toggle["masterToggle<br/>Key: 'SettingNodes/Bluetooth/masterToggle'"]
        SubScreen --> Toggle
    end

    subgraph Renderer["Compose Renderer (DrawTree)"]
        Draw["DrawTree(tree)"]
        DrawNode["DrawNode(node)"]
        LocalReg["LocalNodeViewModelRegistry.current"]
        Draw --> DrawNode
        DrawNode -.->|getOrCreate(node)| LocalReg
    end

    subgraph VMManagement["Hilt Multi-Binding & Registry"]
        Registry["NodeViewModelRegistry<br/>Map&lt;String, NodeViewModelFactory&gt;"]
        Factory["BluetoothMasterToggleViewModel.Factory<br/>targetNode: SettingNodes.Bluetooth.masterToggle"]
        VM["BluetoothMasterToggleViewModel<br/>ToggleController (isChecked, isEnabled)"]
        Registry --> Factory
        Factory --> VM
        LocalReg --> Registry
    end
```

### 5. ProGuard / R8 Safety
Added explicit ProGuard keep rule:
```proguard
-keepnames class * extends com.example.uifw.node.NodeScreen
```
Ensures obfuscation does not rewrite class names and invalidate derived routes.
