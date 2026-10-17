package com.example.uifw

import com.example.sample.BluetoothMasterToggleController
import com.example.sample.DualAudioToggleController
import com.example.sample.SettingItems
import com.example.sample.WifiMasterToggleController
import com.example.uifw.item.Orientation
import com.example.uifw.item.UiItem
import com.example.uifw.item.UiLinearItem
import com.example.uifw.item.UiSubScreenEntryItem
import com.example.uifw.item.UiTextItem
import com.example.uifw.item.UiToggleItem
import com.example.uifw.controller.ItemController
import com.example.uifw.controller.ItemControllerFactory
import com.example.uifw.controller.ItemControllerRegistry
import com.example.uifw.controller.ScreenHostViewModel
import com.example.uifw.controller.ToggleController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ItemTreeArchitectureTest {

    @Test
    fun testHierarchicalRouteGenerationWithoutReflection() {
        // Zero-reflection: derived from JVM class name (replacing '$' with '/')
        assertEquals("SettingItems", SettingItems.route)
        assertEquals("SettingItems/Bluetooth", SettingItems.Bluetooth.route)
        assertEquals("SettingItems/Bluetooth/Advanced", SettingItems.Bluetooth.Advanced.route)
        assertEquals("SettingItems/Wifi", SettingItems.Wifi.route)
    }

    @Test
    fun testDeepLinkNodeKeyGenerationViaPropertyDelegation() {
        // Key format: "${screen.route}/${property.name}"
        assertEquals("SettingItems/bluetoothEntry", SettingItems.bluetoothEntry.key)
        assertEquals("SettingItems/wifiEntry", SettingItems.wifiEntry.key)

        assertEquals("SettingItems/Bluetooth/masterToggle", SettingItems.Bluetooth.masterToggle.key)
        assertEquals("SettingItems/Bluetooth/advancedEntry", SettingItems.Bluetooth.advancedEntry.key)
        assertEquals("SettingItems/Bluetooth/Advanced/dualAudio", SettingItems.Bluetooth.Advanced.dualAudio.key)

        assertEquals("SettingItems/Wifi/masterToggle", SettingItems.Wifi.masterToggle.key)

        // Collision safety: Bluetooth.masterToggle and Wifi.masterToggle share variable name but have distinct keys
        assertNotEquals(
            SettingItems.Bluetooth.masterToggle.key,
            SettingItems.Wifi.masterToggle.key
        )
    }

    @Test
    fun testTreeStructureAndImmutability() {
        val root = SettingItems.tree.root as UiLinearItem
        assertEquals("SettingItems/root", root.key)
        assertEquals(Orientation.VERTICAL, root.orientation)

        // Root has 1 child: the "main_section" column
        assertEquals(1, root.children.size)
        val mainSection = root.children[0] as UiLinearItem
        assertEquals("SettingItems/main_section", mainSection.key)
        assertEquals(Orientation.VERTICAL, mainSection.orientation)

        // main_section has 3 children: bluetoothEntry, wifiEntry, and staticText
        assertEquals(3, mainSection.children.size)

        val child0 = mainSection.children[0] as UiSubScreenEntryItem
        assertEquals("SettingItems/bluetoothEntry", child0.key)
        assertEquals(SettingItems.Bluetooth, child0.targetScreen)

        val child1 = mainSection.children[1] as UiSubScreenEntryItem
        assertEquals("SettingItems/wifiEntry", child1.key)
        assertEquals(SettingItems.Wifi, child1.targetScreen)

        val child2 = mainSection.children[2] as UiTextItem
        assertEquals("SettingItems/version_info", child2.key)
        assertEquals("SW Version 1.0.0", child2.text)
    }

    @Test
    fun testSubScreenTreeStructure() {
        // Bluetooth screen tree
        val btRoot = SettingItems.Bluetooth.tree.root
        assertEquals("SettingItems/Bluetooth/root", btRoot.key)
        val btSection = btRoot.children[0] as UiLinearItem
        assertEquals("SettingItems/Bluetooth/bt_section", btSection.key)
        assertEquals(2, btSection.children.size)

        val btToggle = btSection.children[0] as UiToggleItem
        assertEquals("SettingItems/Bluetooth/masterToggle", btToggle.key)
        assertEquals("블루투스 사용", btToggle.title)

        val btAdvEntry = btSection.children[1] as UiSubScreenEntryItem
        assertEquals("SettingItems/Bluetooth/advancedEntry", btAdvEntry.key)
        assertEquals(SettingItems.Bluetooth.Advanced, btAdvEntry.targetScreen)

        // Bluetooth Advanced screen tree
        val advRoot = SettingItems.Bluetooth.Advanced.tree.root
        assertEquals("SettingItems/Bluetooth/Advanced/root", advRoot.key)
        val advSection = advRoot.children[0] as UiLinearItem
        assertEquals("SettingItems/Bluetooth/Advanced/bt_adv_section", advSection.key)
        assertEquals(1, advSection.children.size)
        val dualAudio = advSection.children[0] as UiToggleItem
        assertEquals("SettingItems/Bluetooth/Advanced/dualAudio", dualAudio.key)
        assertEquals("듀얼 오디오", dualAudio.title)
    }

    @Test
    fun testItemControllerRegistryGetOrCreateAndCaching() {
        val btFactory = object : ItemControllerFactory<BluetoothMasterToggleController> {
            override val targetItem: UiItem = SettingItems.Bluetooth.masterToggle
            override fun create(item: UiItem): BluetoothMasterToggleController =
                BluetoothMasterToggleController(item)
        }
        val wifiFactory = object : ItemControllerFactory<WifiMasterToggleController> {
            override val targetItem: UiItem = SettingItems.Wifi.masterToggle
            override fun create(item: UiItem): WifiMasterToggleController =
                WifiMasterToggleController(item)
        }

        val registry = ItemControllerRegistry(setOf(btFactory, wifiFactory))

        // First call creates the Controller
        val vm1: ToggleController = registry.getOrCreate(SettingItems.Bluetooth.masterToggle)
        assertNotNull(vm1)
        assertTrue(vm1.isChecked.value)

        // Second call returns cached instance
        val vm2: ToggleController = registry.getOrCreate(SettingItems.Bluetooth.masterToggle)
        assertSame("Subsequent getOrCreate calls must return the cached instance", vm1, vm2)

        // State changes persist across retrievals
        vm1.onToggleChange(false)
        assertEquals(false, vm2.isChecked.value)
    }

    @Test
    fun testItemControllerRegistryDuplicateDetection() {
        val factory1 = object : ItemControllerFactory<BluetoothMasterToggleController> {
            override val targetItem: UiItem = SettingItems.Bluetooth.masterToggle
            override fun create(item: UiItem): BluetoothMasterToggleController =
                BluetoothMasterToggleController(item)
        }
        val factory2 = object : ItemControllerFactory<BluetoothMasterToggleController> {
            override val targetItem: UiItem = SettingItems.Bluetooth.masterToggle
            override fun create(item: UiItem): BluetoothMasterToggleController =
                BluetoothMasterToggleController(item)
        }

        try {
            ItemControllerRegistry(setOf(factory1, factory2))
            fail("Expected IllegalArgumentException for duplicate factory registration")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Error message should mention duplicate key",
                e.message!!.contains("Duplicate ItemControllerFactory registered for key 'SettingItems/Bluetooth/masterToggle'")
            )
        }
    }

    @Test
    fun testItemControllerRegistryUnregisteredItemThrowsError() {
        val registry = ItemControllerRegistry(emptySet())
        try {
            registry.getOrCreate<ToggleController>(SettingItems.Bluetooth.masterToggle)
            fail("Expected IllegalStateException for unregistered item")
        } catch (e: IllegalStateException) {
            assertTrue(
                e.message!!.contains("No Factory registered for item key='SettingItems/Bluetooth/masterToggle'")
            )
        }
    }

    @Test
    fun testItemControllerRegistryLifecycleClear() {
        var onClearedCalled = false
        val testItem = SettingItems.Bluetooth.masterToggle

        class TestController(item: UiItem) : ItemController(), ToggleController {
            override val isChecked = kotlinx.coroutines.flow.MutableStateFlow(true)
            override val isEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
            override fun onToggleChange(checked: Boolean) {}
            override fun onCleared() {
                super.onCleared()
                onClearedCalled = true
            }
        }

        val factory = object : ItemControllerFactory<TestController> {
            override val targetItem: UiItem = testItem
            override fun create(item: UiItem): TestController = TestController(item)
        }

        val registry = ItemControllerRegistry(setOf(factory))
        val ctrl = registry.getOrCreate<TestController>(testItem)
        assertNotNull(ctrl)

        registry.clear(testItem.key)
        assertTrue("onCleared() should have been called upon clear(key)", onClearedCalled)

        // After clearing, a subsequent getOrCreate recreates a fresh instance
        val newCtrl = registry.getOrCreate<TestController>(testItem)
        assertNotEquals("A new instance must be created after clear()", System.identityHashCode(ctrl), System.identityHashCode(newCtrl))
    }

    @Test
    fun testScreenHostViewModelClearAll() {
        var onClearedCount = 0
        val testItem = SettingItems.Bluetooth.masterToggle

        class TestController(item: UiItem) : ItemController(), ToggleController {
            override val isChecked = kotlinx.coroutines.flow.MutableStateFlow(true)
            override val isEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
            override fun onToggleChange(checked: Boolean) {}
            override fun onCleared() {
                super.onCleared()
                onClearedCount++
            }
        }

        val factory = object : ItemControllerFactory<TestController> {
            override val targetItem: UiItem = testItem
            override fun create(item: UiItem): TestController = TestController(item)
        }

        val registry = ItemControllerRegistry(setOf(factory))
        val hostVm = ScreenHostViewModel(registry)

        registry.getOrCreate<TestController>(testItem)
        assertEquals(0, onClearedCount)

        // ScreenHostViewModel.onCleared clears the registry
        val method = ScreenHostViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(hostVm)

        assertEquals("All Controllers should be cleared on ScreenHostViewModel.onCleared", 1, onClearedCount)
    }
}
