# ==============================================================================
# Car Settings Deep Link & Setter Test Suite (PowerShell)
# Tests deep link navigation and ?value=... setter mutations via adb am start
# ==============================================================================

param(
    [string]$Uri = "",
    [switch]$All
)

$PackageName = "com.example.lifecycleapp"
$Action = "android.intent.action.VIEW"

function Send-DeepLink {
    param(
        [string]$TargetUri,
        [string]$Description
    )
    Write-Host "------------------------------------------------------------" -ForegroundColor Cyan
    Write-Host ">> Test: $Description" -ForegroundColor Yellow
    Write-Host ">> URI:  $TargetUri" -ForegroundColor Blue
    adb shell am start -W -a $Action -d "$TargetUri" -p $PackageName
    Write-Host ""
}

function Run-AllTests {
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host " Starting Full Deep Link & Setter Test Suite                " -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Green

    # 1. Basic Navigation
    Send-DeepLink "myapp://navigate/home" "1. Navigate to Home (Root Anchor)"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/dashboard" "2. Navigate to Dashboard"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/door" "3. Navigate to Door Category"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/seat" "4. Navigate to Seat Category"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/light" "5. Navigate to External Light Category (Plugin)"
    Start-Sleep -Seconds 2

    # 2. Intra-Category Item Anchor Navigation (No Setter)
    Send-DeepLink "myapp://navigate/door/auto_lock" "6. Navigate to Door -> auto_lock anchor"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/seat/seat_lumbar" "7. Navigate to Seat Lumbar Detail Subscreen"
    Start-Sleep -Seconds 2

    # 3. Setter Mutations (?value=...)
    Send-DeepLink "myapp://navigate/door/auto_lock?value=false" "8. [Setter] Turn Auto-Lock OFF"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/door/auto_lock?value=true" "9. [Setter] Turn Auto-Lock ON"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/seat/driver_seat_heat?value=LEVEL_2" "10. [Setter] Set Driver Seat Heat to LEVEL 2"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/seat/driver_seat_heat?value=OFF" "11. [Setter] Turn Driver Seat Heat OFF"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/seat/seat_massage?value=WAVE" "12. [Setter] Set Massage Mode to WAVE"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/seat/seat_lumbar?value=80,70" "13. [Setter] Set Lumbar Support to 80% Height, 70% Depth"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/light/ambient_light?value=false" "14. [Setter] Turn Ambient Light OFF (External Plugin)"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/light/ambient_light?value=true" "15. [Setter] Turn Ambient Light ON (External Plugin)"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/light/headlights?value=OFF" "16. [Setter] Set Headlights to OFF (External Plugin)"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/light/headlights?value=AUTO" "17. [Setter] Set Headlights to AUTO (External Plugin)"
    Start-Sleep -Seconds 2

    Write-Host "============================================================" -ForegroundColor Green
    Write-Host " Deep Link & Setter Test Suite Completed!                   " -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Green
}

if ($All) {
    Run-AllTests
    exit
}

if ($Uri -ne "") {
    Send-DeepLink $Uri "Custom Deep Link"
    exit
}

# Interactive Menu
Write-Host "=== Car Settings Deep Link Tester ===" -ForegroundColor Green
Write-Host "1) All Tests (-All)"
Write-Host "2) Home (myapp://navigate/home)"
Write-Host "3) Door: Auto-Lock ON (myapp://navigate/door/auto_lock?value=true)"
Write-Host "4) Door: Auto-Lock OFF (myapp://navigate/door/auto_lock?value=false)"
Write-Host "5) Seat: Heat LEVEL 2 (myapp://navigate/seat/driver_seat_heat?value=LEVEL_2)"
Write-Host "6) Seat: Heat OFF (myapp://navigate/seat/driver_seat_heat?value=OFF)"
Write-Host "7) Seat: Lumbar 80,70 (myapp://navigate/seat/seat_lumbar?value=80,70)"
Write-Host "8) Light: Ambient ON (myapp://navigate/light/ambient_light?value=true)"
Write-Host "9) Light: Ambient OFF (myapp://navigate/light/ambient_light?value=false)"
Write-Host "10) Light: Headlights OFF (myapp://navigate/light/headlights?value=OFF)"
Write-Host "11) Light: Headlights AUTO (myapp://navigate/light/headlights?value=AUTO)"
$choice = Read-Host "Select an option [1-11]"

switch ($choice) {
    "1" { Run-AllTests }
    "2" { Send-DeepLink "myapp://navigate/home" "Navigate to Home" }
    "3" { Send-DeepLink "myapp://navigate/door/auto_lock?value=true" "Door Auto-Lock ON" }
    "4" { Send-DeepLink "myapp://navigate/door/auto_lock?value=false" "Door Auto-Lock OFF" }
    "5" { Send-DeepLink "myapp://navigate/seat/driver_seat_heat?value=LEVEL_2" "Seat Heat LEVEL 2" }
    "6" { Send-DeepLink "myapp://navigate/seat/driver_seat_heat?value=OFF" "Seat Heat OFF" }
    "7" { Send-DeepLink "myapp://navigate/seat/seat_lumbar?value=80,70" "Seat Lumbar (80,70)" }
    "8" { Send-DeepLink "myapp://navigate/light/ambient_light?value=true" "Light Ambient ON" }
    "9" { Send-DeepLink "myapp://navigate/light/ambient_light?value=false" "Light Ambient OFF" }
    "10" { Send-DeepLink "myapp://navigate/light/headlights?value=OFF" "Light Headlights OFF" }
    "11" { Send-DeepLink "myapp://navigate/light/headlights?value=AUTO" "Light Headlights AUTO" }
    default { Write-Host "Invalid option." -ForegroundColor Red }
}
