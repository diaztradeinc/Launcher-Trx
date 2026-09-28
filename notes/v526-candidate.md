# v5.26 approved design candidate

The public v5.25 candidate is the parent. This version applies the approved layout-preserving design: refreshed UI finish colors and texture, retained red TRX hero artwork, theme-aware floating controls, and a custom TRX shield launcher icon.

The floating rail is requested during first-run setup and defaults on after Android's Display over other apps permission is granted. Existing installations see a one-time setup invitation. The Back button inside other apps uses a separately enabled Android Accessibility service with no screen-content retrieval; if disabled, the other rail buttons remain available.

The OBDLink MX+ connection path now bounds the Bluetooth socket connection to 15 seconds and exposes reconnect count and the last error under Performance diagnostics. This addresses a stalled connection and makes other device-specific failures visible. A successful vehicle link still requires verification with the paired MX+ and ECU on the Ottocast P3 Pro.

Check on-device: overlay Back with Accessibility enabled/disabled, Home/Navigation/Media/Apps actions above Android apps, collapsed rail and close, weather and Media pages, first-run permissions, 602 × 726 layout, all finish/accent combinations, installed app icon, and Bluetooth reconnect diagnostics. Keep the existing Uconnect system bar untouched.
