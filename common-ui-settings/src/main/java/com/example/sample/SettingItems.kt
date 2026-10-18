package com.example.sample

import androidx.compose.runtime.Immutable
import com.example.uifw.item.*

/**
 * 커스텀 컴포넌트 데이터 노드 정의 (UiCustomItem 구현체)
 */
@Immutable
data class UiAudioLatencyVisualizerItem(
    override val key: String,
    override val title: String = "오디오 레이턴시 시각화",
    val defaultLatencyMs: Int = 45
) : UiCustomItem

/**
 * 정적 인자(Static Argument) 전달을 위한 시트 위치 Enum
 */
enum class SeatPosition(val label: String) {
    DRIVER("운전석"),
    PASSENGER("동승석")
}

/**
 * 루트 화면(SettingItems)만 object로 선언하고, 하위 SubScreen들은 class로 정의하여
 * 부모 화면 내부에서 `by subScreen(...)` 프로퍼티 위임으로 단일 인스턴스를 생성합니다.
 *
 * - 일반 SubScreen: `val Bluetooth by subScreen(::BluetoothScreen)`
 * - 정적 인자 SubScreen: `val DriverSeat by subScreen { parent, name -> SeatDetailScreen(parent, name, SeatPosition.DRIVER) }`
 * - 동적 인자 SubScreen: `routeArg = "deviceId"`를 선언하고 런타임에 `screen.createRoute("Galaxy_Buds3_Pro")`로 이동
 */
object SettingItems : ItemScreen(title = "환경 설정") {
    // Route: "SettingItems"

    // Key: "SettingItems/mainSection"
    val mainSection by section(title = "주요 설정")

    // Key: "SettingItems/seatSection"
    val seatSection by section(title = "차량 시트 설정")

    // 1. 기본 SubScreens (단일 인스턴스로 생성 및 부모의 subScreens에 자동 등록)
    // Route: "SettingItems/Bluetooth"
    val Bluetooth by subScreen(::BluetoothScreen)
    // Route: "SettingItems/Wifi"
    val Wifi by subScreen(::WifiScreen)

    // 2. 정적 인자(Static Argument)를 받는 SubScreens:
    // 동일한 SeatDetailScreen 클래스를 재사용하되, 생성자 인자(SeatPosition)를 다르게 전달하여
    // 서로 독립된 Route("SettingItems/DriverSeat", "SettingItems/PassengerSeat")와 DeepLink Key를 부여받습니다.
    val DriverSeat by subScreen { parent, name ->
        SeatDetailScreen(parent, name, SeatPosition.DRIVER)
    }
    val PassengerSeat by subScreen { parent, name ->
        SeatDetailScreen(parent, name, SeatPosition.PASSENGER)
    }

    // Key: "SettingItems/bluetoothEntry"
    val bluetoothEntry by subScreenEntry(target = Bluetooth)
    // Key: "SettingItems/wifiEntry"
    val wifiEntry by subScreenEntry(target = Wifi)

    // Key: "SettingItems/driverSeatEntry"
    val driverSeatEntry by subScreenEntry(target = DriverSeat)
    // Key: "SettingItems/passengerSeatEntry"
    val passengerSeatEntry by subScreenEntry(target = PassengerSeat)

    override val tree = buildTree {
        mainSection {
            +bluetoothEntry
            +wifiEntry
            staticText("version_info", text = "SW Version 1.0.0")
        }
        seatSection {
            +driverSeatEntry
            +passengerSeatEntry
        }
    }

