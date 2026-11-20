#!/usr/bin/env bash
# Emulator smoke test: install the APK, grant the permissions, then walk through the main screens like a user.
# Everything is printed in the job log (UI trees, visible windows, small screenshots, crash logs).
set -u

PKG=com.nimku.autoclicker
SERVICE=$PKG/com.buzbuz.smartautoclicker.SmartAutoClickerService
UI="python3 scripts/ci/ui.py"
APK=$(ls dist/*x86_64-release.apk 2>/dev/null | head -1)
[ -z "$APK" ] && APK=$(ls dist/*universal-release.apk | head -1)

step() { echo; echo "################ STEP: $* ################"; }

step "install $APK"
adb install -r -g "$APK" || { echo "INSTALL FAILED"; exit 1; }
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell appops set $PKG PROJECT_MEDIA allow
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell dumpsys deviceidle whitelist +$PKG
adb shell settings put secure enabled_accessibility_services $SERVICE
adb shell settings put secure accessibility_enabled 1
sleep 3
adb shell dumpsys accessibility | grep -iE "nimku|Bound services|Enabled services" | head -10
adb logcat -c

step "launch the app"
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 10
$UI dump 01-launch

set_name() {  # replace the text of the focused scenario name field
  $UI tap id:text_field
  adb shell input keyevent KEYCODE_MOVE_END
  for i in $(seq 1 20); do adb shell input keyevent KEYCODE_DEL; done
  adb shell input text "$1"
  sleep 1
}

step "create a Simple scenario"
$UI tap id:add id:empty_create_button
set_name SimpleTest
$UI dump 02-creation-dialog
$UI tap id:button_save
sleep 3
$UI dump 03-list-after-simple-creation

step "start the Simple scenario with its play button"
$UI taprow SimpleTest button_start
sleep 4
$UI dump 04-simple-start-requested
$UI tap text:"Start now" text:"Start" text:"Allow" text:"OK" >/dev/null 2>&1 || true
sleep 5
$UI dump 05-simple-started

step "stop the Simple scenario"
$UI tap id:btn_stop desc:stop || {
  echo "(stop button not reachable, force stopping the app)"
  adb shell am force-stop $PKG
}
sleep 2
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 5
$UI dump 06-back-in-app

step "create a Smart scenario"
$UI tap id:add id:empty_create_button
sleep 2
$UI tap id:scenario_type_smart
set_name SmartTest
$UI dump 07-creation-dialog-smart
$UI tap id:button_save
sleep 3
$UI dump 08-list-after-smart-creation

step "start the Smart scenario"
$UI taprow SmartTest button_start
sleep 4
$UI dump 09-smart-start-requested
$UI tap text:"Start now" text:"Start" textc:"start now" text:"Allow" text:"OK" >/dev/null 2>&1 || true
sleep 6
$UI dump 10-smart-started

step "open the scenario editor from the floating menu"
$UI tap id:btn_click_list || echo "(floating menu not in the UI tree)"
sleep 4
$UI dump 11-scenario-dialog

step "add an event"
$UI tap id:fab_add id:button_add id:add || true
sleep 4
$UI dump 12-event-dialog

step "crash report"
echo "---- crash buffer ----"
adb logcat -d -b crash | tail -200
echo "---- AndroidRuntime / app errors ----"
adb logcat -d '*:E' | grep -iE "AndroidRuntime|nimku|smartautoclicker|FATAL" | tail -200
echo "---- app logs (last 300) ----"
PID_LIST=$(adb shell pidof $PKG 2>/dev/null)
adb logcat -d | grep -iE "nimku|smartautoclicker|buzbuz|Overlay|Klickr" | tail -300

if adb logcat -d -b crash | grep -q "$PKG"; then
  echo "SMOKE TEST: APP CRASHED"
  exit 1
fi
echo "SMOKE TEST: NO CRASH"
