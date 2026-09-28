# v5.25 device candidate

- System settings offers an opt-in floating rail over other Android apps. Android must grant Display over other apps. The rail has Home, Navigation, Media, Apps, collapse, and close controls, plus a persistent notification. The Ram/Uconnect system bar remains owned by the vehicle. Test that tapping each rail icon restores the right launcher page and that Close removes it.
- Apps now lets you select two distinct apps for a split request. Android/Ottocast may ignore the request or open one full screen; use Android Recents → Split screen in that case. Verify this on the P3 Pro with two resizeable apps.
- Weather uses Android networking first, falls back to WebView fetch, retries after failed requests, and displays a cached last good reading. The sky visual has been restored on the home weather tile. Verify temperature and refresh with Wi-Fi on, then observe the cached value with Wi-Fi briefly off.
- Audio visualization is enabled by default after Android audio permission is granted. The permission is requested once when Media first opens, and a manual enable control remains if denied. Idle motion is decorative until live output levels arrive. Verify playing and paused states and all four styles.
- UI chrome gets distinct subtle finish textures and illuminated left navigation buttons. The route start button and native Navigation SDK route button use a dark finish with a theme-colored outline. Verify daytime and nighttime legibility on the truck display.

The web bundle builds locally. Android compilation, APK signing, and in-car behavior require the candidate CI build and device check.
