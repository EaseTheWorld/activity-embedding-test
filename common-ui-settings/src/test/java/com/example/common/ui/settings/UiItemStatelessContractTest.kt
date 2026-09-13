package com.example.common.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architectural Guard Test:
 * Enforces the strict separation between UI Presentation Metadata (UiItem hierarchy)
 * and Reactive State Management (ItemViewModel hierarchy).
 *
 * UiItem subclasses MUST NEVER declare valueFlow or onValueChanged.
 * All reactive state is strictly owned and driven by ItemViewModel implementations.
 */
class UiItemStatelessContractTest {

    private val pureUiItemClasses = listOf(
        UiItem::class.java,
        UiToggleItem::class.java,
        UiChoiceItem::class.java,
        UiSliderItem::class.java,
        UiActionItem::class.java,
        BaseUiToggleItem::class.java,
        BaseUiChoiceItem::class.java
    )

    @Test
    fun `UiItem hierarchy classes must NOT declare or contain valueFlow property or getter`() {
        for (clazz in pureUiItemClasses) {
            val methods = clazz.methods.map { it.name }
            val declaredFields = clazz.declaredFields.map { it.name }

            assertFalse(
                "Class ${clazz.simpleName} must NOT declare 'getValueFlow' method",
                methods.contains("getValueFlow")
            )
            assertFalse(
                "Class ${clazz.simpleName} must NOT declare 'valueFlow' field",
                declaredFields.contains("valueFlow")
            )
        }
    }

    @Test
    fun `UiItem hierarchy classes must NOT declare onValueChanged method`() {
        for (clazz in pureUiItemClasses) {
            val methods = clazz.methods.map { it.name }

            assertFalse(
                "Class ${clazz.simpleName} must NOT declare 'onValueChanged' method",
                methods.contains("onValueChanged")
            )
        }
    }

    @Test
    fun `UiItem hierarchy classes must NOT declare initialValue property or getter`() {
        for (clazz in pureUiItemClasses) {
            val methods = clazz.methods.map { it.name }
            val declaredFields = clazz.declaredFields.map { it.name }

            assertFalse(
                "Class ${clazz.simpleName} must NOT declare 'getInitialValue' method",
                methods.contains("getInitialValue")
            )
            assertFalse(
                "Class ${clazz.simpleName} must NOT declare 'initialValue' field",
                declaredFields.contains("initialValue")
            )
        }
    }

    @Test
    fun `UiToggleItem and UiChoiceItem are pure presentation metadata without initialValue`() {
        val toggle = UiToggleItem(
            id = "test_toggle",
            nameResId = 1
        )
        assertEquals("test_toggle", toggle.id)
        assertEquals(1, toggle.nameResId)

        val choice = UiChoiceItem(
            id = "test_choice",
            nameResId = 2,
            choiceOptions = listOf(UiOption("OPT_A", 10), UiOption("OPT_B", 11))
        )
        assertEquals("test_choice", choice.id)
        assertEquals(listOf("OPT_A", "OPT_B"), choice.optionIds)
    }
}
