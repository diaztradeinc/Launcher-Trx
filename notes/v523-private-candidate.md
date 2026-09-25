# v5.23 private candidate — Apex Instrument

This candidate began as a private branch based on the approved v5.22 main commit `b9c3f52491785c438e3bedb4d3dcac810773f6dc`. Michael approved publishing its source and triggering the permanently signed GitHub candidate build on September 25, 2026. This is a test candidate; vehicle acceptance and a public release still require actual device verification and separate approval.

## Implementation

- Added original high-resolution first-run truck artwork and weather artwork. Existing approved alpine and crimson-moon artwork remains in use. The UI uses charcoal surfaces, safe gutters, consistent rail/header sizing, and a one-time staged setup for Location, Bluetooth, media control access, and launcher role. Users may skip steps or replay setup from Settings.
- Home now has native Google map preview, weather with a subtle motion layer, Maps/Media/OBD/Phone launch targets, functioning session-backed media controls, and truthful OBD status. The Home map search is outside the native MapView rectangle to prevent a native overlay from intercepting the button.
- Media keeps its session-backed controls, supported Like action, queue, and volume. A 32-band Visualizer taps Android's output mix only after the optional audio permission is requested. The bars remain still and report an unavailable state when permission, audio output, or device support is absent. It does not record audio to a file.
- Apps retains real installed app discovery, favorites, long-press actions and search. The new picker sends Android's `FLAG_ACTIVITY_LAUNCH_ADJACENT` request; the device may instead open an app full screen. A persistent left rail and short/long press Back are available inside the launcher. No overlay permission is requested over other apps.
- Navigation's native Google UI and route logic remain; the native controls were retinted to charcoal. Performance continues to hide unreceived ECU values. Settings makes reduced motion, calibration, themes and vehicle link more accessible.
- Version is 5.23.0 / Android code 52300; the candidate artifact name is TRX-APEX-v5.23-INSTRUMENT-CANDIDATE-SIGNED-APK. The workflow requires Maps and permanent signing secrets and checks the packaged certificate against v5.22.

## Completed checks and remaining gates

- `npm run build` passed. Vite transformed 1882 modules; bundle and artwork are generated.
- 11 OBD protocol checks passed through the JDK compiler module.
- `node --check tests/ui-check.cjs` and `git diff --check` passed. The UI regression suite was extended for split request and opt-in visualizer.
- Chromium could not be installed here because the browser download returned a truncated archive; the available cloud browser blocked localhost. **The 24 viewport/page layout suite and screenshot inspection remain unrun for this candidate.** Android SDK/Gradle and the private permanent keystore are absent locally; the approved GitHub CI run is needed to build and verify the APK. No Ottocast P3 Pro was attached.
- On the device: install the permanently signed candidate, inspect Home through Settings at the actual 602×726 viewport, test first-run permissions and replay, verify live Google map/guidance, OBDLink MX+ ECU polling, Like acknowledgement in supported media players, live spectrum permission/output behavior, and whether Ottocast honors launch-adjacent split. Record screenshots and findings before release.
