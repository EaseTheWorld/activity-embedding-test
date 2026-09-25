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
        [string]$Description,
        [string]$Extra = ""
    )
    Write-Host "------------------------------------------------------------" -ForegroundColor Cyan
    Write-Host ">> Test:  $Description" -ForegroundColor Yellow
    Write-Host ">> URI:   $TargetUri" -ForegroundColor Blue
    if ($Extra -ne "") {
        Write-Host ">> Extra: $Extra" -ForegroundColor Blue
        $cmd = "am start -W -a $Action -d `"$TargetUri`" $Extra -p $PackageName"
        adb shell $cmd
    } else {
        adb shell am start -W -a $Action -d "$TargetUri" -p $PackageName
    }
    Write-Host ""
}

function Run-AllTests {
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host " Starting Full Deep Link & Setter Test Suite                " -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Green

    # 1. Basic Navigation (GET: pure destination URI)
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

    # 2. Intra-Category Item Anchor Navigation (No Mutation)
    Send-DeepLink "myapp://navigate/door/auto_lock" "6. Navigate to Door -> auto_lock anchor"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/seat/seat_lumbar" "7. Navigate to Seat Lumbar Detail Subscreen"
    Start-Sleep -Seconds 2

    # 3. Setter Mutations via Intent Extras (POST: payload separated from URI)
    Send-DeepLink "myapp://navigate/door/auto_lock" "8. [Extra Setter] Turn Auto-Lock OFF" "--ez value false"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/door/auto_lock" "9. [Extra Setter] Turn Auto-Lock ON" "--ez value true"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/seat/driver_seat_heat" "10. [Extra Setter] Set Driver Seat Heat to LEVEL 2" "--es value LEVEL_2"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/seat/driver_seat_heat" "11. [Extra Setter] Turn Driver Seat Heat OFF" "--es value OFF"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/seat/seat_massage" "12. [Extra Setter] Set Massage Mode to WAVE" "--es value WAVE"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/seat/seat_lumbar" "13. [Extra Setter] Set Lumbar Support to 80% Height, 70% Depth" "--es value 80,70"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/light/ambient_light" "14. [Extra Setter] Turn Ambient Light OFF (External Plugin)" "--ez value false"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/light/ambient_light" "15. [Extra Setter] Turn Ambient Light ON (External Plugin)" "--ez value true"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/light/headlights" "16. [Extra Setter] Set Headlights to OFF (External Plugin)" "--es value OFF"
    Start-Sleep -Seconds 2

    Send-DeepLink "myapp://navigate/light/headlights" "17. [Extra Setter] Set Headlights to AUTO (External Plugin)" "--es value AUTO"
    Start-Sleep -Seconds 2

    # 4. Backward-Compatibility Fallback (?value=... query parameter)
    Send-DeepLink "myapp://navigate/door/auto_lock?value=false" "18. [Legacy Fallback] Turn Auto-Lock OFF via query param"
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
Write-Host "3) Door: Auto-Lock ON (myapp://navigate/door/auto_lock --ez value true)"
Write-Host "4) Door: Auto-Lock OFF (myapp://navigate/door/auto_lock --ez value false)"
Write-Host "5) Seat: Heat LEVEL 2 (myapp://navigate/seat/driver_seat_heat --es value LEVEL_2)"
Write-Host "6) Seat: Heat OFF (myapp://navigate/seat/driver_seat_heat --es value OFF)"
Write-Host "7) Seat: Lumbar 80,70 (myapp://navigate/seat/seat_lumbar --es value 80,70)"
Write-Host "8) Light: Ambient ON (myapp://navigate/light/ambient_light --ez value true)"
Write-Host "9) Light: Ambient OFF (myapp://navigate/light/ambient_light --ez value false)"
Write-Host "10) Light: Headlights OFF (myapp://navigate/light/headlights --es value OFF)"
Write-Host "11) Light: Headlights AUTO (myapp://navigate/light/headlights --es value AUTO)"
$choice = Read-Host "Select an option [1-11]"

switch ($choice) {
    "1" { Run-AllTests }
    "2" { Send-DeepLink "myapp://navigate/home" "Navigate to Home" }
    "3" { Send-DeepLink "myapp://navigate/door/auto_lock" "Door Auto-Lock ON" "--ez value true" }
    "4" { Send-DeepLink "myapp://navigate/door/auto_lock" "Door Auto-Lock OFF" "--ez value false" }
    "5" { Send-DeepLink "myapp://navigate/seat/driver_seat_heat" "Seat Heat LEVEL 2" "--es value LEVEL_2" }
    "6" { Send-DeepLink "myapp://navigate/seat/driver_seat_heat" "Seat Heat OFF" "--es value OFF" }
    "7" { Send-DeepLink "myapp://navigate/seat/seat_lumbar" "Seat Lumbar (80,70)" "--es value 80,70" }
    "8" { Send-DeepLink "myapp://navigate/light/ambient_light" "Light Ambient ON" "--ez value true" }
    "9" { Send-DeepLink "myapp://navigate/light/ambient_light" "Light Ambient OFF" "--ez value false" }
    "10" { Send-DeepLink "myapp://navigate/light/headlights" "Light Headlights OFF" "--es value OFF" }
    "11" { Send-DeepLink "myapp://navigate/light/headlights" "Light Headlights AUTO" "--es value AUTO" }
    default { Write-Host "Invalid option." -ForegroundColor Red }
}
