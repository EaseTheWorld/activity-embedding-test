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
 * 한 파일 안에 중첩 object로 화면 계층을 구성하여
 * 컴파일 타임에 SubScreen 이름 및 프로퍼티(Key) 중복을 100% 방지합니다.
 *
 * Section과 Item 모두 val 프로퍼티로 선언하여 고유 Key(DeepLink)를 자동 부여받고,
 * 계층 구조(Hierarchy)는 DSL에서 조립합니다.
 */
object SettingItems : ItemScreen(title = "환경 설정", parent = null) {
    // Route: "SettingItems"

    // Key: "SettingItems/mainSection"
    val mainSection by section(title = "주요 설정")

    // Key: "SettingItems/bluetoothEntry"
    val bluetoothEntry by entry(title = "블루투스 설정")
    // Key: "SettingItems/wifiEntry"
    val wifiEntry by entry(title = "Wi-Fi 설정")

    override val tree = buildTree {
        mainSection {
            +bluetoothEntry
            +wifiEntry
            staticText("version_info", text = "SW Version 1.0.0")
        }
    }

    // ── SubScreen 1: Bluetooth ───────────────────────────────────
    object Bluetooth : ItemScreen(
        title = "블루투스 설정",
        parentSection = SettingItems.mainSection
    ) {
        // Route: "SettingItems/Bluetooth"

        // Key: "SettingItems/Bluetooth/connectionSection"
        val connectionSection by section(title = "기본 연결")

        // Key: "SettingItems/Bluetooth/masterToggle"
        val masterToggle by toggle(title = "블루투스 사용")
        // Key: "SettingItems/Bluetooth/advancedEntry"
        val advancedEntry by entry(title = "블루투스 고급 설정")

        override val tree = buildTree {
            connectionSection {
                +masterToggle
                +advancedEntry
            }
        }

        // ── SubScreen 1-1: Bluetooth Advanced (Depth 2) ──────────
        object Advanced : ItemScreen(
            title = "블루투스 고급 설정",
            parentSection = Bluetooth.connectionSection
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

    // ── SubScreen 2: Wi-Fi ───────────────────────────────────────
    object Wifi : ItemScreen(
        title = "Wi-Fi 설정",
        parentSection = SettingItems.mainSection
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

/**
 * 앱 전체 설정 화면 컬렉션.
 * 각 화면은 부모(또는 parentSection)만 참조하고 자식은 모르는 순수 단방향 DAG이므로,
 * 전체 화면 인덱싱 및 딥링크 맵 구성을 위해 화면 모음(Registry)을 제공합니다.
 * (Hilt 사용 시 @IntoSet Set<ItemScreen>으로 멀티바인딩도 가능)
 */
object SettingScreens {
    val all: List<ItemScreen> = listOf(
        SettingItems,
        SettingItems.Bluetooth,
        SettingItems.Bluetooth.Advanced,
        SettingItems.Wifi
    )
}
