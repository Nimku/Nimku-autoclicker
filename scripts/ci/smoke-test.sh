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

# From here on, while a scenario is running, only screenshots are taken ($UI shot): dumping the UI tree
# suspends the app's accessibility service and closes its floating menu.

step "start the Simple scenario with its play button"
$UI taprow SimpleTest button_start
sleep 6
$UI shot 04-simple-started

step "open the Simple scenario actions from the floating menu (4th button)"
$UI tapwin menu 0.5 0.7
sleep 3
$UI shot 05-simple-config-dialog
echo "App window frames: $(python3 -c 'import sys; sys.path.insert(0, "scripts/ci"); import ui; print(ui.window_frames())')"

step "Simple scenario: open the Actions tab and press +"
$UI tapwin dialog 0.75 0.94
sleep 2
$UI shot 05b-simple-actions-tab
$UI tapwin dialog 0.88 0.82
sleep 3
$UI shot 05c-simple-add-action

step "Simple scenario: choose Click"
# "Click" is the 2nd row of the Action type sheet (bottom of the dialog)
$UI tapwin dialog 0.5 0.65
sleep 3
$UI shot 05d-click-position-picker

step "Simple scenario: place the click on the Phone icon of the launcher, confirm"
PHONE_X=44; PHONE_Y=481
$UI tapxy $PHONE_X $PHONE_Y
sleep 2
$UI shot 05e-click-placed
$UI tapwin menu 0.5 0.125     # check mark of the position picker
sleep 3
$UI shot 05f-click-dialog
echo "App window frames: $(python3 -c 'import sys; sys.path.insert(0, "scripts/ci"); import ui; print(ui.window_frames())')"

step "Simple scenario: save the click, then the scenario"
$UI tapwin dialog 0.893 0.043   # save button of the click dialog header
sleep 3
$UI shot 05g-after-click-saved
$UI tapwin dialog 0.893 0.043   # save button of the scenario dialog header
sleep 3
$UI shot 05h-after-scenario-saved

step "play the Simple scenario: the click must open the Phone app"
$UI tapwin menu 0.5 0.1
sleep 5
$UI shot 07-simple-playing
FOCUS=$(adb shell dumpsys window | grep -E 'mCurrentFocus')
echo "Focus after play: $FOCUS"
if echo "$FOCUS" | grep -qi "dialer"; then
  echo "CLICK CHECK: OK, the scenario tapped the Phone icon"
else
  echo "CLICK CHECK: FAILED, the Phone app is not in front"
fi
adb shell input keyevent KEYCODE_HOME
sleep 2
$UI shot 07b-home
$UI tapwin menu 0.5 0.3     # stop
sleep 4
$UI shot 08-simple-stopped

step "create a Smart scenario"
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 4
$UI tap id:add id:empty_create_button
sleep 2
$UI tap id:scenario_type_smart
set_name SmartTest
$UI dump 09-creation-dialog-smart
$UI tap id:button_save
sleep 3
$UI dump 10-list-after-smart-creation
sleep 5   # let the accessibility service bind again after the UI tree dumps

step "start the Smart scenario"
$UI taprow SmartTest button_start
sleep 5
$UI shot 11-smart-start-requested
if adb shell dumpsys window | grep -q "mCurrentFocus=.*MediaProjectionPermissionActivity"; then
  echo "(screen capture permission dialog shown, accepting it)"
  $UI tap text:"Start now" text:"Start" textc:"start now"
  sleep 3
fi
sleep 6
$UI shot 12-smart-started

step "open the scenario editor from the floating menu (3rd button)"
$UI tapwin menu 0.5 0.625
sleep 4
$UI shot 13-scenario-dialog

step "close the editor and stop"
adb shell input keyevent KEYCODE_BACK
sleep 3
$UI shot 14-after-back
$UI tapwin menu 0.5 0.375
sleep 4
$UI shot 15-smart-stopped

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
