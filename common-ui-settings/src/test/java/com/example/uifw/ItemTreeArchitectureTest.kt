package com.example.uifw

import com.example.sample.BluetoothMasterToggleController
import com.example.sample.DualAudioToggleController
import com.example.sample.SettingItems
import com.example.sample.WifiMasterToggleController
import com.example.uifw.controller.ItemController
import com.example.uifw.controller.ItemControllerRegistry
import com.example.uifw.controller.ScreenHostViewModel
import com.example.uifw.controller.ToggleController
import com.example.uifw.item.*
import com.example.uifw.search.ItemSearchIndexer
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
    fun testSectionDefinitionKeyAndTitleGeneration() {
        // Section keys format: "${screen.route}/${property.name}"
        assertEquals("SettingItems/mainSection", SettingItems.mainSection.key)
        assertEquals("주요 설정", SettingItems.mainSection.title)

        assertEquals("SettingItems/Bluetooth/connectionSection", SettingItems.Bluetooth.connectionSection.key)
        assertEquals("기본 연결", SettingItems.Bluetooth.connectionSection.title)

        assertEquals("SettingItems/Bluetooth/Advanced/audioSection", SettingItems.Bluetooth.Advanced.audioSection.key)
        assertEquals("오디오 고급 설정", SettingItems.Bluetooth.Advanced.audioSection.title)

        assertEquals("SettingItems/Wifi/wifiSection", SettingItems.Wifi.wifiSection.key)
        assertEquals("무선 네트워크", SettingItems.Wifi.wifiSection.title)
    }

    @Test
    fun testDeepLinkItemKeyGenerationViaPropertyDelegation() {
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
    fun testTreeStructureWithSectionsAndImmutability() {
        val root = SettingItems.tree.root as UiLinearItem
        assertEquals("SettingItems/root", root.key)
        assertEquals(Orientation.VERTICAL, root.orientation)

        // Root has 1 child: the "mainSection" UiSectionItem
        assertEquals(1, root.children.size)
        val mainSection = root.children[0] as UiSectionItem
        assertEquals("SettingItems/mainSection", mainSection.key)
        assertEquals("주요 설정", mainSection.title)

        // mainSection has 3 children: bluetoothEntry, wifiEntry, and staticText
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
    fun testSubScreenTreeStructureWithSections() {
        // Bluetooth screen tree: Root -> UiSectionItem(connectionSection) -> [masterToggle, advancedEntry]
        val btRoot = SettingItems.Bluetooth.tree.root
        assertEquals("SettingItems/Bluetooth/root", btRoot.key)
        val btSection = btRoot.children[0] as UiSectionItem
        assertEquals("SettingItems/Bluetooth/connectionSection", btSection.key)
        assertEquals("기본 연결", btSection.title)
        assertEquals(2, btSection.children.size)

        val btToggle = btSection.children[0] as UiToggleItem
        assertEquals("SettingItems/Bluetooth/masterToggle", btToggle.key)
        assertEquals("블루투스 사용", btToggle.title)

        val btAdvEntry = btSection.children[1] as UiSubScreenEntryItem
        assertEquals("SettingItems/Bluetooth/advancedEntry", btAdvEntry.key)
        assertEquals(SettingItems.Bluetooth.Advanced, btAdvEntry.targetScreen)

        // Bluetooth Advanced screen tree (Depth 2 subscreen with Section)
        val advRoot = SettingItems.Bluetooth.Advanced.tree.root as UiLinearItem
        assertEquals("SettingItems/Bluetooth/Advanced/root", advRoot.key)
        val audioSec = advRoot.children[0] as UiSectionItem
        assertEquals("SettingItems/Bluetooth/Advanced/audioSection", audioSec.key)
        assertEquals("오디오 고급 설정", audioSec.title)
        assertEquals(3, audioSec.children.size)

        val dualAudio = audioSec.children[0] as UiToggleItem
        assertEquals("SettingItems/Bluetooth/Advanced/dualAudio", dualAudio.key)
        assertEquals("듀얼 오디오", dualAudio.title)

        val latencyVis = audioSec.children[1] as UiCustomItem
        assertEquals("SettingItems/Bluetooth/Advanced/latencyVisualizer", latencyVis.key)
        assertEquals("오디오 레이턴시 시각화", latencyVis.title)

        val audioCodec = audioSec.children[2] as UiTextItem
        assertEquals("SettingItems/Bluetooth/Advanced/audio_codec", audioCodec.key)
        assertEquals("Audio Codec: LDAC / aptX HD", audioCodec.text)
    }

    @Test
    fun testSearchIndexerBreadcrumbsAndHierarchy() {
        val index = ItemSearchIndexer.indexTree(SettingItems)
        assertTrue("Index should contain multiple entries across screens and sections", index.size >= 8)

        // 1. Verify Section Entry and Breadcrumb
        val connSectionEntry = index.find { it.targetKey == "SettingItems/Bluetooth/connectionSection" }
        assertNotNull(connSectionEntry)
        assertTrue(connSectionEntry!!.isSection)
        assertEquals("기본 연결", connSectionEntry.title)
        assertEquals("SettingItems/Bluetooth", connSectionEntry.screenRoute)
        assertEquals(
            "환경 설정 > 블루투스 설정 > 기본 연결",
            connSectionEntry.formattedBreadcrumb
        )

        // 2. Verify Item Entry and Breadcrumb: Screen > Subscreen > Section > Item
        val btToggleEntry = index.find { it.targetKey == "SettingItems/Bluetooth/masterToggle" }
        assertNotNull(btToggleEntry)
        assertEquals(false, btToggleEntry!!.isSection)
        assertEquals("블루투스 사용", btToggleEntry.title)
        assertEquals("SettingItems/Bluetooth", btToggleEntry.screenRoute)
        assertEquals(
            "환경 설정 > 블루투스 설정 > 기본 연결 > 블루투스 사용",
            btToggleEntry.formattedBreadcrumb
        )

        // 3. Verify Subscreen-Subscreen Item: Screen > Subscreen > Subscreen > Section > Item
        val dualAudioEntry = index.find { it.targetKey == "SettingItems/Bluetooth/Advanced/dualAudio" }
        assertNotNull(dualAudioEntry)
        assertEquals("듀얼 오디오", dualAudioEntry!!.title)
        assertEquals("SettingItems/Bluetooth/Advanced", dualAudioEntry.screenRoute)
        assertEquals(
            "환경 설정 > 블루투스 설정 > 블루투스 고급 설정 > 오디오 고급 설정 > 듀얼 오디오",
            dualAudioEntry.formattedBreadcrumb
        )
    }

    @Test
    fun testSearchFilteringBySectionAndItemTitle() {
        val index = ItemSearchIndexer.indexTree(SettingItems)

        // Search by Section Title: "기본 연결"
        val sectionResults = ItemSearchIndexer.search(index, "기본 연결")
        assertEquals(1, sectionResults.size)
        assertEquals("기본 연결", sectionResults[0].title)
        assertTrue(sectionResults[0].isSection)
        assertEquals("SettingItems/Bluetooth/connectionSection", sectionResults[0].targetKey)

        // Search by Item Title: "듀얼 오디오"
        val itemResults = ItemSearchIndexer.search(index, "듀얼 오디오")
        assertEquals(1, itemResults.size)
        assertEquals("듀얼 오디오", itemResults[0].title)
        assertEquals(false, itemResults[0].isSection)
        assertEquals("SettingItems/Bluetooth/Advanced/dualAudio", itemResults[0].targetKey)
        assertEquals(
            "환경 설정 > 블루투스 설정 > 블루투스 고급 설정 > 오디오 고급 설정 > 듀얼 오디오",
            itemResults[0].formattedBreadcrumb
        )

        // Search by partial query: "오디오"
        val audioResults = ItemSearchIndexer.search(index, "오디오")
        assertTrue("Should match audioSection and dualAudio", audioResults.size >= 2)
    }

    @Test
    fun testItemControllerRegistryGetOrCreateAndCaching() {
        val btController = BluetoothMasterToggleController()
        val wifiController = WifiMasterToggleController()

        val registry = ItemControllerRegistry(setOf(btController, wifiController))

        // First call returns the Controller
        val vm1: ToggleController = registry.get(SettingItems.Bluetooth.masterToggle)
        assertNotNull(vm1)
        assertTrue(vm1.isChecked.value)

        // Second call returns the same instance
        val vm2: ToggleController = registry.get(SettingItems.Bluetooth.masterToggle)
        assertSame("Subsequent get calls must return the same instance", vm1, vm2)

        // State changes persist across retrievals
        vm1.onToggleChange(false)
        assertEquals(false, vm2.isChecked.value)
    }

    @Test
    fun testItemControllerRegistryDuplicateDetection() {
        val ctrl1 = BluetoothMasterToggleController()
        val ctrl2 = BluetoothMasterToggleController()

        try {
            ItemControllerRegistry(setOf(ctrl1, ctrl2))
            fail("Expected IllegalArgumentException for duplicate controller registration")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Error message should mention duplicate key",
                e.message!!.contains("Duplicate ItemController registered for key 'SettingItems/Bluetooth/masterToggle'")
            )
        }
    }

    @Test
    fun testItemControllerRegistryUnregisteredItemThrowsError() {
        val registry = ItemControllerRegistry(emptySet())
        try {
            registry.get<ToggleController>(SettingItems.Bluetooth.masterToggle)
            fail("Expected IllegalStateException for unregistered item")
        } catch (e: IllegalStateException) {
            assertTrue(
                e.message!!.contains("No ItemController registered for item key='SettingItems/Bluetooth/masterToggle'")
            )
        }
    }

    @Test
    fun testItemControllerRegistryLifecycleClear() {
        var onClearedCalled = false
        val testItem = SettingItems.Bluetooth.masterToggle

        class TestController : ItemController(), ToggleController {
            override val item: UiItem = testItem
            override val isChecked = kotlinx.coroutines.flow.MutableStateFlow(true)
            override val isEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
            override fun onToggleChange(checked: Boolean) {}
            override fun onCleared() {
                super.onCleared()
                onClearedCalled = true
            }
        }

        val controller = TestController()
        val registry = ItemControllerRegistry(setOf(controller))
        val ctrl = registry.get<TestController>(testItem)
        assertNotNull(ctrl)

        registry.clear(testItem.key)
        assertTrue("onCleared() should have been called upon clear(key)", onClearedCalled)
    }

    @Test
    fun testScreenHostViewModelClearAll() {
        var onClearedCount = 0
        val testItem = SettingItems.Bluetooth.masterToggle

        class TestController : ItemController(), ToggleController {
            override val item: UiItem = testItem
            override val isChecked = kotlinx.coroutines.flow.MutableStateFlow(true)
            override val isEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
            override fun onToggleChange(checked: Boolean) {}
            override fun onCleared() {
                super.onCleared()
                onClearedCount++
            }
        }

        val controller = TestController()
        val registry = ItemControllerRegistry(setOf(controller))
        val hostVm = ScreenHostViewModel(registry)

        registry.get<TestController>(testItem)
        assertEquals(0, onClearedCount)

        // ScreenHostViewModel.onCleared clears the registry
        val method = ScreenHostViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(hostVm)

        assertEquals("All Controllers should be cleared on ScreenHostViewModel.onCleared", 1, onClearedCount)
    }

    @Test
    fun testKeyBasedControllerAndItemLookupInRegistry() {
        val btController = BluetoothMasterToggleController()
        val registry = ItemControllerRegistry(setOf(btController))
        val key = "SettingItems/Bluetooth/masterToggle"

        // 1. Check controller existence
        assertTrue(registry.hasController(key))
        assertEquals(false, registry.hasController("NonExistent/key"))

        // 2. Find item directly from key string
        val itemFromKey = registry.findItem(key)
        assertNotNull(itemFromKey)
        assertSame(SettingItems.Bluetooth.masterToggle, itemFromKey)

        // 3. getOrNull returns controller instance if registered, null if not
        val activeCtrl: ToggleController? = registry.getOrNull(key)
        assertNotNull(activeCtrl)
        assertSame(btController, activeCtrl)

        val nonExistent: ToggleController? = registry.getOrNull("NonExistent/key")
        assertEquals(null, nonExistent)

        // 4. get / getOrCreate via Key string returns instance
        val ctrlFromKey: ToggleController = registry.get(key)
        assertNotNull(ctrlFromKey)
        assertTrue(ctrlFromKey.isChecked.value)

        // 5. get(item) and get(key) return the exact same instance
        val ctrlFromItem: ToggleController = registry.get(SettingItems.Bluetooth.masterToggle)
        assertSame(ctrlFromKey, ctrlFromItem)

        // 6. State changes persist across key lookups
        ctrlFromKey.onToggleChange(false)
        val ctrlRefetched: ToggleController = registry.get(key)
        assertEquals(false, ctrlRefetched.isChecked.value)
    }

    @Test
    fun testTreeAndScreenKeyLookupAndGlobalKeyMap() {
        // 1. Local Tree search
        val localItem = SettingItems.tree.findItem("SettingItems/bluetoothEntry")
        assertNotNull(localItem)
        assertSame(SettingItems.bluetoothEntry, localItem)

        // Local tree should NOT find subscreen child directly
        val notInLocalTree = SettingItems.tree.findItem("SettingItems/Bluetooth/masterToggle")
        assertEquals(null, notInLocalTree)

        // 2. Recursive Screen search finds items at any depth
        val rootSection = SettingItems.findItemRecursively("SettingItems/mainSection")
        assertNotNull(rootSection)
        assertTrue(rootSection is UiSectionItem)

        val depth1Item = SettingItems.findItemRecursively("SettingItems/Bluetooth/masterToggle")
        assertNotNull(depth1Item)
        assertSame(SettingItems.Bluetooth.masterToggle, depth1Item)

        val depth2Item = SettingItems.findItemRecursively("SettingItems/Bluetooth/Advanced/dualAudio")
        assertNotNull(depth2Item)
        assertSame(SettingItems.Bluetooth.Advanced.dualAudio, depth2Item)

        val depth2Section = SettingItems.findItemRecursively("SettingItems/Bluetooth/Advanced/audioSection")
        assertNotNull(depth2Section)
        assertEquals("오디오 고급 설정", (depth2Section as UiSectionItem).title)

        // 3. Global Key Map
        val globalMap = SettingItems.buildGlobalKeyMap()
        assertTrue("Global map should index all items across screens", globalMap.size >= 8)
        assertSame(SettingItems.Bluetooth.masterToggle, globalMap["SettingItems/Bluetooth/masterToggle"])
        assertSame(SettingItems.Bluetooth.Advanced.dualAudio, globalMap["SettingItems/Bluetooth/Advanced/dualAudio"])
        assertSame(SettingItems.Wifi.masterToggle, globalMap["SettingItems/Wifi/masterToggle"])
    }

    @Test
    fun testCustomItemSubclassHierarchyAndSearch() {
        val latencyItem = SettingItems.Bluetooth.Advanced.latencyVisualizer
        assertTrue("Must implement UiCustomItem", latencyItem is UiCustomItem)
        assertEquals("SettingItems/Bluetooth/Advanced/latencyVisualizer", latencyItem.key)
        assertEquals("오디오 레이턴시 시각화", latencyItem.title)

        // Recursive tree lookup
        val found = SettingItems.findItemRecursively("SettingItems/Bluetooth/Advanced/latencyVisualizer")
        assertNotNull(found)
        assertSame(latencyItem, found)

        // Search Indexer includes custom item with full breadcrumb
        val index = ItemSearchIndexer.indexTree(SettingItems)
        val customEntry = index.find { it.targetKey == latencyItem.key }
        assertNotNull("Custom item should be indexed", customEntry)
        assertEquals("오디오 레이턴시 시각화", customEntry!!.title)
        assertEquals(
            "환경 설정 > 블루투스 설정 > 블루투스 고급 설정 > 오디오 고급 설정 > 오디오 레이턴시 시각화",
            customEntry.formattedBreadcrumb
        )

        // Search filtering by custom item title
        val searchResults = ItemSearchIndexer.search(index, "레이턴시")
        assertEquals(1, searchResults.size)
        assertEquals(latencyItem.key, searchResults[0].targetKey)
    }

    // ── Class-level test screens for explicitKey / explicitRoute ──
    object ExplicitKeyTestScreen : ItemScreen(title = "테스트 화면") {
        // 1. explicitKey 미지정 -> 프로퍼티명 사용
        val defaultToggle by toggle(title = "기본 토글")

        // 2. 상대 explicitKey -> "${screen.route}/$explicitKey"
        val renamedToggle by toggle(title = "이름 변경 토글", explicitKey = "original_toggle_key")

        // 3. 슬래시 포함 절대 explicitKey -> 지정된 전체 경로 그대로 사용
        val legacyItem by text("레거시 아이템", explicitKey = "legacy/system/old_item")

        // 4. 선행 슬래시 포함 -> 슬래시 제거 후 절대 경로로 사용
        val slashItem by text("슬래시 아이템", explicitKey = "/absolute/custom_key")

        // 5. Section with explicitKey
        val customSection by section(title = "커스텀 섹션", explicitKey = "my_section")

        // 6. subScreenEntry with explicitKey
        val customEntry by subScreenEntry(target = SettingItems.Bluetooth, explicitKey = "bt_entry")

        override val tree = buildTree {
            customSection {
                +defaultToggle
                +renamedToggle
                +legacyItem
                +slashItem
                +customEntry
            }
        }
    }

    object CustomRouteScreen : ItemScreen(title = "커스텀 라우트 화면", explicitRoute = "Settings/Custom") {
        val masterSwitch by toggle(title = "스위치", explicitKey = "master")
        val defaultItem by text("텍스트")

        override val tree = buildTree {
            +masterSwitch
            +defaultItem
        }
    }

    @Test
    fun testExplicitKeyRelativeAndAbsoluteResolution() {
        assertEquals("ItemTreeArchitectureTest/ExplicitKeyTestScreen/defaultToggle", ExplicitKeyTestScreen.defaultToggle.key)
        assertEquals("ItemTreeArchitectureTest/ExplicitKeyTestScreen/original_toggle_key", ExplicitKeyTestScreen.renamedToggle.key)
        assertEquals("legacy/system/old_item", ExplicitKeyTestScreen.legacyItem.key)
        assertEquals("absolute/custom_key", ExplicitKeyTestScreen.slashItem.key)
        assertEquals("ItemTreeArchitectureTest/ExplicitKeyTestScreen/my_section", ExplicitKeyTestScreen.customSection.key)
        assertEquals("ItemTreeArchitectureTest/ExplicitKeyTestScreen/bt_entry", ExplicitKeyTestScreen.customEntry.key)
    }

    @Test
    fun testExplicitRouteOnItemScreen() {
        assertEquals("Settings/Custom", CustomRouteScreen.route)
        assertEquals("Settings/Custom/master", CustomRouteScreen.masterSwitch.key)
        assertEquals("Settings/Custom/defaultItem", CustomRouteScreen.defaultItem.key)
    }
}


