package com.example.common.ui.settings

import android.content.Intent
import com.example.core.item.ItemType
import com.example.core.item.MutableChoiceItemViewModel
import com.example.core.item.MutableItemViewModel
import com.example.core.item.ParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCapabilityProviderTest {

    @Test
    fun `UiToggleItem generates automatic boolean ParameterSpec and preserves keywords`() {
        val toggle = UiToggleItem(
            id = "child_lock",
            nameResId = 101,
            keywords = listOf("차일드락", "어린이보호", "아동잠금", "child lock")
        )

        val cap = toggle.capability
        assertEquals("child_lock", cap.itemId)
        assertEquals(ItemType.TOGGLE, cap.actionType)
        assertEquals(listOf("차일드락", "어린이보호", "아동잠금", "child lock"), cap.keywords)

        val params = cap.acceptedParameters
        assertEquals(1, params.size)
        assertEquals("value", params[0].name)
        assertEquals("boolean", params[0].type)
        assertEquals(listOf("true", "false"), params[0].options)
        assertTrue(params[0].isRequired)
    }

    @Test
    fun `UiChoiceItem generates automatic choice ParameterSpec with optionIds`() {
        val choice = UiChoiceItem(
            id = "driver_seat_heat",
            nameResId = 201,
            options = listOf(
                UiOption("OFF", 1),
                UiOption("LEVEL 1", 2),
                UiOption("LEVEL 2", 3),
                UiOption("LEVEL 3", 4)
            ),
            keywords = listOf("운전석열선", "엉뜨", "시트히터")
        )

        val cap = choice.capability
        assertEquals("driver_seat_heat", cap.itemId)
        assertEquals(ItemType.CHOICE, cap.actionType)
        assertEquals(listOf("운전석열선", "엉뜨", "시트히터"), cap.keywords)

        val params = cap.acceptedParameters
        assertEquals(1, params.size)
        assertEquals("value", params[0].name)
        assertEquals("choice", params[0].type)
        assertEquals(listOf("OFF", "LEVEL 1", "LEVEL 2", "LEVEL 3"), params[0].options)
        assertTrue(params[0].isRequired)
    }

    @Test
    fun `UiSliderItem generates automatic int ParameterSpec with min and max bounds`() {
        val slider = UiSliderItem(
            id = "display_brightness",
            nameResId = 301,
            min = 10,
            max = 100,
            keywords = listOf("화면밝기", "디스플레이밝기")
        )

        val cap = slider.capability
        assertEquals("display_brightness", cap.itemId)
        assertEquals(ItemType.SLIDER, cap.actionType)

        val params = cap.acceptedParameters
        assertEquals(1, params.size)
        assertEquals("value", params[0].name)
        assertEquals("int", params[0].type)
        assertEquals(10, params[0].min)
        assertEquals(100, params[0].max)
        assertTrue(params[0].isRequired)
    }

    @Test
    fun `ParameterSpec round-trip JSON serialization and parsing preserves all fields`() {
        val originalSpecs = listOf(
            ParameterSpec(
                name = "height",
                type = "int",
                min = 0,
                max = 100,
                isRequired = true,
                description = "Vertical height percentage"
            ),
            ParameterSpec(
                name = "depth",
                type = "int",
                min = 0,
                max = 100,
                isRequired = false,
                description = "Firmness depth percentage"
            ),
            ParameterSpec(
                name = "mode",
                type = "choice",
                options = listOf("SOFT", "MEDIUM", "FIRM")
            )
        )

        val json = "[" + originalSpecs.joinToString(",") { it.toJson() } + "]"
        val parsed = ParameterSpec.parseList(json)

        assertEquals(3, parsed.size)

        assertEquals("height", parsed[0].name)
        assertEquals("int", parsed[0].type)
        assertEquals(0, parsed[0].min)
        assertEquals(100, parsed[0].max)
        assertTrue(parsed[0].isRequired)
        assertEquals("Vertical height percentage", parsed[0].description)

        assertEquals("depth", parsed[1].name)
        assertEquals("int", parsed[1].type)
        assertEquals(0, parsed[1].min)
        assertEquals(100, parsed[1].max)
        assertEquals(false, parsed[1].isRequired)

        assertEquals("mode", parsed[2].name)
        assertEquals("choice", parsed[2].type)
        assertEquals(listOf("SOFT", "MEDIUM", "FIRM"), parsed[2].options)
    }

    @Test
    fun `VoiceAssistantClient accurately matches natural voice utterances and extracts parameters`() {
        val client = VoiceAssistantClient()

        val childLockItem = VoiceDiscoveredItem(
            itemKey = "child_lock",
            title = "Child Safety Lock",
            subtitle = "Prevent rear door opening",
            itemType = "TOGGLE",
            currentValue = "false",
            parameters = listOf(
                ParameterSpec(name = "value", type = "boolean", options = listOf("true", "false"), isRequired = true)
            ),
            keywords = listOf("차일드락", "어린이보호", "아동잠금", "child lock"),
            targetAction = "com.example.carsettings.door.OPEN",
            targetActivity = "com.example.lifecycleapp.GenericSettingsActivity",
            authority = "com.example.carsettings.provider.door"
        )

        val seatHeatItem = VoiceDiscoveredItem(
            itemKey = "driver_seat_heat",
            title = "Driver Seat Heating",
            subtitle = "Seat warmth",
            itemType = "CHOICE",
            currentValue = "OFF",
            parameters = listOf(
                ParameterSpec(name = "value", type = "choice", options = listOf("OFF", "LEVEL 1", "LEVEL 2", "LEVEL 3"), isRequired = true)
            ),
            keywords = listOf("운전석열선", "엉뜨", "시트히터", "운전석시트열선"),
            targetAction = "com.example.carsettings.seat.OPEN",
            targetActivity = "com.example.lifecycleapp.GenericSettingsActivity",
            authority = "com.example.carsettings.provider.seat"
        )

        val lumbarItem = VoiceDiscoveredItem(
            itemKey = "driver_seat_lumbar",
            title = "Lumbar Support",
            subtitle = "Pneumatic 2D cushion",
            itemType = "CUSTOM",
            currentValue = "50,30",
            parameters = listOf(
                ParameterSpec(name = "height", type = "int", min = 0, max = 100),
                ParameterSpec(name = "depth", type = "int", min = 0, max = 100)
            ),
            keywords = listOf("요추", "럼버서포트", "허리받침"),
            targetAction = "com.example.carsettings.seat.OPEN",
            targetActivity = "com.example.lifecycleapp.GenericSettingsActivity",
            authority = "com.example.carsettings.provider.seat"
        )

        client.registerDiscoveredItems(listOf(childLockItem, seatHeatItem, lumbarItem))

        // 1. Toggle ON match
        val resToggleOn = client.resolveVoiceCommand("차일드락 켜줘")
        assertNotNull(resToggleOn)
        assertEquals("child_lock", resToggleOn!!.targetItem.itemKey)
        assertEquals("true", resToggleOn.matchedParameters["value"])

        // 2. Toggle OFF match
        val resToggleOff = client.resolveVoiceCommand("어린이보호 잠금 해제해줘")
        assertNotNull(resToggleOff)
        assertEquals("child_lock", resToggleOff!!.targetItem.itemKey)
        assertEquals("false", resToggleOff.matchedParameters["value"])

        // 3. Choice Level match ("엉뜨 2단")
        val resChoiceLevel = client.resolveVoiceCommand("운전석 엉뜨 2단으로 틀어줘")
        assertNotNull(resChoiceLevel)
        assertEquals("driver_seat_heat", resChoiceLevel!!.targetItem.itemKey)
        assertEquals("LEVEL 2", resChoiceLevel.matchedParameters["value"])

        // 4. Choice OFF match
        val resChoiceOff = client.resolveVoiceCommand("운전석 열선 꺼줘")
        assertNotNull(resChoiceOff)
        assertEquals("driver_seat_heat", resChoiceOff!!.targetItem.itemKey)
        assertEquals("OFF", resChoiceOff.matchedParameters["value"])

        // 5. Multi-parameter match ("높이 80 깊이 45")
        val resLumbar = client.resolveVoiceCommand("요추 받침대 높이 80 깊이 45로 조절해줘")
        assertNotNull(resLumbar)
        assertEquals("driver_seat_lumbar", resLumbar!!.targetItem.itemKey)
        assertEquals("80", resLumbar.matchedParameters["height"])
        assertEquals("45", resLumbar.matchedParameters["depth"])

        // 6. Deep link parameters verification
        val deepLinkParams = client.createDeepLinkParameters(resLumbar)
        assertEquals("driver_seat_lumbar", deepLinkParams["target_item"])
        assertEquals("80", deepLinkParams["height"])
        assertEquals("45", deepLinkParams["depth"])
        assertEquals("com.example.carsettings.seat.OPEN", resLumbar.targetItem.targetAction)
    }

    @Test
    fun `Extracted voice parameters apply directly to MutableItemViewModel with zero intermediate knowledge`() {
        // Mock multi-parameter ViewModel
        data class SeatDimensions(val height: Int = 50, val depth: Int = 30)

        class MockLumbarViewModel : MutableItemViewModel<SeatDimensions> {
            private val _flow = MutableStateFlow(SeatDimensions())
            override val valueFlow: StateFlow<SeatDimensions> = _flow.asStateFlow()

            override fun setValue(newValue: SeatDimensions) {
                _flow.value = newValue
            }

            override fun updateFromParameters(parameters: Map<String, String>): Boolean {
                val h = parameters["height"]?.toIntOrNull()
                val d = parameters["depth"]?.toIntOrNull()
                if (h != null && d != null) {
                    setValue(SeatDimensions(height = h, depth = d))
                    return true
                }
                return false
            }
        }

        val vm = MockLumbarViewModel()
        val voiceParams = mapOf("height" to "75", "depth" to "60")

        val applied = vm.updateFromParameters(voiceParams)
        assertTrue(applied)
        assertEquals(75, vm.valueFlow.value.height)
        assertEquals(60, vm.valueFlow.value.depth)
    }
}
