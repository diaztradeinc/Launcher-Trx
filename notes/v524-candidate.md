# v5.24 candidate: finishes, media visualizers, and navigation controls

This follows v5.23.1 and preserves its approved artwork and working OBD path.

- Settings offers Charcoal, Dark, All Black and Carbon UI finishes. These change panel and control surfaces independently from Hellfire, Titanium, Baja, Arctic and Night Ops accent themes. The finish persists after restart and is passed to the native navigation activity.
- Media cycles Bars, Mirror, Wave and Orbit. Each style uses the same optional live FFT values and selected accent. A silent or unavailable audio capture stays still; there is no simulated audio. The choice persists.
- Google Navigation SDK header styling and APEX search/route/tools controls use the selected finish and accent. The tools handle replaces the large plus circle. The map reserves a small bottom region on high-density displays so the native ETA card is less likely to sit under Ottocast's controls. Google map attribution remains visible.
- The app picker now says "Split with TRX APEX" because Android's launch-adjacent API pairs the calling launcher with the target app. To split two third-party apps, open the first app and use Android Recents' Split screen control, then choose the second. Device support and the vendor's Recents implementation decide whether this works.
- A rail over other apps needs optional Android display-over-other-apps permission and a native overlay service. It cannot replace the Ram/Uconnect system bar or claim system-level Back behavior. That separate feature is not included in this candidate.

Checks: `npm run build`, `git diff --check`, and the signed Android CI workflow. The prior Playwright layout suite cannot run locally without Chromium. Device checks: visual finishes on 602×726 window; visualizer with audio access granted and denied; native route at night/day; controls and ETA visibility near Ottocast's bottom bar; split pairing through Android Recents. Do not call these tests complete until run on the P3 Pro.
