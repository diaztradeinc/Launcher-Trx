# Google navigation and a geographic 3D cockpit

Status, 2026-09-28: v0.4.0 implements the isolated geographic 3D preview. The user reports the standard map now loads. The live headless guidance → 3D bridge remains pending; the new renderer is not yet Ottocast-verified.

## Current failure

The v0.3 emulator log reports authorization failures from both Google Maps and Navigation for the permanent Nav Lab package/certificate. v0.3.1 compiles and passes the UI/simulation tests, but the authorization gate correctly blocks APK delivery. Those UI tests do not establish successful SDK initialization, routing or real map rendering.

The exact Cloud setting responsible is not established. Check the key actually injected from the repository's MAPS_API_KEY secret against its owning Cloud project, enabled APIs, API restrictions, Android package/certificate restrictions, and billing. Do not assume an Android restriction is the sole cause. Cloud console access was unavailable in this session.

v0.3.2 adds a non-secret SHA-256 credential identifier in CI and Setup, trims accidental surrounding whitespace consistently, rejects missing/malformed CI credentials and redacts Google keys from uploaded text reports. No client change can grant missing server-side authorization.

## Proposed implementation

1. **Routing:** headless Google Navigation SDK, using the existing signed package. Road-snapped position and bearing drive the vehicle pose. The documented turn-by-turn service feed supplies maneuver, lane, distance and ETA state. Route change callbacks replace the remaining route geometry. Clear stale route/guidance on reroute or stop; show loss of location rather than continuing fictional travel.
2. **Geographic scene:** evaluate Google's Maps 3D SDK for Android 0.2.2. Its Map3DView supplies Google terrain/imagery; GoogleMap3D supports route polylines, models and a controlled camera. This is a different renderer from the existing NavigationView and from the synthetic Filament demo.
3. **Truck:** add a licensed, optimized glTF/GLB model once with ModelOptions; update the returned Model's position and orientation. Remove it at teardown. Specify the altitude mode explicitly and test bridges/tunnels/terrain alignment. Do not recreate the entire truck every GPS callback. The original demo truck is only a stylized proxy.
4. **Route and camera:** draw the actual route geometry in blue, update it on reroutes, follow the snapped pose with heading interpolation across 359/0 degrees. Derive altitude from an appropriate map/terrain source; zero altitude and GPS altitude are not interchangeable with ground height. Street-level chase distance, geometry quality and occlusion require device tests.
5. **APEX HUD:** preserve the approved dock, maneuver card and ETA layout around the map with Google attribution visible. Lane/sign text comes from real guidance. Never invent physical gantries or exact lane placement where the feed does not provide them.

## Evidence and limits

- Official headless mode documentation supports omitting NavigationView/SupportNavigationFragment while obtaining navigation callbacks: https://developers.google.com/maps/architecture/navsdk-headless-mode
- Turn-by-turn feed: https://developers.google.com/maps/documentation/navigation/android-sdk/tbt-feed
- Maps 3D overview explicitly supports glTF models, polylines and camera animation: https://developers.google.com/maps/documentation/maps-3d/android-sdk/overview
- Setup requires enabling **Maps 3D SDK for Android**, plus metadata `com.google.android.geo.maps3d.API_KEY`. Existing Navigation access does not imply this separate service is enabled: https://developers.google.com/maps/documentation/maps-3d/android-sdk/setup
- Artifact inspected: `com.google.android.gms:play-services-maps3d:0.2.2`. Its AAR manifest declares min SDK 23, target 35 and GLES 2.0; the existing app requires SDK 26/GLES 3.0. Its POM uses Kotlin 2.3.0 and does not directly depend on the conventional play-services-maps artifact. These checks show no obvious minimum-version mismatch, **not** proof of combined dependency or runtime compatibility. Full Gradle resolution, build, Play services module loading and Ottocast rendering remain unverified.
- Mutable model API: https://developers.google.com/maps/documentation/maps-3d/android-sdk/reference/com/google/android/gms/maps3d/model/Model
- Official sample repository inspected at `7c9acb7f59ef9a8453aa9115663df35572b1567f`: https://github.com/googlemaps-samples/android-maps3d-samples . Some samples are scaffolds; use the SDK reference as the API authority.
- SDK 0.2.2 is **experimental/pre-GA**. Coverage and ground-level quality are not guaranteed to match the concept images: https://developers.google.com/maps/documentation/maps-3d/android-sdk/release-notes
- Applicable Google terms and attribution must be checked for the combined product. Headless support alone is not blanket permission to show Google navigation data on a third-party geographic map. This proposal uses Google's geographic renderer: https://cloud.google.com/maps-platform/terms/maps-service-terms

## Delivery gates

First establish successful Google initialization and route calculation with the existing signed credential. Then compile an isolated Google 3D view alongside the Navigation SDK and verify real tiles load. Add the truck/route/HUD bridge only after renderer initialization is demonstrated. Test route replay, rerouting, location loss, background/resume and renderer cleanup. Test on Ottocast with sustained frame timing, memory and thermal observations before treating it as a replacement for the current map. Retain a working standard Google view as an explicit fallback, with a visible reason when 3D cannot initialize.

Do not deliver another APK labelled verified merely because compilation or the synthetic demo succeeds.
