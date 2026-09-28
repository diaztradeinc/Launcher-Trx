# TRX APEX 3D Lab 0.2

An isolated native Android/Filament prototype. App ID `com.diaztradeinc.trxnavprototype`; this does not replace the TRX APEX launcher or register as a home app.

**Simulation only. Not for driving.** No GPS, Google Navigation data, road database, API key, or internet permission. All roads and scenery are synthetic. The original low-poly pickup is a visual proxy, not a licensed RAM/TRX model. UI fidelity and vehicle detailing are prototype quality, not the photorealistic concept image.

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

JDK 17, Gradle 8.9, Android SDK 35:

```
python3 prototype-nav/tools/generate_scene.py
gradle -p prototype-nav assembleDebug
```

Without signing environment variables local debug signing is used. CI requires the existing permanent TRX signing key, verifies its fingerprint, then runs Android instrumentation with screenshots before publishing the APK. Targets Android 8.0+ and OpenGL ES 3.0; includes arm64-v8a, armeabi-v7a, x86_64. Hardware/vendor compatibility beyond emulator still requires device testing.

## Further implementation

Approve actual device render/layout first. Then source a properly licensed detailed truck model and choose a map/routing provider whose terms permit the intended custom rendering. A real navigation engine also needs location permissions, snapped-location updates, road mesh/tile rendering, maneuvers/lane data, rerouting, location-loss behavior and device testing. Do not connect Google Navigation data to this synthetic/non-Google world.

Renderer surface and asset lifecycle follows the Apache-2.0 Filament ModelViewer example, copyright 2020 Android Open Source Project. Procedural geometry and HUD are original code.

## 0.2 visual pass

Sculpted original pickup proxy with sloped glass, tubular sports bar, widened wheel arches, tread blocks, tailgate strokes and exhausts. Continuous sandstone ridges, shoulders, reflectors and route chevrons replace the sparse test scene. Dock icons now scale independently from labels. This remains a synthetic simulation; no exact licensed TRX model or live routing is included. Version code 2 updates the separate lab app. Phone smoothness was reported by the user for 0.1; P3 Pro performance remains unverified.
