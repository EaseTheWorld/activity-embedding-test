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
 * 루트 화면(SettingItems)만 object로 선언하고, 하위 SubScreen들은 class로 정의하여
 * 부모 화면 내부에서 `by subScreen(...)` 프로퍼티 위임으로 단일 인스턴스를 생성합니다.
 *
 * - 부모 -> 자식 단방향 생성자 주입이므로 클래스로더 순환 참조(Deadlock/null)가 원천 차단됩니다.
 * - Section과 Item, SubScreen 모두 `by` 위임 프로퍼티로 일관성 있게 선언됩니다.
 * - 트리(buildTree) 내 Section 안에 Entry(또는 SubScreen)를 배치하는 것만으로
 *   별도의 parentSection 지정이나 수동 subScreens 리스트 없이 전체 Breadcrumb과 화면 목록이 자동 구성됩니다.
 */
object SettingItems : ItemScreen(title = "환경 설정") {
    // Route: "SettingItems"

    // Key: "SettingItems/mainSection"
    val mainSection by section(title = "주요 설정")

    // SubScreens (단일 인스턴스로 생성 및 부모의 subScreens에 자동 등록)
    // Route: "SettingItems/Bluetooth"
    val Bluetooth by subScreen(::BluetoothScreen)
    // Route: "SettingItems/Wifi"
    val Wifi by subScreen(::WifiScreen)

    // Key: "SettingItems/bluetoothEntry"
    val bluetoothEntry by subScreenEntry(target = Bluetooth)
    // Key: "SettingItems/wifiEntry"
    val wifiEntry by subScreenEntry(target = Wifi)

    override val tree = buildTree {
        mainSection {
            +bluetoothEntry
            +wifiEntry
            staticText("version_info", text = "SW Version 1.0.0")
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

        // Key: "SettingItems/Bluetooth/masterToggle"
        val masterToggle by toggle(title = "블루투스 사용")

        // SubScreen 1-1: Bluetooth Advanced (Depth 2)
        // Route: "SettingItems/Bluetooth/Advanced"
        val Advanced by subScreen(::AdvancedScreen)

        // Key: "SettingItems/Bluetooth/advancedEntry"
        val advancedEntry by subScreenEntry(target = Advanced)

        override val tree = buildTree {
            connectionSection {
                +masterToggle
                +advancedEntry
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
}

