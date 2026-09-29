# Validation — 2026-09-29 UTC

- Native Android compile and APK packaging: passed locally with JDK 17, SDK 35, Gradle 8.13.
- Unit tests: 3 passed, no failures (token type, stale/future fix rejection, request epochs).
- User-provided public token: HTTP 200 for Mapbox Standard style, a Seattle driving route and Geocoding v6 address search. Token value is excluded from source and this report.
- Emulator: Android 10 x86_64, software graphics, 602 × 726 pixels, density 600. Setup and navigation activity opened without an AndroidRuntime crash. The emulator did not load the live map over its network; map rendering, model orientation and simulated movement are therefore unverified.
- UI review found density-inflated Mapbox attribution controls and poor token-field contrast/masking; corrected with the widget-scoped sizing context and explicit password transformation.
- CI for the initial source commit passed compilation, tests, and permanent certificate verification. CI must pass again for the final UI fixes before delivery.
- Current permanent certificate SHA-1: 03:04:AF:48:B6:70:72:BE:31:3F:17:C2:D8:F7:17:6D:6C:78:D3:BC.

The delivered build is a device-test prototype. No claim is made of Ottocast frame rate, live GPS behavior, on-road rerouting or full mockup fidelity. Use DEVICE_TEST.md to record the next results.
