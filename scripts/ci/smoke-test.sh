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

step "create a Simple scenario"
$UI tap id:add id:empty_create_button
$UI dump 02-creation-dialog
$UI tap id:button_save
sleep 3
$UI dump 03-list-after-simple-creation

step "start the Simple scenario"
$UI tap id:scenario_name
sleep 6
$UI dump 04-simple-started

step "stop it (press back to the app, then stop via notification-free path: force stop overlay by home)"
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 5
$UI dump 05-back-in-app

step "create a Smart scenario"
$UI tap id:add id:empty_create_button
sleep 2
$UI tap id:scenario_type_smart text:"Smart clicker"
$UI dump 06-creation-dialog-smart
$UI tap id:button_save
sleep 3
$UI dump 07-list-after-smart-creation

step "start the Smart scenario"
$UI tap textc:"Smart" id:scenario_name
sleep 4
$UI dump 08-smart-start-requested
# System screen capture dialog, if shown
$UI tap text:"Start now" text:"Start" textc:"start now" >/dev/null 2>&1 || true
sleep 6
$UI dump 09-smart-started

step "open the scenario editor from the floating menu"
$UI tap id:btn_click_list desc:"edit" || true
sleep 4
$UI dump 10-scenario-dialog

step "add an event"
$UI tap id:add id:fab_add textc:"+" || true
sleep 4
$UI dump 11-event-dialog

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
