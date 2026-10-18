package com.example.sample

import com.example.uifw.controller.EntryController
import com.example.uifw.controller.ItemController
import com.example.uifw.controller.ToggleController
import com.example.uifw.item.ItemScreen
import com.example.uifw.item.UiItem
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// 1. 블루투스 마스터 토글 ItemController
class BluetoothMasterToggleController @Inject constructor() : ItemController(), ToggleController {
    override val item: UiItem = SettingItems.Bluetooth.masterToggle

    private val _isChecked = MutableStateFlow(true)
    override val isChecked: StateFlow<Boolean> = _isChecked.asStateFlow()

    override val isEnabled: StateFlow<Boolean> = MutableStateFlow(true)

    override fun onToggleChange(checked: Boolean) {
        _isChecked.value = checked
    }
}

// 2. Wi-Fi 마스터 토글 ItemController
class WifiMasterToggleController @Inject constructor() : ItemController(), ToggleController {
    override val item: UiItem = SettingItems.Wifi.masterToggle

    private val _isChecked = MutableStateFlow(false)
    override val isChecked: StateFlow<Boolean> = _isChecked.asStateFlow()

    override val isEnabled: StateFlow<Boolean> = MutableStateFlow(true)

    override fun onToggleChange(checked: Boolean) {
        _isChecked.value = checked
    }
}

// 3. 블루투스 듀얼 오디오 토글 ItemController
class DualAudioToggleController @Inject constructor() : ItemController(), ToggleController {
    override val item: UiItem = SettingItems.Bluetooth.Advanced.dualAudio

    private val _isChecked = MutableStateFlow(false)
    override val isChecked: StateFlow<Boolean> = _isChecked.asStateFlow()

    override val isEnabled: StateFlow<Boolean> = MutableStateFlow(true)

    override fun onToggleChange(checked: Boolean) {
        _isChecked.value = checked
    }
}

// 4. 블루투스 화면 진입 EntryController
class BluetoothEntryController @Inject constructor() : ItemController(), EntryController {
    override val item: UiItem = SettingItems.bluetoothEntry

    override fun onClick(navigator: (ItemScreen) -> Unit) {
        navigator(SettingItems.Bluetooth)
    }
}

// 5. Wi-Fi 화면 진입 EntryController
class WifiEntryController @Inject constructor() : ItemController(), EntryController {
    override val item: UiItem = SettingItems.wifiEntry

    override fun onClick(navigator: (ItemScreen) -> Unit) {
        navigator(SettingItems.Wifi)
    }
}

// 6. 블루투스 고급 화면 진입 EntryController
class BluetoothAdvancedEntryController @Inject constructor() : ItemController(), EntryController {
    override val item: UiItem = SettingItems.Bluetooth.advancedEntry

    override fun onClick(navigator: (ItemScreen) -> Unit) {
        navigator(SettingItems.Bluetooth.Advanced)
    }
}

// 7. Hilt Multi-binding 모듈 (ItemController IntoSet)
@Module
@InstallIn(SingletonComponent::class)
abstract class SampleItemControllerModule {

    @Binds
    @IntoSet
    abstract fun bindBluetoothMasterToggle(
        controller: BluetoothMasterToggleController
    ): ItemController

    @Binds
    @IntoSet
    abstract fun bindWifiMasterToggle(
        controller: WifiMasterToggleController
    ): ItemController

    @Binds
    @IntoSet
    abstract fun bindDualAudioToggle(
        controller: DualAudioToggleController
    ): ItemController

    @Binds
    @IntoSet
    abstract fun bindBluetoothEntry(
        controller: BluetoothEntryController
    ): ItemController

    @Binds
    @IntoSet
    abstract fun bindWifiEntry(
        controller: WifiEntryController
    ): ItemController

    @Binds
    @IntoSet
    abstract fun bindBluetoothAdvancedEntry(
        controller: BluetoothAdvancedEntryController
    ): ItemController
}
