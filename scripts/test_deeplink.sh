#!/usr/bin/env bash
# ==============================================================================
# Car Settings Deep Link & Setter Test Suite
# Tests deep link navigation and ?value=... setter mutations via adb am start
# ==============================================================================

set -e

PACKAGE_NAME="com.example.lifecycleapp"
ACTION="android.intent.action.VIEW"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

send_deeplink() {
    local uri="$1"
    local desc="$2"
    local extra="$3"
    echo -e "${CYAN}------------------------------------------------------------${NC}"
    echo -e "${YELLOW}>> Test:${NC} $desc"
    echo -e "${BLUE}>> URI:${NC}   $uri"
    if [ -n "$extra" ]; then
        echo -e "${BLUE}>> Extra:${NC} $extra"
        adb shell am start -W -a "$ACTION" -d "$uri" $extra -p "$PACKAGE_NAME"
    else
        adb shell am start -W -a "$ACTION" -d "$uri" -p "$PACKAGE_NAME"
    fi
    echo ""
}

run_all_tests() {
    echo -e "${GREEN}============================================================${NC}"
    echo -e "${GREEN} Starting Full Deep Link & Setter Test Suite                ${NC}"
    echo -e "${GREEN}============================================================${NC}"

    # 1. Basic Navigation (GET: pure destination URI)
    send_deeplink "myapp://navigate/home" "1. Navigate to Home (Root Anchor)"
    sleep 2

    send_deeplink "myapp://navigate/dashboard" "2. Navigate to Dashboard"
    sleep 2

    send_deeplink "myapp://navigate/door" "3. Navigate to Door Category"
    sleep 2

    send_deeplink "myapp://navigate/seat" "4. Navigate to Seat Category"
    sleep 2

    send_deeplink "myapp://navigate/light" "5. Navigate to External Light Category (Plugin)"
    sleep 2

    # 2. Intra-Category Item Anchor Navigation (No Mutation)
    send_deeplink "myapp://navigate/door/auto_lock" "6. Navigate to Door -> auto_lock anchor"
    sleep 2

    send_deeplink "myapp://navigate/seat/seat_lumbar" "7. Navigate to Seat Lumbar Detail Subscreen"
    sleep 2

    # 3. Setter Mutations via Intent Extras (POST: payload separated from URI)
    send_deeplink "myapp://navigate/door/auto_lock" "8. [Extra Setter] Turn Auto-Lock OFF" "--ez value false"
    sleep 2

    send_deeplink "myapp://navigate/door/auto_lock" "9. [Extra Setter] Turn Auto-Lock ON" "--ez value true"
    sleep 2

    send_deeplink "myapp://navigate/seat/driver_seat_heat" "10. [Extra Setter] Set Driver Seat Heat to LEVEL 2" "--es value LEVEL_2"
    sleep 2

    send_deeplink "myapp://navigate/seat/driver_seat_heat" "11. [Extra Setter] Turn Driver Seat Heat OFF" "--es value OFF"
    sleep 2

    send_deeplink "myapp://navigate/seat/seat_massage" "12. [Extra Setter] Set Massage Mode to WAVE" "--es value WAVE"
    sleep 2

    send_deeplink "myapp://navigate/seat/seat_lumbar" "13. [Extra Setter] Set Lumbar Support to 80% Height, 70% Depth" "--es value 80,70"
    sleep 2

    send_deeplink "myapp://navigate/light/ambient_light" "14. [Extra Setter] Turn Ambient Light OFF (External Plugin)" "--ez value false"
    sleep 2

    send_deeplink "myapp://navigate/light/ambient_light" "15. [Extra Setter] Turn Ambient Light ON (External Plugin)" "--ez value true"
    sleep 2

    send_deeplink "myapp://navigate/light/headlights" "16. [Extra Setter] Set Headlights to OFF (External Plugin)" "--es value OFF"
    sleep 2

    send_deeplink "myapp://navigate/light/headlights" "17. [Extra Setter] Set Headlights to AUTO (External Plugin)" "--es value AUTO"
    sleep 2

    # 4. Backward-Compatibility Fallback (?value=... query parameter)
    send_deeplink "myapp://navigate/door/auto_lock?value=false" "18. [Legacy Fallback] Turn Auto-Lock OFF via query param"
    sleep 2

    echo -e "${GREEN}============================================================${NC}"
    echo -e "${GREEN} Deep Link & Setter Test Suite Completed!                   ${NC}"
    echo -e "${GREEN}============================================================${NC}"
}

if [ "$1" == "--all" ]; then
    run_all_tests
    exit 0
fi

if [ -n "$1" ]; then
    send_deeplink "$1" "Custom Deep Link" "$2"
    exit 0
fi

# Interactive menu
echo -e "${GREEN}=== Car Settings Deep Link Tester ===${NC}"
echo "1) All Tests (--all)"
echo "2) Home (myapp://navigate/home)"
echo "3) Door: Auto-Lock ON (myapp://navigate/door/auto_lock --ez value true)"
echo "4) Door: Auto-Lock OFF (myapp://navigate/door/auto_lock --ez value false)"
echo "5) Seat: Heat LEVEL 2 (myapp://navigate/seat/driver_seat_heat --es value LEVEL_2)"
echo "6) Seat: Heat OFF (myapp://navigate/seat/driver_seat_heat --es value OFF)"
echo "7) Seat: Lumbar 80,70 (myapp://navigate/seat/seat_lumbar --es value 80,70)"
echo "8) Light: Ambient ON (myapp://navigate/light/ambient_light --ez value true)"
echo "9) Light: Ambient OFF (myapp://navigate/light/ambient_light --ez value false)"
echo "10) Light: Headlights OFF (myapp://navigate/light/headlights --es value OFF)"
echo "11) Light: Headlights AUTO (myapp://navigate/light/headlights --es value AUTO)"
read -p "Select an option [1-11]: " choice

case $choice in
    1) run_all_tests ;;
    2) send_deeplink "myapp://navigate/home" "Navigate to Home" ;;
    3) send_deeplink "myapp://navigate/door/auto_lock" "Door Auto-Lock ON" "--ez value true" ;;
    4) send_deeplink "myapp://navigate/door/auto_lock" "Door Auto-Lock OFF" "--ez value false" ;;
    5) send_deeplink "myapp://navigate/seat/driver_seat_heat" "Seat Heat LEVEL 2" "--es value LEVEL_2" ;;
    6) send_deeplink "myapp://navigate/seat/driver_seat_heat" "Seat Heat OFF" "--es value OFF" ;;
    7) send_deeplink "myapp://navigate/seat/seat_lumbar" "Seat Lumbar (80,70)" "--es value 80,70" ;;
    8) send_deeplink "myapp://navigate/light/ambient_light" "Light Ambient ON" "--ez value true" ;;
    9) send_deeplink "myapp://navigate/light/ambient_light" "Light Ambient OFF" "--ez value false" ;;
    10) send_deeplink "myapp://navigate/light/headlights" "Light Headlights OFF" "--es value OFF" ;;
    11) send_deeplink "myapp://navigate/light/headlights" "Light Headlights AUTO" "--es value AUTO" ;;
    *) echo "Invalid option." ;;
esac
