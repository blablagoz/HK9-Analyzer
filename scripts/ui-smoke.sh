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
  # A boot-time Pixel Launcher ANR can obscure a healthy foreground app.
  # Dismiss only that named system process; never dismiss our own error dialog.
  python3 - "build/screenshots/screen-$screen.xml" <<'PY'
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
labels = [n.get('text', '') for n in root.iter('node')]
if "Pixel Launcher isn't responding" in labels:
    for node in root.iter('node'):
        if node.get('text') == 'Close app':
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
            subprocess.run(['adb', 'shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2)], check=True)
            break
PY
  sleep 1
  adb shell uiautomator dump /sdcard/hk9-ui.xml
  adb pull /sdcard/hk9-ui.xml "build/screenshots/screen-$screen.xml"
  if grep -Eq "isn.t responding|keeps stopping" "build/screenshots/screen-$screen.xml"; then
    echo 'An error dialog is obscuring the app.'
    exit 1
  fi
  adb exec-out screencap -p > "build/screenshots/screen-$screen.png"
done
adb logcat -d -s AndroidRuntime:E > build/screenshots/crash-log.txt
adb logcat -d -b events | grep 'am_anr.*com.blablagoz.hk9analyzer' > build/screenshots/anr-log.txt || true
if [ -s build/screenshots/anr-log.txt ]; then
  cat build/screenshots/anr-log.txt
  exit 1
fi
if grep -q 'FATAL EXCEPTION' build/screenshots/crash-log.txt; then
  cat build/screenshots/crash-log.txt
  exit 1
fi
echo 'All four screens launched without an AndroidRuntime crash.'
