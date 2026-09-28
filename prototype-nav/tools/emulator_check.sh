#!/usr/bin/env bash
set -euo pipefail
collect() {
  mkdir -p prototype-nav/qa
  timeout 10s adb pull /sdcard/Android/data/com.diaztradeinc.trxnavprototype/files/. prototype-nav/qa/ || true
  timeout 10s adb logcat -d > prototype-nav/qa/logcat.txt || true
  timeout 10s adb exec-out screencap -p > prototype-nav/qa/final-screen.png || true
}
trap collect EXIT
adb shell wm size 602x726
adb shell wm density 160
timeout --signal=TERM --kill-after=15s 180s gradle -p prototype-nav connectedDebugAndroidTest --no-daemon
