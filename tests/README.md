# Candidate checks

Run `npm run build` for the production web bundle. UI tests need Playwright and an installed Chromium (`npm install --no-save playwright`, then `npx playwright install chromium`). Run `node tests/ui-check.cjs`.

Optional environment variables: `APEX_CHROMIUM` selects an existing executable; `APEX_CHROMIUM_ARGS_MODULE` points to an ES module exporting `{args:[...]}` for that executable; `APEX_QA_OUTPUT` chooses the output directory. Default output is ignored `qa-output/`. The test supplies the development-only native bridge. No fabricated vehicle or media data is included in production.

The UI suite covers 24 page/viewport combinations, five themes, persistence, calibration reset, media command dispatch, unsupported Like, navigation search/route options, OBD reconnect dispatch, installed app launch, six favorites at maximum icon scale, and preserving intentionally empty favorites. Bounds checks cover page sections; screenshot review complements them. Browser tests cannot validate native map rendering, a player's acknowledgement, or ECU communication.

Pure Java OBD parser tests require a JDK:

```sh
javac -d /tmp/apex-obd-test native/android/java/ObdProtocol.java tests/ObdProtocolTest.java
java -cp /tmp/apex-obd-test ObdProtocolTest
```

The Android workflow builds the authoritative templates under `native/android`. The generated `android` directory is not authoritative. Use the permanent signing identity and configured Maps key for vehicle updates; an isolated local debug compile is not a distributable update.
