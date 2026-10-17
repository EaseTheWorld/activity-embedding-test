package com.example.uifw

import com.example.sample.BluetoothMasterToggleViewModel
import com.example.sample.DualAudioToggleViewModel
import com.example.sample.SettingNodes
import com.example.sample.WifiMasterToggleViewModel
import com.example.uifw.node.Orientation
import com.example.uifw.node.UiLinearNode
import com.example.uifw.node.UiNode
import com.example.uifw.node.UiSubScreenEntryNode
import com.example.uifw.node.UiTextNode
import com.example.uifw.node.UiToggleNode
import com.example.uifw.vm.NodeViewModel
import com.example.uifw.vm.NodeViewModelFactory
import com.example.uifw.vm.NodeViewModelRegistry
import com.example.uifw.vm.ScreenHostViewModel
import com.example.uifw.vm.ToggleController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NodeTreeArchitectureTest {

    @Test
    fun testHierarchicalRouteGenerationWithoutReflection() {
        // Zero-reflection: derived from JVM class name (replacing '$' with '/')
        assertEquals("SettingNodes", SettingNodes.route)
        assertEquals("SettingNodes/Bluetooth", SettingNodes.Bluetooth.route)
        assertEquals("SettingNodes/Bluetooth/Advanced", SettingNodes.Bluetooth.Advanced.route)
        assertEquals("SettingNodes/Wifi", SettingNodes.Wifi.route)
    }

    @Test
    fun testDeepLinkNodeKeyGenerationViaPropertyDelegation() {
        // Key format: "${screen.route}/${property.name}"
        assertEquals("SettingNodes/bluetoothEntry", SettingNodes.bluetoothEntry.key)
        assertEquals("SettingNodes/wifiEntry", SettingNodes.wifiEntry.key)

        assertEquals("SettingNodes/Bluetooth/masterToggle", SettingNodes.Bluetooth.masterToggle.key)
        assertEquals("SettingNodes/Bluetooth/advancedEntry", SettingNodes.Bluetooth.advancedEntry.key)
        assertEquals("SettingNodes/Bluetooth/Advanced/dualAudio", SettingNodes.Bluetooth.Advanced.dualAudio.key)

        assertEquals("SettingNodes/Wifi/masterToggle", SettingNodes.Wifi.masterToggle.key)

        // Collision safety: Bluetooth.masterToggle and Wifi.masterToggle share variable name but have distinct keys
        assertNotEquals(
            SettingNodes.Bluetooth.masterToggle.key,
            SettingNodes.Wifi.masterToggle.key
        )
    }

    @Test
    fun testTreeStructureAndImmutability() {
        val root = SettingNodes.tree.root as UiLinearNode
        assertEquals("SettingNodes/root", root.key)
        assertEquals(Orientation.VERTICAL, root.orientation)

        // Root has 1 child: the "main_section" column
        assertEquals(1, root.children.size)
        val mainSection = root.children[0] as UiLinearNode
        assertEquals("SettingNodes/main_section", mainSection.key)
        assertEquals(Orientation.VERTICAL, mainSection.orientation)

        // main_section has 3 children: bluetoothEntry, wifiEntry, and staticText
        assertEquals(3, mainSection.children.size)

        val child0 = mainSection.children[0] as UiSubScreenEntryNode
        assertEquals("SettingNodes/bluetoothEntry", child0.key)
        assertEquals(SettingNodes.Bluetooth, child0.targetScreen)

        val child1 = mainSection.children[1] as UiSubScreenEntryNode
        assertEquals("SettingNodes/wifiEntry", child1.key)
        assertEquals(SettingNodes.Wifi, child1.targetScreen)

        val child2 = mainSection.children[2] as UiTextNode
        assertEquals("SettingNodes/version_info", child2.key)
        assertEquals("SW Version 1.0.0", child2.text)
    }

    @Test
    fun testSubScreenTreeStructure() {
        // Bluetooth screen tree
        val btRoot = SettingNodes.Bluetooth.tree.root
        assertEquals("SettingNodes/Bluetooth/root", btRoot.key)
        val btSection = btRoot.children[0] as UiLinearNode
        assertEquals("SettingNodes/Bluetooth/bt_section", btSection.key)
        assertEquals(2, btSection.children.size)

        val btToggle = btSection.children[0] as UiToggleNode
        assertEquals("SettingNodes/Bluetooth/masterToggle", btToggle.key)
        assertEquals("블루투스 사용", btToggle.title)

        val btAdvEntry = btSection.children[1] as UiSubScreenEntryNode
        assertEquals("SettingNodes/Bluetooth/advancedEntry", btAdvEntry.key)
        assertEquals(SettingNodes.Bluetooth.Advanced, btAdvEntry.targetScreen)

        // Bluetooth Advanced screen tree
        val advRoot = SettingNodes.Bluetooth.Advanced.tree.root
        assertEquals("SettingNodes/Bluetooth/Advanced/root", advRoot.key)
        val advSection = advRoot.children[0] as UiLinearNode
        assertEquals("SettingNodes/Bluetooth/Advanced/bt_adv_section", advSection.key)
        assertEquals(1, advSection.children.size)
        val dualAudio = advSection.children[0] as UiToggleNode
        assertEquals("SettingNodes/Bluetooth/Advanced/dualAudio", dualAudio.key)
        assertEquals("듀얼 오디오", dualAudio.title)
    }

    @Test
    fun testNodeViewModelRegistryGetOrCreateAndCaching() {
        val btFactory = object : NodeViewModelFactory<BluetoothMasterToggleViewModel> {
            override val targetNode: UiNode = SettingNodes.Bluetooth.masterToggle
            override fun create(node: UiNode): BluetoothMasterToggleViewModel =
                BluetoothMasterToggleViewModel(node)
        }
        val wifiFactory = object : NodeViewModelFactory<WifiMasterToggleViewModel> {
            override val targetNode: UiNode = SettingNodes.Wifi.masterToggle
            override fun create(node: UiNode): WifiMasterToggleViewModel =
                WifiMasterToggleViewModel(node)
        }

        val registry = NodeViewModelRegistry(setOf(btFactory, wifiFactory))

        // First call creates the ViewModel
        val vm1: ToggleController = registry.getOrCreate(SettingNodes.Bluetooth.masterToggle)
        assertNotNull(vm1)
        assertTrue(vm1.isChecked.value)

        // Second call returns cached instance
        val vm2: ToggleController = registry.getOrCreate(SettingNodes.Bluetooth.masterToggle)
        assertSame("Subsequent getOrCreate calls must return the cached instance", vm1, vm2)

        // State changes persist across retrievals
        vm1.onToggleChange(false)
        assertEquals(false, vm2.isChecked.value)
    }

    @Test
    fun testNodeViewModelRegistryDuplicateDetection() {
        val factory1 = object : NodeViewModelFactory<BluetoothMasterToggleViewModel> {
            override val targetNode: UiNode = SettingNodes.Bluetooth.masterToggle
            override fun create(node: UiNode): BluetoothMasterToggleViewModel =
                BluetoothMasterToggleViewModel(node)
        }
        val factory2 = object : NodeViewModelFactory<BluetoothMasterToggleViewModel> {
            override val targetNode: UiNode = SettingNodes.Bluetooth.masterToggle
            override fun create(node: UiNode): BluetoothMasterToggleViewModel =
                BluetoothMasterToggleViewModel(node)
        }

        try {
            NodeViewModelRegistry(setOf(factory1, factory2))
            fail("Expected IllegalArgumentException for duplicate factory registration")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Error message should mention duplicate key",
                e.message!!.contains("Duplicate NodeViewModelFactory registered for key 'SettingNodes/Bluetooth/masterToggle'")
            )
        }
    }

    @Test
    fun testNodeViewModelRegistryUnregisteredNodeThrowsError() {
        val registry = NodeViewModelRegistry(emptySet())
        try {
            registry.getOrCreate<ToggleController>(SettingNodes.Bluetooth.masterToggle)
            fail("Expected IllegalStateException for unregistered node")
        } catch (e: IllegalStateException) {
            assertTrue(
                e.message!!.contains("No Factory registered for node key='SettingNodes/Bluetooth/masterToggle'")
            )
        }
    }

    @Test
    fun testNodeViewModelRegistryLifecycleClear() {
        var onClearedCalled = false
        val testNode = SettingNodes.Bluetooth.masterToggle

        class TestVm(node: UiNode) : NodeViewModel(), ToggleController {
            override val isChecked = kotlinx.coroutines.flow.MutableStateFlow(true)
            override val isEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
            override fun onToggleChange(checked: Boolean) {}
            override fun onCleared() {
                super.onCleared()
                onClearedCalled = true
            }
        }

        val factory = object : NodeViewModelFactory<TestVm> {
            override val targetNode: UiNode = testNode
            override fun create(node: UiNode): TestVm = TestVm(node)
        }

        val registry = NodeViewModelRegistry(setOf(factory))
        val vm = registry.getOrCreate<TestVm>(testNode)
        assertNotNull(vm)

        registry.clear(testNode.key)
        assertTrue("onCleared() should have been called upon clear(key)", onClearedCalled)

        // After clearing, a subsequent getOrCreate recreates a fresh instance
        val newVm = registry.getOrCreate<TestVm>(testNode)
        assertNotEquals("A new instance must be created after clear()", System.identityHashCode(vm), System.identityHashCode(newVm))
    }

    @Test
    fun testScreenHostViewModelClearAll() {
        var onClearedCount = 0
        val testNode = SettingNodes.Bluetooth.masterToggle

        class TestVm(node: UiNode) : NodeViewModel(), ToggleController {
            override val isChecked = kotlinx.coroutines.flow.MutableStateFlow(true)
            override val isEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
            override fun onToggleChange(checked: Boolean) {}
            override fun onCleared() {
                super.onCleared()
                onClearedCount++
            }
        }

        val factory = object : NodeViewModelFactory<TestVm> {
            override val targetNode: UiNode = testNode
            override fun create(node: UiNode): TestVm = TestVm(node)
        }

        val registry = NodeViewModelRegistry(setOf(factory))
        val hostVm = ScreenHostViewModel(registry)

        registry.getOrCreate<TestVm>(testNode)
        assertEquals(0, onClearedCount)

        // ScreenHostViewModel.onCleared clears the registry
        // We use reflection or sub-calling onCleared
        val method = ScreenHostViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(hostVm)

        assertEquals("All ViewModels should be cleared on ScreenHostViewModel.onCleared", 1, onClearedCount)
    }
}
