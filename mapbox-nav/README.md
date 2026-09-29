# TRX APEX Mapbox Lab 0.2.0

Standalone Android prototype: `com.diaztradeinc.trxmapboxlab`.
It installs alongside the existing launcher and Google navigation lab.

## First test

1. Install the device-test APK and open **TRX Mapbox Lab**.
2. A privately configured build has the public token filled in. Otherwise paste your own Mapbox `pk.` token. Secret `sk.` tokens are rejected.
3. Choose **Open 3D route simulation**, wait for the map, then tap **Demo**, inspect the preview, and tap **Start**.
4. Keep the experimental truck checkbox off for the first test. Check the route, default location marker, buildings, turn banner, voice, overview and End. The bundled truck can be enabled separately after the base map is stable.
5. Use **Setup → Return to token / mode setup** and choose **Open live GPS test** for a separate GPS session. Grant precise location. Find an address, select a result, preview the route, then tap Start. Long-pressing the map also selects a destination.

Simulation always starts in Seattle and never consumes live GPS. Live mode never silently substitutes simulated positions. This version pauses the trip session when backgrounded; keep it foregrounded for testing. End clears guidance and ignores late route responses. Stale live positions suppress spoken and displayed instructions after eight seconds.

## Startup repair candidate (0.1.1)

Token initialization now occurs before navigation activity construction. Camera evaluation and observer/trip startup wait for a loaded map style. The experimental truck is opt-in. Setup includes local, redacted crash diagnostics with Android process-exit reasons where available; nothing is uploaded automatically. The device-reported force close is not yet root-caused without its crash report.

## Included

- Mapbox Navigation SDK 3.31.1 and its compatible native map dependency.
- Mapbox Standard 3D scene with day/night lighting and a scenery toggle.
- Original red pickup proxy, bundled locally; no model-hosting dependency.
- Road-snapped SDK location provider, adaptive following/overview camera.
- Real route requests, SDK rerouting, maneuver arrows, remaining distance/time and ETA.
- Android English text-to-speech for SDK voice announcements.
- Mapbox Geocoding v6 user-submitted US address/city search. This is not POI autocomplete. Temporary search results are not saved.
- Separate Mapbox token/mode setup and device dimensions diagnostics.
- Pixel-scaled HUD and separate density-aware widget context for Ottocast's high logical DPI. The map renders natively; the app does not change system DPI.
- Mapbox attribution remains inside an unobscured map viewport.

## Honest limits

This is a device-test prototype, not a tested replacement for a navigation app. A passing compilation or server API check does not establish map rendering, correct vehicle orientation, on-road behavior, location source quality or Ottocast performance. The pickup is our existing original low-poly proxy, not a licensed detailed RAM model. 3D Lanes, lane-specific vehicle placement and accurate elevated road meshes are not included. Coverage depends on Mapbox. Background navigation and business/POI autocomplete are not included. Saved addresses contain user-entered text only and are searched again when used.

## Build

JDK 17, Android SDK 35, Gradle 8.13:

```sh
gradle -p mapbox-nav testDebugUnitTest assembleDebug
```

An optional `MAPBOX_PUBLIC_TOKEN` environment variable fills the setup field through a generated resource. Its value is never checked into source. Public mobile access tokens are extractable from APKs by design; use a dedicated app token and account usage controls. Never supply a secret token in this variable.

With `APEX_KEYSTORE_PATH` and `APEX_KEYSTORE_PASSWORD`, debug/device-test builds use the existing permanent key alias `trxapex`. Without them Gradle uses local Android debug signing. Signing type must be stated when delivering a build; retain the same certificate for upgrades.

Pure unit tests cover stale/future location rejection, public-vs-secret token input and asynchronous request invalidation. Additional device validation is required as listed in `DEVICE_TEST.md`.

## Source lineage

Based on branch `prototype/v0.1-native-3d`, commit `f690ee9e906273e25931c5e80d80dc5456ce24b7`. No Google navigation or launcher implementation was changed. The bundled truck originates in `prototype-nav/tools/generate_scene.py` and `prototype-nav/models/apex-truck-v02.glb`.

Mapbox API usage patterns were checked against its official Android Navigation examples (CC0):
https://github.com/mapbox/mapbox-navigation-android-examples

Provider references:
- https://docs.mapbox.com/android/navigation/guides/install/
- https://docs.mapbox.com/map-styles/reference/standard/
- https://docs.mapbox.com/api/search/geocoding/

## Native crash investigation (0.1.2)

The supplied OnePlus Android 16 report identifies REASON_CRASH_NATIVE / signal 6 after replay startup, without a Java exception. It does not identify the faulting library. This build reads Android 12+ native tombstone summaries (abort message and crashing-thread frames only), locally and on demand. Open View / copy crash report immediately after updating to recover a retained earlier crash. No need to start navigation first.

Opening the map no longer automatically starts a trip session. Demo requests a route, prepares replay events and then starts replay; GPS starts live tracking. This separates map rendering from session activation for diagnosis. A confirmed native-crash fix is still pending the detailed trace.

Schema reference: https://android.googlesource.com/platform/system/core/+/refs/heads/main/debuggerd/proto/tombstone.proto

## Map surface sizing fix (0.1.3)

The device trace reports an uncaught native `failed to resize: size is empty` exception. The HUD layout was inserting MapView at 1x1 physical pixels, then resizing it from a post-layout listener. This is unsafe at high map pixel ratios. MapView now receives realistic initial dimensions, child bounds are applied before measurement, and transient dimensions are clamped to at least two logical map pixels. Existing layout proportions and map pixel ratio are preserved. The local report also includes measured map dimensions and ratio. Device confirmation is still required.

## Complete foreground trip flow (0.2.0)

Preserves the working 0.1.3 map surface layout. Adds route preview with time/distance, provider-returned alternatives, explicit Start, turn list through Trip, end-trip confirmation, persistent arrival display, Home/Work and named saved addresses, plus persistent day/night, scenery and voice preferences. Only user-authored address strings are saved; temporary geocoder labels and coordinates are never persisted. Saved addresses are resolved again online. Alternatives are offered only when returned by Mapbox.

Device acceptance: preview and cancel a demo, choose an alternative if available, start and finish demo, verify arrival persists; save/edit/remove Home; restart and verify preferences; connect precise live GPS then preview/start a short trip; background/resume and confirm the documented foreground-only behavior. This is a feature-complete foreground test build, not an assertion of road validation or background guidance.
