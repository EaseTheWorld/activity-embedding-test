# ADR 0002: VHAL Data Layer Integration & Item Hierarchy Architecture

## Status
**Accepted**

## Context
In ADR 0001, we established the generic `Item<T>` contract and decoupled presentation through `ComposableItemRenderer` and `UiItem`.
Next, we needed to integrate the automotive **Data Layer (Vehicle HAL / VHAL)** via Android Automotive OS (`CarPropertyManager` / VHAL property IDs).

We faced several critical architectural questions:
1. **Diamond Hierarchy Conflict**: An item needs both UI metadata/rendering (`UiChoiceItem`) and VHAL property mapping (`VhalBoundItem`). In Kotlin/JVM, how should this inheritance be structured?
2. **Abstract Class vs Interface for `UiItem`**: Should `UiItem` be an interface or an abstract class? Why not make both interfaces, or make VHAL the class?
3. **Domain, UI, and Hardware Mapping**: How should discrete choices (e.g. Massage Mode: Domain `"WAVE"`, UI `R.string.massage_wave`, VHAL `1`) be declared without duplicating mapping code across multiple files?
4. **Ordering and Configuration in Registries**: How should the list of items in a category be ordered and filtered when using Dependency Injection (Hilt)?

---

## Decisions

### 1. The Asymmetric Diamond Resolution (Main Base Class + Capability Interface)
Rather than multiple class inheritance (which is forbidden in Kotlin/JVM), we resolved the diamond structure using the classic, time-tested pattern found in standard libraries (`ArrayList` extends `AbstractList` and implements `RandomAccess`):

```
            Item<T> (Domain Contract Interface)
           /       \
          /         \
  UiChoiceItem       VhalBoundItem<T, V> 
(abstract class)        (interface)
          \         /
           \       /
       VhalChoiceItem<V> (Bridge Abstract Class)
               ▲
               │
      SeatMassageModeItem (Concrete Item)
```

- **`UiChoiceItem`**: **`abstract class`** (UI backbone) holding backing fields (`titleRes`, `iconRes`, `options`, `optionSlot`, `_valueFlow`).
- **`VhalBoundItem<T, V>`**: **`interface`** (hardware capability) declaring `propertyId: Int`, `areaId: Int`, and bidirectional conversion functions (`toItemValue`, `toVhalValue`). Pure Kotlin with zero Android/Car framework coupling.
- **`VhalChoiceItem<V>`**: **`abstract class`** bridging UI and VHAL. Implements `toItemValue` and `toVhalValue` automatically from the options list, reducing concrete item boilerplate to a few declarative lines.

---

### 2. Rationale: Why `UiItem` is an Abstract Class while `VhalBoundItem` is an Interface

We explicitly evaluated three structural combinations:

| Structural Option | Evaluation | Decision |
| :--- | :--- | :--- |
| **`UiItem` (Class) + `VhalItem` (Interface)** | ✅ Standard "Base Backbone + Capability Interface". UI properties are common to 100% of Settings items. `super(...)` delegates backing fields once. Non-VHAL items (local prefs) simply omit `VhalItem`. | **Selected** |
| **`VhalItem` (Class) + `UiItem` (Interface)** | ❌ Fatal flaw: Non-VHAL settings (SharedPreferences, Display units, Bluetooth) cannot inherit from `VhalItem` or are forced to pass dummy property IDs (`-1`), violating LSP. | **Rejected** |
| **Both are Interfaces** | ⚠️ Purest in theory, but Kotlin interfaces cannot have constructors or backing fields. Subclasses would be forced to write `override val` on 6+ properties repeatedly, requiring a helper base class anyway. | **Rejected** (Unnecessary boilerplate) |

#### Key Principle: Constructor Delegation (`super(...)`)
With an abstract class, the concrete subclass does not declare any backing fields or `override val` keywords. It simply delegates the parameter values to the parent constructor `super(...)`:

```kotlin
// Subclass needs ZERO property declarations in its body!
class DriverSeatHeatingItem : VhalChoiceItem<Int>(
    key = "driver_seat_heat",
    titleRes = R.string.seat_item_driver_heat_title,
    propertyId = VehiclePropertyIds.HVAC_SEAT_TEMPERATURE,
    areaId = VehicleAreaSeat.SEAT_ROW_1_LEFT,
    carOptions = listOf(...),
    initialValue = "OFF"
)
```
When new properties with default values are added to `VhalChoiceItem`, existing subclasses require zero modifications, ensuring Open-Closed Principle (OCP) compliance.

---

### 3. Unified All-in-One Option Model (`CarUiOption` is-a `UiOption`)

To eliminate split mapping tables across Domain, UI, and Data layers, `CarUiOption` extends `UiOption`:

```kotlin
open class UiOption<T>(
    open val value: T,
    @get:StringRes open val labelRes: Int,
    @get:DrawableRes open val iconRes: Int? = null,
    open val badge: String? = null
)

class CarUiOption<T, V>(
    value: T,
    labelRes: Int,
    iconRes: Int? = null,
    badge: String? = null,
    val vhalValue: V // 👈 Hardware VHAL raw value
) : UiOption<T>(value, labelRes, iconRes, badge)
```

In a single line of code, the developer establishes the tri-directional mapping:
```kotlin
CarUiOption("WAVE", R.string.massage_wave, badge = "추천", vhalValue = 1)
```
- **Domain Value**: `"WAVE"`
- **UI Presentation**: `R.string.massage_wave` + optional icon/badge
- **Hardware VHAL Value**: `1`

The bridge class `VhalChoiceItem` implements bidirectional conversion automatically:
- `toItemValue(vhalValue)`: `carOptions.firstOrNull { it.vhalValue == vhalValue }?.value`
- `toVhalValue(itemValue)`: `carOptions.first { it.value == itemValue }.vhalValue`

---

### 4. Deterministic Ordering and Vehicle Configuration Filtering

Settings screens strictly require deterministic visual display order. Therefore:
- Hilt `@IntoSet` is rejected for UI registry ordering because `Set` does not guarantee iteration order.
- Registries explicitly declare ordered lists using `listOfNotNull(...)` with vehicle configuration filters:
  ```kotlin
  override val items: List<Item<*>> = listOfNotNull(
      driverSeatHeat,
      driverSeatVent.takeIf { vehicleConfig.hasVentilation },
      seatMassage.takeIf { vehicleConfig.hasMassage },
      easyEntryExit
  )
  ```

---

## Consequences

### Positive
- **Drastic Boilerplate Reduction**: Concrete items shrink from ~35 lines of repetitive `override val` and `MutableStateFlow` logic to ~10 lines of declarative specification.
- **Single Source of Truth**: Adding a choice option requires modifying exactly 1 line of code for Domain, UI, and VHAL simultaneously.
- **Zero Framework Leakage in Core**: `VhalBoundItem` uses primitive `Int`s and pure Kotlin, keeping `:core-item-contract` 100% Android-free.
- **Seamless Testability**: `toItemValue` and `toVhalValue` are unit-testable without Android Automotive OS emulator or Car Service mocks.

### Trade-offs
- An item utilizing `CarUiOption` is tightly coupling its UI text and VHAL value definition within that specific option model. (Mitigated by the fact that feature modules are the exact integration boundary where UI meets Data for that specific automotive feature).
