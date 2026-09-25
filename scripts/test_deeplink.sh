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
    echo -e "${CYAN}------------------------------------------------------------${NC}"
    echo -e "${YELLOW}>> Test:${NC} $desc"
    echo -e "${BLUE}>> URI:${NC}  $uri"
    adb shell am start -W -a "$ACTION" -d "$uri" -p "$PACKAGE_NAME"
    echo ""
}

run_all_tests() {
    echo -e "${GREEN}============================================================${NC}"
    echo -e "${GREEN} Starting Full Deep Link & Setter Test Suite                ${NC}"
    echo -e "${GREEN}============================================================${NC}"

    # 1. Basic Navigation
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

    # 2. Intra-Category Item Anchor Navigation (No Setter)
    send_deeplink "myapp://navigate/door/auto_lock" "6. Navigate to Door -> auto_lock anchor"
    sleep 2

    send_deeplink "myapp://navigate/seat/seat_lumbar" "7. Navigate to Seat Lumbar Detail Subscreen"
    sleep 2

    # 3. Setter Mutations (?value=...)
    send_deeplink "myapp://navigate/door/auto_lock?value=false" "8. [Setter] Turn Auto-Lock OFF"
    sleep 2

    send_deeplink "myapp://navigate/door/auto_lock?value=true" "9. [Setter] Turn Auto-Lock ON"
    sleep 2

    send_deeplink "myapp://navigate/seat/driver_seat_heat?value=LEVEL_2" "10. [Setter] Set Driver Seat Heat to LEVEL 2"
    sleep 2

    send_deeplink "myapp://navigate/seat/driver_seat_heat?value=OFF" "11. [Setter] Turn Driver Seat Heat OFF"
    sleep 2

    send_deeplink "myapp://navigate/seat/seat_massage?value=WAVE" "12. [Setter] Set Massage Mode to WAVE"
    sleep 2

    send_deeplink "myapp://navigate/seat/seat_lumbar?value=80,70" "13. [Setter] Set Lumbar Support to 80% Height, 70% Depth"
    sleep 2

    send_deeplink "myapp://navigate/light/ambient_light?value=false" "14. [Setter] Turn Ambient Light OFF (External Plugin)"
    sleep 2

    send_deeplink "myapp://navigate/light/ambient_light?value=true" "15. [Setter] Turn Ambient Light ON (External Plugin)"
    sleep 2

    send_deeplink "myapp://navigate/light/headlights?value=OFF" "16. [Setter] Set Headlights to OFF (External Plugin)"
    sleep 2

    send_deeplink "myapp://navigate/light/headlights?value=AUTO" "17. [Setter] Set Headlights to AUTO (External Plugin)"
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
    send_deeplink "$1" "Custom Deep Link"
    exit 0
fi

# Interactive menu
echo -e "${GREEN}=== Car Settings Deep Link Tester ===${NC}"
echo "1) All Tests (--all)"
echo "2) Home (myapp://navigate/home)"
echo "3) Door: Auto-Lock ON (myapp://navigate/door/auto_lock?value=true)"
echo "4) Door: Auto-Lock OFF (myapp://navigate/door/auto_lock?value=false)"
echo "5) Seat: Heat LEVEL 2 (myapp://navigate/seat/driver_seat_heat?value=LEVEL_2)"
echo "6) Seat: Heat OFF (myapp://navigate/seat/driver_seat_heat?value=OFF)"
echo "7) Seat: Lumbar 80,70 (myapp://navigate/seat/seat_lumbar?value=80,70)"
echo "8) Light: Ambient ON (myapp://navigate/light/ambient_light?value=true)"
echo "9) Light: Ambient OFF (myapp://navigate/light/ambient_light?value=false)"
echo "10) Light: Headlights OFF (myapp://navigate/light/headlights?value=OFF)"
echo "11) Light: Headlights AUTO (myapp://navigate/light/headlights?value=AUTO)"
read -p "Select an option [1-11]: " choice

case $choice in
    1) run_all_tests ;;
    2) send_deeplink "myapp://navigate/home" "Navigate to Home" ;;
    3) send_deeplink "myapp://navigate/door/auto_lock?value=true" "Door Auto-Lock ON" ;;
    4) send_deeplink "myapp://navigate/door/auto_lock?value=false" "Door Auto-Lock OFF" ;;
    5) send_deeplink "myapp://navigate/seat/driver_seat_heat?value=LEVEL_2" "Seat Heat LEVEL 2" ;;
    6) send_deeplink "myapp://navigate/seat/driver_seat_heat?value=OFF" "Seat Heat OFF" ;;
    7) send_deeplink "myapp://navigate/seat/seat_lumbar?value=80,70" "Seat Lumbar (80,70)" ;;
    8) send_deeplink "myapp://navigate/light/ambient_light?value=true" "Light Ambient ON" ;;
    9) send_deeplink "myapp://navigate/light/ambient_light?value=false" "Light Ambient OFF" ;;
    10) send_deeplink "myapp://navigate/light/headlights?value=OFF" "Light Headlights OFF" ;;
    11) send_deeplink "myapp://navigate/light/headlights?value=AUTO" "Light Headlights AUTO" ;;
    *) echo "Invalid option." ;;
esac
