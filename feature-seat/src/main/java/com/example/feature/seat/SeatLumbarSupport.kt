package com.example.feature.seat

import com.example.core.item.ItemViewModel
import com.example.core.item.MutableItemViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ============================================================================
// Custom Domain DataType & Reactive ViewModel
// ============================================================================

/**
 * Custom Data Class representing 2D Pneumatic Lumbar Support coordinates.
 */
data class SeatLumbarSupport(
    val heightPercent: Int = 50, // 0..100% (Vertical position)
    val depthPercent: Int = 30   // 0..100% (Support firmness/extension)
) {
    fun toSerialized(): String = "$heightPercent,$depthPercent"

    companion object {
        val DEFAULT = SeatLumbarSupport(heightPercent = 50, depthPercent = 30)

        fun fromSerialized(raw: String, fallback: SeatLumbarSupport = DEFAULT): SeatLumbarSupport {
            val parts = raw.split(",")
            if (parts.size == 2) {
                val h = parts[0].trim().toIntOrNull() ?: fallback.heightPercent
                val d = parts[1].trim().toIntOrNull() ?: fallback.depthPercent
                return SeatLumbarSupport(heightPercent = h, depthPercent = d)
            }
            return fallback
        }
    }
}

/**
 * Dedicated ItemViewModel managing reactive domain state for [SeatLumbarSupportItem].
 * Owns [valueFlow] and receives user mutations via [setValue].
 * Also handles IPC serialization / deserialization roundtrips.
 */
class SeatLumbarViewModel(
    initialValue: SeatLumbarSupport = SeatLumbarSupport.DEFAULT
) : MutableItemViewModel<SeatLumbarSupport> {

    private val _valueFlow = MutableStateFlow(initialValue)
    override val valueFlow: StateFlow<SeatLumbarSupport> = _valueFlow.asStateFlow()

    override fun setValue(newValue: SeatLumbarSupport) {
        _valueFlow.value = newValue
    }

    /**
     * Backward-compatibility or direct update hook.
     */
    fun onValueChanged(newValue: SeatLumbarSupport) {
        setValue(newValue)
    }

    /**
     * Deserializes wire payload from IPC (ContentProvider) and updates state.
     */
    fun updateFromSerialized(raw: String) {
        val updated = SeatLumbarSupport.fromSerialized(raw, fallback = _valueFlow.value)
        setValue(updated)
    }
}
