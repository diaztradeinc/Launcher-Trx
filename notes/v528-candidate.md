# v5.28 sunset system candidate

The supplied truck photo's approved sunset treatment is the hero on Home, Weather, Performance, Apps, Settings, and first-run setup. The old alpine, splash, media-stage, and weather artwork files are removed. The original album fallback and launcher icon remain in use. Existing stored theme, surface, display calibration, navigation, favorites, and media preferences retain their keys.

The in-app left navigation now has the same six destinations and Back as the Android overlay. The overlay hides on the launcher and appears above other Android app windows when Display over other apps is granted. Its buttons shrink with short displays. Native turn-by-turn navigation reserves its left edge for the overlay, uses the selected surface/accent, and has a dark End route button. The overlay cannot replace vehicle hardware controls or Android system bars.

The Weather page provides current, hourly, and five-day conditions from the device location and a refresh control. With no location or network, it shows an empty state; cached current weather remains visible. The media spectrum requests audio access when first opened, starts automatically when permission is granted and a player is active, and retains Bars, Mirror, Wave, and Orbit selections. OBDLink MX+ retains secure/insecure paired Bluetooth SPP fallback; diagnostics now identify the link mode. Standard live Mode 01 readings appear only after actual ECU responses.

Verification performed here: `npm run build` and `git diff --check` pass. The Playwright suite is updated to cover Weather (28 layouts) but could not run: Chromium is absent. The standalone Java OBD parser test could not run: `javac` is absent. Android compilation and signed APK creation require the configured CI environment. No device integration or vehicle measurements were available in this workspace.

Device gate before a final release:

1. Install over the current signed version; confirm preferences, favorites, layout at Ottocast 602×726, weather, and sunset splash on a clean install.
2. Grant overlay and optional Accessibility; open Maps, a music app, and a third-party app. Check the rail covers the old app edge in the application area, all six destinations, Back, collapse, and persistence after app switches and reboot. Confirm it does not obstruct the vehicle and Android bars.
3. Start turn-by-turn navigation, verify search, guidance, End route, accent/finish, and usable left padding while the rail is visible.
4. Pair OBDLink MX+, turn the ignition on, confirm adapter and ECU connection, changing RPM/speed/coolant and other supported PIDs, diagnostics, reconnect, and behavior after another OBD app releases the adapter.
5. Play audio in a supported app; grant audio access, verify live movement and all four visualization styles, theme colors, pause/resume, volume and transport controls. Android may restrict system output capture on this Ottocast build.
6. Open Weather with GPS and data, refresh hourly/five-day forecasts, then try loss of network and disabled location.
7. Request a pair of external apps in split screen; verify Android places both apps side by side. The OS/Ottocast controls placement, so launch-adjacent alone cannot guarantee this result.
