package com.example.sample

import com.example.uifw.item.UiItem
import com.example.uifw.controller.ItemController
import com.example.uifw.controller.ToggleController
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
    override val targetItem: UiItem = SettingItems.Bluetooth.masterToggle

    private val _isChecked = MutableStateFlow(true)
    override val isChecked: StateFlow<Boolean> = _isChecked.asStateFlow()

    override val isEnabled: StateFlow<Boolean> = MutableStateFlow(true)

    override fun onToggleChange(checked: Boolean) {
        _isChecked.value = checked
    }
}

// 2. Wi-Fi 마스터 토글 ItemController
class WifiMasterToggleController @Inject constructor() : ItemController(), ToggleController {
    override val targetItem: UiItem = SettingItems.Wifi.masterToggle

    private val _isChecked = MutableStateFlow(false)
    override val isChecked: StateFlow<Boolean> = _isChecked.asStateFlow()

    override val isEnabled: StateFlow<Boolean> = MutableStateFlow(true)

    override fun onToggleChange(checked: Boolean) {
        _isChecked.value = checked
    }
}

// 3. 블루투스 듀얼 오디오 토글 ItemController
class DualAudioToggleController @Inject constructor() : ItemController(), ToggleController {
    override val targetItem: UiItem = SettingItems.Bluetooth.Advanced.dualAudio

    private val _isChecked = MutableStateFlow(false)
    override val isChecked: StateFlow<Boolean> = _isChecked.asStateFlow()

    override val isEnabled: StateFlow<Boolean> = MutableStateFlow(true)

    override fun onToggleChange(checked: Boolean) {
        _isChecked.value = checked
    }
}

// 4. Hilt Multi-binding 모듈 (ItemController IntoSet)
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
}
