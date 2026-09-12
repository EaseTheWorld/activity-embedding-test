package com.example.core.item

/**
 * Pure, bidirectional mapping between a high-level UI Domain type [DomainT]
 * and a low-level Hardware / Storage Raw type [RawV].
 *
 * Encapsulates Value Mapping so neither the UI Item nor the Storage layer
 * needs to know about the other layer's type representation.
 */
data class ValueMapping<DomainT, RawV>(
    val toDomain: (RawV) -> DomainT,
    val toRaw: (DomainT) -> RawV
) {
    companion object {
        /** Standard mapping for Boolean domain states to integer hardware flags (1 = true, 0 = false) */
        val BooleanToInt = ValueMapping<Boolean, Int>(
            toDomain = { it == 1 },
            toRaw = { if (it) 1 else 0 }
        )

        /** Standard mapping for Boolean domain states to serialized string preferences ("true" / "false") */
        val BooleanToString = ValueMapping<Boolean, String>(
            toDomain = { it.equals("true", ignoreCase = true) },
            toRaw = { it.toString() }
        )

        /** Identity mapping when domain and raw types are identical */
        fun <T> identity() = ValueMapping<T, T>(
            toDomain = { it },
            toRaw = { it }
        )
    }
}

/**
 * Identifies a Vehicle HAL (VHAL) or hardware register property.
 */
data class HardwareKey(
    val propertyId: Int,
    val areaId: Int = 0
)

/**
 * Complete specification binding a logical Item to a Storage / Hardware source.
 * Encapsulates both:
 * 1. **Key Mapping**: [itemId] (Domain/UI) <-> [storageKey] (Hardware/Storage)
 * 2. **Value Mapping**: [valueMapping] converting [DomainT] <-> [RawV]
 *
 * @param DomainT The domain/UI state type of the item (e.g. Boolean, Int, String).
 * @param RawV The raw storage/hardware value type (e.g. Int, Float, String).
 * @param KeyT The type identifying the storage target (e.g. [HardwareKey], String preference key).
 */
data class PropertyBinding<DomainT, RawV, KeyT>(
    val itemId: String,
    val storageKey: KeyT,
    val valueMapping: ValueMapping<DomainT, RawV>
)

/**
 * Typealias specializing [PropertyBinding] for Vehicle Hardware / VHAL properties.
 */
typealias HardwareBinding<DomainT, RawV> = PropertyBinding<DomainT, RawV, HardwareKey>

/**
 * Encapsulates a hardware property identifier alongside its [ValueMapping].
 * Allows Storage to internally handle type conversion so handlers only see [DomainT].
 */
data class VehicleProperty<DomainT, RawV>(
    val propertyId: Int,
    val areaId: Int = 0,
    val mapper: ValueMapping<DomainT, RawV>
) {
    val key: HardwareKey get() = HardwareKey(propertyId, areaId)
}

/**
 * Holder associating an immutable [Item] with its reactive [ItemViewModel].
 * Eliminates raw string keys from DI/Hilt configuration.
 */
data class ItemViewModelBinding<T>(
    val item: Item,
    val viewModel: ItemViewModel<T>
) {
    val itemId: String get() = item.id
}

/**
 * Declarative infix operator for binding an [Item] to an [ItemViewModel].
 * Enables clean, table-like DSL in Hilt modules:
 * `DoorCatalog.autoDoorLock bindsTo HardwareItemViewModel(...)`
 */
infix fun <T> Item.bindsTo(viewModel: ItemViewModel<T>): ItemViewModelBinding<T> =
    ItemViewModelBinding(item = this, viewModel = viewModel)


