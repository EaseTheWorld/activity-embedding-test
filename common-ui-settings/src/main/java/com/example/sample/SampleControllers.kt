package com.example.sample

import androidx.compose.runtime.Immutable
import com.example.uifw.controller.EntryController
import com.example.uifw.controller.ItemController
import com.example.uifw.controller.ItemNavigator
import com.example.uifw.controller.ItemUiState
import com.example.uifw.controller.StatefulItemController
import com.example.uifw.controller.ToggleItemController
import com.example.uifw.item.UiItem
import com.example.uifw.item.UiToggleItem
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// 1. 블루투스 마스터 토글 ItemController (ToggleUiState 방출)
class BluetoothMasterToggleController @Inject constructor() : ToggleItemController(
    item = SettingItems.Bluetooth.masterToggle,
    initialChecked = true
)

// 2. Wi-Fi 마스터 토글 ItemController (ToggleUiState 방출)
class WifiMasterToggleController @Inject constructor() : ToggleItemController(
    item = SettingItems.Wifi.masterToggle,
    initialChecked = false
)

// 3. 블루투스 듀얼 오디오 토글 ItemController (ToggleUiState 방출)
class DualAudioToggleController @Inject constructor() : ToggleItemController(
    item = SettingItems.Bluetooth.Advanced.dualAudio,
    initialChecked = false
)

// 4. 블루투스 화면 진입 EntryController
// onClick을 오버라이드하지 않으면 EntryItem(SettingItems.bluetoothEntry)에 선언된 targetScreen(SettingItems.Bluetooth)으로 자동 이동합니다.
class BluetoothEntryController @Inject constructor() : ItemController(), EntryController {
    override val item: UiItem = SettingItems.bluetoothEntry
}

// 5. Wi-Fi 화면 진입 EntryController (기본 targetScreen = SettingItems.Wifi 사용)
class WifiEntryController @Inject constructor() : ItemController(), EntryController {
    override val item: UiItem = SettingItems.wifiEntry
}

// 6. 블루투스 고급 화면 진입 EntryController (기본 targetScreen = SettingItems.Bluetooth.Advanced 사용)
class BluetoothAdvancedEntryController @Inject constructor() : ItemController(), EntryController {
    override val item: UiItem = SettingItems.Bluetooth.advancedEntry
}

// 7. 정적 인자 SubScreen(DriverSeat / PassengerSeat)을 위한 재사용 가능한 시트 토글 Controller
abstract class BaseSeatToggleController(
    item: UiToggleItem,
    initialChecked: Boolean = false
) : ToggleItemController(item = item, initialChecked = initialChecked)

class DriverSeatHeaterController @Inject constructor() :
    BaseSeatToggleController(SettingItems.DriverSeat.heaterToggle, initialChecked = true)

class DriverSeatVentilationController @Inject constructor() :
    BaseSeatToggleController(SettingItems.DriverSeat.ventilationToggle, initialChecked = false)

class PassengerSeatHeaterController @Inject constructor() :
    BaseSeatToggleController(SettingItems.PassengerSeat.heaterToggle, initialChecked = false)

class PassengerSeatVentilationController @Inject constructor() :
    BaseSeatToggleController(SettingItems.PassengerSeat.ventilationToggle, initialChecked = false)

// 8. 동적 인자(deviceId)를 전달하여 DeviceDetail SubScreen으로 이동하는 EntryController
class BudsDeviceEntryController @Inject constructor() : ItemController(), EntryController {
    override val item: UiItem = SettingItems.Bluetooth.budsDeviceEntry

    override fun onClick(navigator: ItemNavigator) {
        // 런타임 동적 인자("Galaxy_Buds3_Pro")를 포함한 라우트로 이동
        navigator(SettingItems.Bluetooth.DeviceDetail.createRoute("Galaxy_Buds3_Pro"))
    }
}

class CarKitDeviceEntryController @Inject constructor() : ItemController(), EntryController {
    override val item: UiItem = SettingItems.Bluetooth.carKitDeviceEntry

    override fun onClick(navigator: ItemNavigator) {
        // 런타임 동적 인자("Car_Audio_Kit")를 포함한 라우트로 이동
        navigator(SettingItems.Bluetooth.DeviceDetail.createRoute("Car_Audio_Kit"))
    }
}

class DeviceCallAudioToggleController @Inject constructor() :
    BaseSeatToggleController(SettingItems.Bluetooth.DeviceDetail.callAudioToggle, initialChecked = true)

class DeviceMediaAudioToggleController @Inject constructor() :
    BaseSeatToggleController(SettingItems.Bluetooth.DeviceDetail.mediaAudioToggle, initialChecked = true)

// 9. 커스텀 아이템(UiAudioLatencyVisualizerItem)을 위한 순수 불변 UiState 및 Controller
@Immutable
data class AudioLatencyUiState(
    override val item: UiAudioLatencyVisualizerItem,
    val title: String = item.title,
    val latencyMs: Int = item.defaultLatencyMs,
    override val isVisible: Boolean = true,
    override val isEnabled: Boolean = true
) : ItemUiState

class AudioLatencyVisualizerController @Inject constructor() :
    StatefulItemController<AudioLatencyUiState, Int>() {

    override val item: UiAudioLatencyVisualizerItem =
        SettingItems.Bluetooth.Advanced.latencyVisualizer

    private val _uiState = MutableStateFlow(AudioLatencyUiState(item = item))
    override val uiState: StateFlow<AudioLatencyUiState> = _uiState.asStateFlow()

    override fun onValueChange(value: Int) {
        _uiState.value = _uiState.value.copy(latencyMs = value)
    }

    fun setVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(isVisible = visible)
    }

    fun setEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isEnabled = enabled)
    }
}

// 10. Hilt Multi-binding 모듈 (ItemController & ItemRenderer IntoSet)
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

    @Binds
    @IntoSet
    abstract fun bindDriverSeatHeater(
        controller: DriverSeatHeaterController
    ): ItemController

    @Binds
    @IntoSet
    abstract fun bindDriverSeatVentilation(
        controller: DriverSeatVentilationController
    ): ItemController

    @Binds
    @IntoSet
    abstract fun bindPassengerSeatHeater(
        controller: PassengerSeatHeaterController
    ): ItemController

    @Binds
    @IntoSet
    abstract fun bindPassengerSeatVentilation(
        controller: PassengerSeatVentilationController
    ): ItemController

    @Binds
    @IntoSet
    abstract fun bindBudsDeviceEntry(
        controller: BudsDeviceEntryController
    ): ItemController

    @Binds
    @IntoSet
    abstract fun bindCarKitDeviceEntry(
        controller: CarKitDeviceEntryController
    ): ItemController

    @Binds
    @IntoSet
    abstract fun bindDeviceCallAudioToggle(
        controller: DeviceCallAudioToggleController
    ): ItemController

    @Binds
    @IntoSet
    abstract fun bindDeviceMediaAudioToggle(
        controller: DeviceMediaAudioToggleController
    ): ItemController

    @Binds
    @IntoSet
    abstract fun bindAudioLatencyVisualizerController(
        controller: AudioLatencyVisualizerController
    ): ItemController

    @Binds
    @IntoSet
    abstract fun bindAudioLatencyVisualizerRenderer(
        renderer: AudioLatencyVisualizerRenderer
    ): com.example.uifw.ui.ItemRenderer<*, *>
}
