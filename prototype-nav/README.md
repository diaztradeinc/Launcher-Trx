# TRX APEX Navigation Lab 0.4.0

An isolated native Android/Filament prototype. App ID `com.diaztradeinc.trxnavprototype`; this does not replace the TRX APEX launcher or register as a home app.

**Current entry point:** Google Navigation trial. The user reports that the standard map now loads. This does not yet verify live route guidance or the new 3D renderer. See the authorization finding below.

**Separate Filament simulation (Setup → Open 3D simulation):** Not for driving. This scene uses no GPS, Google Navigation data or road database. All its roads and scenery are synthetic. The original low-poly pickup is a visual proxy, not a licensed RAM/TRX model. UI fidelity and vehicle detailing are prototype quality, not the photorealistic concept image.

## Controls

- START DEMO / PAUSE / RESUME: drive or pause the 480-meter simulated route.
- 1× / 2×: simulation speed; arrival countdown changes accordingly.
- 3D / MAP: chase and overhead views of the same synthetic world.
- Recenter: restore default chase camera.
- Sun: day/night lighting.
- Voice: optional local Android speech; reports unavailable speech engine.
- Red X: end simulation; RESTART resets it.
- Settings: camera distance, lighting, and prototype disclosure.
- Home: close lab. Other launcher pages remain in the separate launcher.

Filament frame submissions are capped at 30/sec. The on-screen value is a submission rate, not a GPU timing guarantee. Actual P3 Pro performance must be measured on the device. Motion uses elapsed time; background time does not advance the route.

## Reproduce

JDK 17, Gradle 8.13, Android SDK 35:

```
python3 prototype-nav/tools/generate_scene.py
gradle -p prototype-nav assembleDebug
```

Without signing environment variables local debug signing is used. CI requires the existing permanent TRX signing key, verifies its fingerprint, then runs Android instrumentation with screenshots before publishing the APK. Targets Android 8.0+ and OpenGL ES 3.0; includes arm64-v8a, armeabi-v7a, x86_64. Hardware/vendor compatibility beyond emulator still requires device testing.

## Further implementation

Approve actual device render/layout first. Then source a properly licensed detailed truck model and choose a map/routing provider whose terms permit the intended custom rendering. A real navigation engine also needs location permissions, snapped-location updates, road mesh/tile rendering, maneuvers/lane data, rerouting, location-loss behavior and device testing. The synthetic demo is not a geographic map. See `GOOGLE_3D_INTEGRATION.md` for the proposed real-map integration and unresolved verification gates.

Renderer surface and asset lifecycle follows the Apache-2.0 Filament ModelViewer example, copyright 2020 Android Open Source Project. Procedural geometry and HUD are original code.

## 0.2 visual pass

Sculpted original pickup proxy with sloped glass, tubular sports bar, widened wheel arches, tread blocks, tailgate strokes and exhausts. Continuous sandstone ridges, shoulders, reflectors and route chevrons replace the sparse test scene. Dock icons now scale independently from labels. This remains a synthetic simulation; no exact licensed TRX model or live routing is included. Version code 2 updates the separate lab app. The user reported smooth simulation tests on both phone and Ottocast; this does not validate the separate Google map renderer or live navigation.


## v0.3 — Google Navigation trial
Launcher entry now opens a Google Navigation SDK 7.9.0 viewport inside the APEX frame. The standalone package and permanent signing certificate are unchanged. The original synthetic 3D simulation remains under Setup; it is not the live map.

Features: confirmed address search (Android Geocoder, not Places autocomplete), saved destinations, real SDK routing/guidance/ETA, traffic, buildings, tilted following camera, road/satellite layers, route overview, day/night and voice preferences, end route, launcher page shortcuts. Native Google maneuver/ETA/attribution stay unobscured; custom controls occupy separate layout rows. Google standard location marker retained pending a supported custom vehicle implementation.

CI injects existing MAPS_API_KEY. Google Cloud must enable Navigation SDK for Android and billing, and authorize package com.diaztradeinc.trxnavprototype with permanent SHA-1 03:04:AF:48:B6:70:72:BE:31:3F:17:C2:D8:F7:17:6D:6C:78:D3:BC. A successful APK build does not prove key authorization or road navigation. Google instrumentation checks the real activity, permission-denied recovery and settings; the simulation tests still validate only synthetic rendering.


### Authorization finding, 2026-09-28
v0.3 runtime logs contained Google Maps and Navigation SDK authorization failures for `com.diaztradeinc.trxnavprototype`, despite passing the two UI/simulation tests. Those tests did **not** validate live navigation. Google Cloud authorization must be corrected for the existing key; a new APK is not required for a server-side restriction update. The launcher package remains unchanged.

Find now preserves startup errors, with a regression assertion for the permission-denied case. The delivery script also blocks APK publication when either Google SDK reports an authorization failure. A log without that error is not proof of route success.


## v0.4.0 — Google geographic 3D preview

Setup → Google 3D preview opens the experimental Google Maps 3D 0.2.2 renderer at the current standard-map center. Tap a road to place the original red pickup proxy. Controls offer chase/overhead, 45-degree rotation, near/wide and recenter. Back to map returns to standard Google navigation. Opening the preview is blocked while route guidance is active.

This is a geographic renderer evaluation, not live 3D navigation: no simulated journey, fake lane guidance or route is shown. The close camera uses the tapped map's altitude; the model is relative to the mesh. Google attribution remains inside an unobscured viewport. A slow/error state leaves Back to map available.

Enable **Maps 3D SDK for Android** for the same Cloud project/key; Maps/Navigation authorization does not automatically enable this service. The existing MAPS_API_KEY is used for both metadata entries. No new secret is required if that key permits all required SDKs.

The original truck-only asset is `models/apex-truck-v02.glb` (257,036 bytes), served from this public repository over HTTPS. It is not a licensed detailed RAM model. The generator deterministically exports it without the synthetic world. Preserve the versioned URL's content; change the filename for future mesh revisions.

CI now checks real 3D map readiness and a map-tap/model-placement callback, exporting its status and screenshot. A successful callback does not alone prove model visual quality; inspect the screenshot and test on Ottocast. The combined Navigation/Maps3D dependency and emulator results must pass before publishing.
