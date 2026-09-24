# TRX APEX 5.22 private candidate — September 24, 2026

## Scope and release status

Continues the measured 602×726 Uconnect app window and earlier ebcce08 layout/navigation work. Public v5.21 previously built successfully. This v5.22 candidate is local only; publication and the permanent-signed vehicle update require Michael's release approval. No vehicle is attached to this workspace (`adb devices -l` returned no devices).

## Design and artwork

Six rebuilt pages: Home, Navigation, Sonic, Dynamics, Orbit, Studio. Approved graphite, red TRX and restrained accent direction retained. Three generated production assets: alpine TRX hero (1672×941), Sonic studio background (1672×941), crimson moon fallback album (1254×1254). Exact prompts are recorded in `v522-artwork-prompts.json`; historical reference paths identify the source used during generation and may no longer exist. Existing app identity and native truck map marker remain. Unused legacy web artwork and the stacked OEM override stylesheet were removed.

These pixel dimensions exceed the displayed artwork areas. The vehicle's DPI setting is a display-density configuration, not a claim that its screen has 600 physical pixels per inch. No resolution guarantee is inferred from a print DPI tag.

`v522-review.html` contains actual browser screenshots with explicitly identified test data, not vehicle verification. Native Google Maps cannot render inside those browser screenshots.

## Functional changes

| Area | Candidate behavior | Remaining physical check |
|---|---|---|
| Layout | Six pages fit the measured app viewport; rail and header remain separate from page content; scrollable lists and dialogs | Inspect actual Uconnect edges and keyboard at stored PHONE profile |
| Navigation | Native interactive preview, real Places search and SDK routes; day/night maps, readable toolbar, toll/shorter-route/voice/satellite preferences | Key authorization, GPS, preview placement, route guidance, recenter/layers/overview/voice/exit |
| Media | Actual session capabilities; playback, seek, stable queue IDs and Android stream volume; no fabricated queue or song | Current player exposes requested actions and acknowledges commands |
| Like | Semantic custom action or correct heart/thumb rating style; unlike clears thumb rating rather than disliking; heart follows player metadata | Like/unlike same track in player and launcher; unsupported players remain disabled |
| OBD | Waits for ELM prompt, longer protocol discovery, supported-PID polling, retry/reconnect and bounded response trace | OBDLink MX+ connection and real ECU responses with ignition/engine on |
| Telemetry | Genuine standard-PID values; adapter and ECU states distinct; unavailable values are dashes | Compare RPM, speed, coolant, battery, intake and supported boost against a trusted reading |
| Orbit | Installed apps, real launches, six favorites, search, long-press app info/uninstall, intentional empty favorites | Device app launch and Android confirmation screens |
| Themes | Five persisted accent palettes; display lighting, icon scale, artwork and geometry controls | Contrast/glare and touch usability in actual vehicle lighting |

Gear and transmission temperature are not claimed as functional: verified RAM-specific PIDs are still needed. No fabricated gear, power figure, trip ETA or unsupported performance timer is displayed. OBD is read-only; no diagnostic clears or ECU writes are introduced.

## Verification

- Production Vite build passed.
- Browser: all 24 page/viewport combinations passed at 602×726, 480×800, 390×680 and 800×600; zero runtime errors or top-level section overlaps/out-of-bounds sections.
- Browser interactions passed: all five themes, persistence, icon scale, calibration reset, media command dispatch, unsupported Like disabled, Places selection and route theme, OBD reconnect dispatch, app launch, six favorites at maximum icon size, empty favorites persistence.
- Eleven pure Java OBD parsing checks passed: echo/status, CAN header, incomplete frames, no cross-ECU byte joining, no-data/stopped and supported-PID masks.
- Screenshots inspected; software-rendered Chromium used to avoid an environment-specific SwiftShader screenshot corruption. This does not verify the vehicle GPU.
- Final Android `assembleDebug` passed. APK metadata independently confirmed package `com.diaztradeinc.trxlauncher`, version `5.22.0` / `52200`, minimum SDK 24 and target SDK 36.
- Android build uses an isolated regenerated project and authoritative native templates. Template installation now follows Capacitor sync so the final build retains the custom version, integrations and signing configuration.
- Local compile uses a dummy Maps placeholder and local debug identity. It is not a functional Maps test or a vehicle update, and must not replace the permanent-signed installed app.

## Vehicle acceptance sequence

1. After approval, build with the configured Maps key and permanent signing identity. Update in place; retain stored PHONE profile and existing settings.
2. Park the vehicle. Inspect all six pages at the measured window; test keyboard/search, longest queue titles, expanded app list, six favorites, largest icons and dialogs. Confirm no controls disappear at the edges.
3. Pair OBDLink MX+, grant Bluetooth access, disconnect competing OBD apps, start the engine. Confirm ECU live state and plausible changing PIDs. If data remains unavailable, capture the recent adapter response trace in Dynamics. Do not label an adapter-only connection as ECU success.
4. Play media in the actual chosen player. Test pause/resume, previous/next, seek, queue selection, volume and like/unlike. Confirm Like inside that player; verify capability-disabled behavior for an unsupported source.
5. With GPS and internet available, search/save Home and Work, test real map preview and a destination. Check the native toolbar, voice, layers, recenter, overview and exit. Verify turn guidance with a passenger handling interaction.
6. Switch all five themes, day/night/auto and calibration, then restart the launcher. Confirm preferences persist and the PHONE profile did not silently change.

Physical acceptance remains pending. Browser mocks and compilation are not evidence that the ECU, Google service authorization, or a third-party media app is functioning on the P3 Pro.
