# Device acceptance — Mapbox Lab 0.1.0

Use controls while stationary or as a passenger.

1. Confirm app name/version and separate installation from the launcher.
2. Open simulation. Mapbox attribution must remain visible. Check for authentication/map errors.
3. Tap Demo; verify a real Seattle route and continuous truck movement, road-level route line, upcoming turn and ETA. Simulation banner must persist. Inspect truck orientation and size; these require actual renderer validation.
4. Pan/pinch then Recenter; Overview must fit the route. Day/night and scenery toggle must preserve route and camera. End must clear route and voice even during an outstanding request.
5. Try Setup after changing screen dimensions/orientation. Report viewport and density shown in device details. Check touch size and text legibility, especially on the Ottocast/Uconnect at 600 DPI.
6. Deny precise location in live mode. No demo position or live guidance should appear. Return to setup remains accessible.
7. Grant precise location; wait for a fresh GPS position. Choose a nearby address and confirm the destination. Confirm real location, correct starting road, maneuver timing and route.
8. Safely deviate with a passenger observing; verify SDK rerouting and route redraw.
9. Disable location: after eight seconds the warning must appear and spoken/maneuver guidance must stop. Restore location and check recovery.
10. Background then resume: no background speech, resumed mode stays correct. Ending a trip and opening simulation/live must not preserve the other mode's positions or route.
11. Run a 15-minute urban simulation and check heat, dropped frames, map gaps and memory behavior. A 30 fps target is not a measured result.

No build should be described as on-road verified until the live cases have been tested.
