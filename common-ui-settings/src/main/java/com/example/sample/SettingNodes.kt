package com.example.sample

import com.example.uifw.node.*

/**
 * 한 파일 안에 중첩 object로 화면 계층을 구성하여
 * 컴파일 타임에 SubScreen 이름 및 프로퍼티(Key) 중복을 100% 방지합니다.
 */
object SettingNodes : NodeScreen(title = "환경 설정") {
    // Route: "SettingNodes"

    val bluetoothEntry by subScreenEntry(target = Bluetooth)
    val wifiEntry by subScreenEntry(target = Wifi)

    override val tree = buildTree {
        column("main_section") {
            +bluetoothEntry
            +wifiEntry
            staticText("version_info", text = "SW Version 1.0.0")
        }
    }

    // ── SubScreen 1: Bluetooth ───────────────────────────────────
    object Bluetooth : NodeScreen(title = "블루투스 설정") {
        // Route: "SettingNodes/Bluetooth"

        // Key: "SettingNodes/Bluetooth/masterToggle"
        val masterToggle by toggle(title = "블루투스 사용")
        // Key: "SettingNodes/Bluetooth/advancedEntry"
        val advancedEntry by subScreenEntry(target = Advanced)

        override val tree = buildTree {
            column("bt_section") {
                +masterToggle
                +advancedEntry
            }
        }

        // ── SubScreen 1-1: Bluetooth Advanced (Depth 2) ──────────
        object Advanced : NodeScreen(title = "블루투스 고급 설정") {
            // Route: "SettingNodes/Bluetooth/Advanced"

            // Key: "SettingNodes/Bluetooth/Advanced/dualAudio"
            val dualAudio by toggle(title = "듀얼 오디오")

            override val tree = buildTree {
                column("bt_adv_section") {
                    +dualAudio
                }
            }
        }
    }

    // ── SubScreen 2: Wi-Fi ───────────────────────────────────────
    object Wifi : NodeScreen(title = "Wi-Fi 설정") {
        // Route: "SettingNodes/Wifi"

        // Bluetooth와 변수명(masterToggle)이 같아도 스코프가 달라 충돌 없음!
        // Key: "SettingNodes/Wifi/masterToggle"
        val masterToggle by toggle(title = "Wi-Fi 사용")

        override val tree = buildTree {
            column("wifi_section") {
                +masterToggle
            }
        }
    }
}
