# v5.28.1 screenshot correction

Reviewed seven device screenshots from v5.28. The weather control at the right of the header was an opaque gray block; it is now a transparent icon and temperature. Weather repeated the Home truck artwork in its hero and current-condition card; it now has a condition-aware sky, restrained animated rain when relevant, editorial forecast text, and icons that follow each reported forecast condition. The Home artwork remains untouched.

The old first-run permission setup remains available for new installations. A separate themed launch splash now appears for 1.8 seconds on each fresh app process start, including upgrades with existing setup preferences. It does not re-request setup permissions on every launch. The native map preview is hidden while that splash or the floating-navigation setup dialog is displayed so it cannot draw over either surface.

Source checks: `npm run build` and `git diff --check` pass. The layout suite now checks the splash lifecycle and Weather's unique hero; running it requires a Playwright Chromium binary unavailable locally. Confirm the header and Weather layout on the Ottocast and the permissions overlay after installing this candidate.
