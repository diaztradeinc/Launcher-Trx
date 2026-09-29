# Validation — 2026-09-29 UTC

- Native Android compile and APK packaging: passed locally with JDK 17, SDK 35, Gradle 8.13.
- Unit tests: 3 passed, no failures (token type, stale/future fix rejection, request epochs).
- User-provided public token: HTTP 200 for Mapbox Standard style, a Seattle driving route and Geocoding v6 address search. Token value is excluded from source and this report.
- Emulator: Android 10 x86_64, software graphics, 602 × 726 pixels, density 600. Setup and navigation activity initially opened, but prolonged observation caught a native SIGSEGV in libmapbox-maps.so. This is not an AndroidRuntime exception and the earlier Java-only log check was insufficient. The emulator also lacks a working internet connection.
- Investigating the native model loader separately with an offline background style. Repacked the original glTF with an explicit default scene and triangle indices; Khronos glTF validator reports zero errors and warnings. Model loading is now deferred until map style readiness. The isolated indexed-model process stayed alive with no new fatal signal during initial observation. A full-screen diagnostic label obscured the surface; corrected its layout before further visual checks. This does not prove the earlier native crash is fixed; actual hardware rendering and stability still require device testing.
- UI review found density-inflated Mapbox attribution controls and poor token-field contrast/masking; corrected with the widget-scoped sizing context and explicit password transformation.
- CI for the initial source commit passed compilation, tests, and permanent certificate verification. CI must pass again for the final source before delivery.
- Current permanent certificate SHA-1: 03:04:AF:48:B6:70:72:BE:31:3F:17:C2:D8:F7:17:6D:6C:78:D3:BC.

The delivered build is a device-test prototype. No claim is made of Ottocast frame rate, live GPS behavior, on-road rerouting or full mockup fidelity. Use DEVICE_TEST.md to record the next results.
