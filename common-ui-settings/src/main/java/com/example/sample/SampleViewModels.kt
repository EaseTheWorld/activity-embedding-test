package com.example.sample

import com.example.uifw.node.UiNode
import com.example.uifw.vm.NodeViewModel
import com.example.uifw.vm.NodeViewModelFactory
import com.example.uifw.vm.ToggleController
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

// 1. 블루투스 마스터 토글 ViewModel
class BluetoothMasterToggleViewModel @AssistedInject constructor(
    @Assisted private val node: UiNode
    // private val bluetoothRepository: BluetoothRepository (Hilt 주입)
) : NodeViewModel(), ToggleController {

    private val _isChecked = MutableStateFlow(true)
    override val isChecked: StateFlow<Boolean> = _isChecked.asStateFlow()

    override val isEnabled: StateFlow<Boolean> = MutableStateFlow(true)

    override fun onToggleChange(checked: Boolean) {
        _isChecked.value = checked
    }

    @AssistedFactory
    interface Factory : NodeViewModelFactory<BluetoothMasterToggleViewModel> {
        // 문자열 하드코딩 없이 SettingNodes의 프로퍼티를 직접 참조!
        override val targetNode: UiNode
            get() = SettingNodes.Bluetooth.masterToggle

        override fun create(node: UiNode): BluetoothMasterToggleViewModel
    }
}

// 2. Wi-Fi 마스터 토글 ViewModel
class WifiMasterToggleViewModel @AssistedInject constructor(
    @Assisted private val node: UiNode
) : NodeViewModel(), ToggleController {

    private val _isChecked = MutableStateFlow(false)
    override val isChecked: StateFlow<Boolean> = _isChecked.asStateFlow()

    override val isEnabled: StateFlow<Boolean> = MutableStateFlow(true)

    override fun onToggleChange(checked: Boolean) {
        _isChecked.value = checked
    }

    @AssistedFactory
    interface Factory : NodeViewModelFactory<WifiMasterToggleViewModel> {
        override val targetNode: UiNode
            get() = SettingNodes.Wifi.masterToggle

        override fun create(node: UiNode): WifiMasterToggleViewModel
    }
}

// 3. 블루투스 듀얼 오디오 토글 ViewModel (SubScreen 1-1 Advanced 지원)
class DualAudioToggleViewModel @AssistedInject constructor(
    @Assisted private val node: UiNode
) : NodeViewModel(), ToggleController {

    private val _isChecked = MutableStateFlow(false)
    override val isChecked: StateFlow<Boolean> = _isChecked.asStateFlow()

    override val isEnabled: StateFlow<Boolean> = MutableStateFlow(true)

    override fun onToggleChange(checked: Boolean) {
        _isChecked.value = checked
    }

    @AssistedFactory
    interface Factory : NodeViewModelFactory<DualAudioToggleViewModel> {
        override val targetNode: UiNode
            get() = SettingNodes.Bluetooth.Advanced.dualAudio

        override fun create(node: UiNode): DualAudioToggleViewModel
    }
}

// 4. Hilt Multi-binding 모듈 (@IntoSet으로 어노테이션 키 타이핑 제거)
@Module
@InstallIn(SingletonComponent::class)
abstract class SampleNodeViewModelModule {

    @Binds
    @IntoSet
    abstract fun bindBluetoothMasterToggle(
        factory: BluetoothMasterToggleViewModel.Factory
    ): NodeViewModelFactory<*>

    @Binds
    @IntoSet
    abstract fun bindWifiMasterToggle(
        factory: WifiMasterToggleViewModel.Factory
    ): NodeViewModelFactory<*>

    @Binds
    @IntoSet
    abstract fun bindDualAudioToggle(
        factory: DualAudioToggleViewModel.Factory
    ): NodeViewModelFactory<*>
}
