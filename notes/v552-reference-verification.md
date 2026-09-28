# v5.52 reference correction

Root cause: v5.49/v5.50 page styling was gated by rendering-uconnect. Phone and Custom profiles kept older geometry. The UI suite initialized Phone and did not assert parity between profiles.

Changes:
- One approved-cockpit layout applies to every display profile. Profiles retain calibration differences only.
- Full-width header, six left destinations, and a single active stripe.
- Performance: large uninterrupted truck scene; speed overlay; two columns of boost estimate, RPM, coolant, battery, intake and engine load. Additional supported fields remain in diagnostics; missing readings remain unavailable.
- Settings: fitted appearance, interior, display, motion and cockpit sections. Greeting settings moved into a dialog.
- Apps: six stable built-in destinations, separate favorite row, four-column drawer. Favorite editing now has draft, reorder, Save and Cancel.
- Custom named destinations persist with their Google place details.
- Media source cards, dialogs and first-run permission card share the dark material. Existing PremiumVisualizer.jsx and its drawing designs are unchanged.
- Native Google guidance has a header and six-destination rail. Its real map and supported guidance remain in use; the photographic road in the mockup is not a live map renderer.

Validation:
- npm run build passed.
- UI suite: 28 layouts at 602x726, 480x800, 390x680 and 800x600; zero errors, overflow or overlap findings.
- Identical page geometry checked across Phone, Uconnect and Custom.
- Saved destinations, favorite cancellation/reorder, media controls, all four live visualizer canvases, app drawer, quick pairs, display settings and weather close checked with mocked native data.
- Visual review performed from the actual rendered pages and dialogs, without posting large screenshots in chat.

Limits: browser checks use mocked Android data. NavigationActivity compilation/signing is checked by CI; actual Ottocast rendering, GPS guidance and OBD behavior require device validation. Existing compressed scene artwork is reused, not regenerated. No claim of pixel-identical artwork or photorealistic live navigation.
