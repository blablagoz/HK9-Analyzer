#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/screenshots
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell settings put system font_scale 1.0
for screen in 0 1 2 3; do
  adb shell am force-stop com.blablagoz.hk9analyzer
  adb shell am start -W -n com.blablagoz.hk9analyzer/.MainActivity --ei screen "$screen"
  sleep 2
  adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | grep com.blablagoz.hk9analyzer
  adb shell uiautomator dump /sdcard/hk9-ui.xml
  adb pull /sdcard/hk9-ui.xml "build/screenshots/screen-$screen.xml"
  adb exec-out screencap -p > "build/screenshots/screen-$screen.png"
done
adb logcat -d -s AndroidRuntime:E > build/screenshots/crash-log.txt
if grep -q 'FATAL EXCEPTION' build/screenshots/crash-log.txt; then
  cat build/screenshots/crash-log.txt
  exit 1
fi
echo 'All four screens launched without an AndroidRuntime crash.'