    // ── SubScreen 1: Bluetooth (class) ───────────────────────────
    class BluetoothScreen(
        parent: ItemScreen,
        name: String? = null
    ) : ItemScreen(
        title = "블루투스 설정",
        parent = parent,
        explicitName = name
    ) {
        // Route: "SettingItems/Bluetooth"

        // Key: "SettingItems/Bluetooth/connectionSection"
        val connectionSection by section(title = "기본 연결")

        // Key: "SettingItems/Bluetooth/pairedDevicesSection"
        val pairedDevicesSection by section(title = "페어링된 기기")

        // Key: "SettingItems/Bluetooth/masterToggle"
        val masterToggle by toggle(title = "블루투스 사용")

        // SubScreen 1-1: Bluetooth Advanced (Depth 2)
        // Route: "SettingItems/Bluetooth/Advanced"
        val Advanced by subScreen(::AdvancedScreen)

        // SubScreen 1-2: 동적 인자(Dynamic Route Argument)를 받는 기기 상세 SubScreen
        // Base Route: "SettingItems/Bluetooth/DeviceDetail"
        // Route Pattern: "SettingItems/Bluetooth/DeviceDetail/{deviceId}"
        val DeviceDetail by subScreen(::DeviceDetailScreen)

        // Key: "SettingItems/Bluetooth/advancedEntry"
        val advancedEntry by subScreenEntry(target = Advanced)

        // 동적 인자("Galaxy_Buds3_Pro", "Car_Audio_Kit")를 넘겨 DeviceDetail 화면으로 진입하는 Entry 아이템들
        // Key: "SettingItems/Bluetooth/budsDeviceEntry"
        val budsDeviceEntry by entry(title = "Galaxy Buds3 Pro 상세", target = DeviceDetail)
        // Key: "SettingItems/Bluetooth/carKitDeviceEntry"
        val carKitDeviceEntry by entry(title = "Car Audio Kit 상세", target = DeviceDetail)

        override val tree = buildTree {
            connectionSection {
                +masterToggle
                +advancedEntry
            }
            pairedDevicesSection {
                +budsDeviceEntry
                +carKitDeviceEntry
            }
        }

        // ── SubScreen 1-1: Bluetooth Advanced (Depth 2, class) ───
        class AdvancedScreen(
            parent: ItemScreen,
            name: String? = null
        ) : ItemScreen(
            title = "블루투스 고급 설정",
            parent = parent,
            explicitName = name
        ) {
            // Route: "SettingItems/Bluetooth/Advanced"

            // Key: "SettingItems/Bluetooth/Advanced/audioSection"
            val audioSection by section(title = "오디오 고급 설정")

            // Key: "SettingItems/Bluetooth/Advanced/dualAudio"
            val dualAudio by toggle(title = "듀얼 오디오")

            // Key: "SettingItems/Bluetooth/Advanced/latencyVisualizer"
            val latencyVisualizer by screenItem { key ->
                UiAudioLatencyVisualizerItem(key = key)
            }

            override val tree = buildTree {
                audioSection {
                    +dualAudio
                    +latencyVisualizer
                    staticText("audio_codec", text = "Audio Codec: LDAC / aptX HD")
                }
            }
        }

        // ── SubScreen 1-2: 동적 인자(Dynamic Argument)를 받는 기기 상세 화면 ──
        class DeviceDetailScreen(
            parent: ItemScreen,
            name: String? = null
        ) : ItemScreen(
            title = "블루투스 기기 상세 설정",
            parent = parent,
            explicitName = name,
            routeArg = "deviceId"
        ) {
            // Base Route: "SettingItems/Bluetooth/DeviceDetail"
            // NavGraph Route Pattern: "SettingItems/Bluetooth/DeviceDetail/{deviceId}"

            // Key: "SettingItems/Bluetooth/DeviceDetail/profileSection"
            val profileSection by section(title = "연결 프로필 설정")

            // Key: "SettingItems/Bluetooth/DeviceDetail/callAudioToggle"
            val callAudioToggle by toggle(title = "통화 오디오")

            // Key: "SettingItems/Bluetooth/DeviceDetail/mediaAudioToggle"
            val mediaAudioToggle by toggle(title = "미디어 오디오")

            override val tree = buildTree {
                profileSection {
                    +callAudioToggle
                    +mediaAudioToggle
                }
            }
        }
    }

    // ── SubScreen 2: Wi-Fi (class) ───────────────────────────────
    class WifiScreen(
        parent: ItemScreen,
        name: String? = null
    ) : ItemScreen(
        title = "Wi-Fi 설정",
        parent = parent,
        explicitName = name
    ) {
        // Route: "SettingItems/Wifi"

        // Key: "SettingItems/Wifi/wifiSection"
        val wifiSection by section(title = "무선 네트워크")

        // Key: "SettingItems/Wifi/masterToggle"
        val masterToggle by toggle(title = "Wi-Fi 사용")

        override val tree = buildTree {
            wifiSection {
                +masterToggle
            }
        }
    }

    // ── SubScreen 3: 정적 인자(SeatPosition)를 받는 재사용 가능한 시트 상세 화면 ──
    class SeatDetailScreen(
        parent: ItemScreen,
        name: String? = null,
        val seatPosition: SeatPosition
    ) : ItemScreen(
        title = "${seatPosition.label} 시트 설정",
        parent = parent,
        explicitName = name
    ) {
        // Route: "SettingItems/DriverSeat" 또는 "SettingItems/PassengerSeat"

        // Key: "SettingItems/{DriverSeat|PassengerSeat}/comfortSection"
        val comfortSection by section(title = "${seatPosition.label} 열선/통풍")

        // Key: "SettingItems/{DriverSeat|PassengerSeat}/heaterToggle"
        val heaterToggle by toggle(title = "${seatPosition.label} 열선 시트")

        // Key: "SettingItems/{DriverSeat|PassengerSeat}/ventilationToggle"
        val ventilationToggle by toggle(title = "${seatPosition.label} 통풍 시트")

        override val tree = buildTree {
            comfortSection {
                +heaterToggle
                +ventilationToggle
            }
        }
    }
}


