package com.example.sample

import com.example.uifw.item.UiItem
import com.example.uifw.controller.ItemController
import com.example.uifw.controller.ItemControllerFactory
import com.example.uifw.controller.ToggleController
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// 1. 블루투스 마스터 토글 ItemController
class BluetoothMasterToggleController @AssistedInject constructor(
    @Assisted private val item: UiItem
) : ItemController(), ToggleController {

    private val _isChecked = MutableStateFlow(true)
    override val isChecked: StateFlow<Boolean> = _isChecked.asStateFlow()

    override val isEnabled: StateFlow<Boolean> = MutableStateFlow(true)

    override fun onToggleChange(checked: Boolean) {
        _isChecked.value = checked
    }

    @AssistedFactory
    interface Factory : ItemControllerFactory<BluetoothMasterToggleController> {
        override val targetItem: UiItem
            get() = SettingItems.Bluetooth.masterToggle

        override fun create(item: UiItem): BluetoothMasterToggleController
    }
}

// 2. Wi-Fi 마스터 토글 ItemController
class WifiMasterToggleController @AssistedInject constructor(
    @Assisted private val item: UiItem
) : ItemController(), ToggleController {

    private val _isChecked = MutableStateFlow(false)
    override val isChecked: StateFlow<Boolean> = _isChecked.asStateFlow()

    override val isEnabled: StateFlow<Boolean> = MutableStateFlow(true)

    override fun onToggleChange(checked: Boolean) {
        _isChecked.value = checked
    }

    @AssistedFactory
    interface Factory : ItemControllerFactory<WifiMasterToggleController> {
        override val targetItem: UiItem
            get() = SettingItems.Wifi.masterToggle

        override fun create(item: UiItem): WifiMasterToggleController
    }
}

// 3. 블루투스 듀얼 오디오 토글 ItemController
class DualAudioToggleController @AssistedInject constructor(
    @Assisted private val item: UiItem
) : ItemController(), ToggleController {

    private val _isChecked = MutableStateFlow(false)
    override val isChecked: StateFlow<Boolean> = _isChecked.asStateFlow()

    override val isEnabled: StateFlow<Boolean> = MutableStateFlow(true)

    override fun onToggleChange(checked: Boolean) {
        _isChecked.value = checked
    }

    @AssistedFactory
    interface Factory : ItemControllerFactory<DualAudioToggleController> {
        override val targetItem: UiItem
            get() = SettingItems.Bluetooth.Advanced.dualAudio

        override fun create(item: UiItem): DualAudioToggleController
    }
}

// 4. Hilt Multi-binding 모듈
@Module
@InstallIn(SingletonComponent::class)
abstract class SampleItemControllerModule {

    @Binds
    @IntoSet
    abstract fun bindBluetoothMasterToggle(
        factory: BluetoothMasterToggleController.Factory
    ): ItemControllerFactory<*>

    @Binds
    @IntoSet
    abstract fun bindWifiMasterToggle(
        factory: WifiMasterToggleController.Factory
    ): ItemControllerFactory<*>

    @Binds
    @IntoSet
    abstract fun bindDualAudioToggle(
        factory: DualAudioToggleController.Factory
    ): ItemControllerFactory<*>
}
