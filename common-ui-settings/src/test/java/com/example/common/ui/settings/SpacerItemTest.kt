package com.example.common.ui.settings

import com.example.core.item.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacerItemTest {

    @Test
    fun `verify SpacerItem default key auto-increments and remains unique`() {
        val spacer1 = SpacerItem(16)
        val spacer2 = SpacerItem(24)

        assertTrue(spacer1.key.startsWith("spacer_"))
        assertTrue(spacer2.key.startsWith("spacer_"))
        assertNotEquals(spacer1.key, spacer2.key)
        assertEquals(16, spacer1.heightDp)
        assertEquals(24, spacer2.heightDp)
        assertEquals(0, spacer1.titleRes)
    }

    @Test
    fun `verify SpacerItem explicit key is respected`() {
        val spacer = SpacerItem(heightDp = 32, key = "custom_door_divider")
        assertEquals("custom_door_divider", spacer.key)
        assertEquals(32, spacer.heightDp)
    }

    @Test
    fun `verify withSpacer extension binds deterministic key to host item`() {
        val dummyItem = object : Item {
            override val key: String = "master_switch"
        }

        val items = dummyItem.withSpacer(heightDp = 20)
        assertEquals(2, items.size)
        assertEquals(dummyItem, items[0])

        val spacer = items[1] as SpacerItem
        assertEquals("master_switch_spacer", spacer.key)
        assertEquals(20, spacer.heightDp)
    }
}
