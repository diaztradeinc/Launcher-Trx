# Validation — 2026-09-29 UTC

- Native Android compile and APK packaging: passed locally with JDK 17, SDK 35, Gradle 8.13.
- Unit tests: 3 passed, no failures (token type, stale/future fix rejection, request epochs).
- User-provided public token: HTTP 200 for Mapbox Standard style, a Seattle driving route and Geocoding v6 address search. Token value is excluded from source and this report.
- Emulator: Android 10 x86_64, software graphics, 602 × 726 pixels, density 600. Setup and navigation activity initially opened, but prolonged observation caught a native SIGSEGV in libmapbox-maps.so. This is not an AndroidRuntime exception and the earlier Java-only log check was insufficient. The emulator also lacks a working internet connection.
- Investigating the native model loader separately with an offline background style. Repacked the original glTF with an explicit default scene and triangle indices; Khronos glTF validator reports zero errors and warnings. Model loading is now deferred until map style readiness. The isolated indexed-model process stayed alive with no new fatal signal during initial observation. After correcting the diagnostic label layout, the offline background and attribution rendered. The puck was undersized; adjusted its viewport scale. No new fatal signal was observed in this isolated test. This does not establish stability of online Standard navigation; actual hardware rendering and stability still require device testing.
- UI review found density-inflated Mapbox attribution controls and poor token-field contrast/masking; corrected with the widget-scoped sizing context and explicit password transformation.
- CI for the initial source commit passed compilation, tests, and permanent certificate verification. CI must pass again for the final source before delivery.
- Current permanent certificate SHA-1: 03:04:AF:48:B6:70:72:BE:31:3F:17:C2:D8:F7:17:6D:6C:78:D3:BC.

The delivered build is a device-test prototype. No claim is made of Ottocast frame rate, live GPS behavior, on-road rerouting or full mockup fidelity. Use DEVICE_TEST.md to record the next results.

## 0.1.1 startup repair candidate

- User reports both simulation and live mode force-closing in the delivered 0.1.0 APK. Treat 0.1.0 as unsuccessful on that device.
- Fixed initialization ordering risks: apply stored token in Application and setup before constructing SDK-backed navigation objects; delay camera evaluation and trip observer startup until the map style is ready.
- Custom truck now opt-in, off by default, while Standard 3D scenery remains enabled.
- Added a local crash report button with token/URL redaction, startup stage, Java stack and Android 11+ process-exit reasons. No automatic reporting.
- Local compilation and three unit tests pass with the embedded token intentionally empty, matching CI.
- Exact 0.1.0 signed APK launches in the offline emulator; device-specific online failure is not reproduced. Earlier emulator SystemUI crashes at high density are separate from this app. No claim of a confirmed device crash fix.
- Device test: install 0.1.1 as an update; leave truck off; open simulation and live GPS. If either closes, reopen setup and copy diagnostics. Record the device and Android version.

## 0.1.2 native crash diagnostics

- Device report: OnePlus CPH2551, Android 16, arm64; exit reason 5 / status 6 after Starting replay session. No Java stack. Faulting library is unknown until native trace is available.
- Added bounded, selective AOSP tombstone decoding and asynchronous local report reading. Tests cover crashing-thread selection, omission of memory/log data, unknown fields and malformed/oversized records.
- Removed automatic trip activation on map entry; replay begins after Demo returns a route and events are prepared. Live trip begins with GPS.
- Local empty-token build and six unit tests pass. No Android 16 runtime verification or claimed root-cause fix.

## 0.1.3 map size correction

- Fresh 0.1.2 device trace confirms the same native resize exception before replay (last stage Loading truck model). Last-stage labels are chronological breadcrumbs, not proof that the named component caused the native crash.
- Removed 1x1 layout placeholders; apply child bounds in parent onMeasure before the SDK receives its first measured size. Clamp map dimensions against pixel ratio and retain valid bounds during zero-size parent transitions.
- Nine local unit tests passed with an empty embedded token, including high-density/zero-size regression cases. Android compile/package passed.
- Device-specific crash resolution and complete 3D navigation still require the user's OnePlus/Ottocast test.
