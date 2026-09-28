# v5.27 rail and OBD candidate

The floating rail covers the left edge of the Android application area with the six destinations present in the launcher: Home, Navigation, Media, Performance, Apps, and Settings. It adds Back (requires the optional Accessibility control) and Close. Closing persists the disabled state until the Settings toggle re-enables it. The overlay hides while TRX APEX is foreground so only the built-in rail appears there. Android's `TYPE_APPLICATION_OVERLAY` remains below system and vehicle controls and cannot replace the Ottocast or Uconnect bars.

The OBDLink MX+ RFCOMM connection attempts authenticated SPP first, then Android's documented insecure SPP socket when the first attempt fails. Both attempts have bounded timeouts and stage-specific diagnostics. Reconnects back off after repeated failures; this avoids rapid adapter contention. If both attempts fail, check whether another app or another paired device owns the adapter before retrying.

Verify on the Ottocast with the signed candidate: open another Android app and try all destinations, Back with Accessibility enabled, Close and Settings re-enable, launcher overlay hiding, and the MX+ diagnostics with the truck running. Do not report the OBD link as confirmed live until PIDs are observed on the actual adapter.
