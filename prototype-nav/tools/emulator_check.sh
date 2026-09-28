#!/usr/bin/env bash
set -euo pipefail
collect() {
  mkdir -p prototype-nav/qa
  timeout 10s adb pull /sdcard/Download/navlab/. prototype-nav/qa/ || true
  timeout 10s adb logcat -d > prototype-nav/qa/logcat.txt || true
  timeout 10s adb exec-out screencap -p > prototype-nav/qa/final-screen.png || true
}
trap collect EXIT
adb shell wm size 602x726
adb shell wm density 160
timeout --signal=TERM --kill-after=15s 240s gradle -p prototype-nav connectedDebugAndroidTest --no-daemon

# Screenshot export is a delivery gate, not an optional artifact.
timeout 10s adb shell ls /sdcard/Download/navlab/01-chase.png /sdcard/Download/navlab/04-exit.png
