#!/usr/bin/env bash
set -euo pipefail
collect() {
  mkdir -p prototype-nav/qa
  adb pull /sdcard/Android/data/com.diaztradeinc.trxnavprototype/files/. prototype-nav/qa/ || true
  adb logcat -d > prototype-nav/qa/logcat.txt || true
}
trap collect EXIT
adb shell wm size 602x726
adb shell wm density 160
gradle -p prototype-nav connectedDebugAndroidTest --no-daemon
