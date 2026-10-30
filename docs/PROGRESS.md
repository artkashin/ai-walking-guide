# Progress — 0.2.0, 2026-10-30

## Delivery

Source: `607370aac93195d53aaf6c994cbe8d072fde8352`. App versionCode 2/versionName 0.2.0; Android 8.0+.

- **GitHub Actions build passed:** [41139927009](https://github.com/artkashin/ai-walking-guide/actions/runs/41139927009). Downloaded its reports: 19 JVM tests, zero failures/errors; lint zero errors and 20 maintenance warnings (dependency/target API, KTX suggestions, platform Exif recommendation and stopService lint notices).
- **Both emulator jobs passed:** [41139924579](https://github.com/artkashin/ai-walking-guide/actions/runs/41139924579), 7 tests on API 26 and 7 on API 35, no skipped/failed tests. Tests cover UI, stale/fresh actual Android location adapter replay, SQLite atomic reservations/restart/aliases, encrypted Keystore storage and photo resize/orientation/EXIF stripping.
- Downloaded the workflow APK through the GitHub connector, compared its SHA256SUMS and verified its APK signature with apksigner.
- APK SHA-256: `2abae1965614b066e79b55231b315de56ba9cedd7bde1211c3277affe86f9b49`.
- Direct APK release is being published by the dedicated Publish verified APK workflow. It downloads only the successful source build and requires the successful emulator run; no rebuild or private signing secret is involved.

## Implemented scope

| Milestone | Implementation and acceptance status |
| --- | --- |
| [W01 #1](https://github.com/artkashin/ai-walking-guide/issues/1) | Foundation verified and closed; original build [41134562116](https://github.com/artkashin/ai-walking-guide/actions/runs/41134562116). |
| [W02 #2](https://github.com/artkashin/ai-walking-guide/issues/2) | Official local-phone OAuth, PKCE, signed claims, secure storage, refresh/revocation, model list and SSE implemented. Live account consent/inference remains unverified. |
| [W03 #3](https://github.com/artkashin/ai-walking-guide/issues/3) | External Maps chosen/approved by user; no billed SDK. Foreground GPS/permission UI and fresh/stale replay implemented/tested. Real walking/device battery behavior still requires phone acceptance. |
| [W04 #4](https://github.com/artkashin/ai-walking-guide/issues/4) | Actual-position nearest-next advisory goals, reached-goal removal and Maps walking URL without stale origin; route/silence/replay unit tests pass. Google Maps owns path geometry/replanning. |
| [W05 #5](https://github.com/artkashin/ai-walking-guide/issues/5) | Real Wikipedia search, canonical Wikidata/page aliases, local transactional session memory and terminal recovery verified. No zones. Public English API smoke returned three real POIs; Russian API was temporarily busy and fallback is handled. |
| [W06 #6](https://github.com/artkashin/ai-walking-guide/issues/6) | Grounded Russian text generation, explicit SILENCE option, offline TTS synthesis/cache and Media3 controls implemented. Real installed voice quality/audio focus on phone remains unverified. |
| [W07 #7](https://github.com/artkashin/ai-walking-guide/issues/7) | Camera/picker/consent, sanitized images, contextual vision request and user confirmation implemented; photo privacy transformations tested on both APIs. Real model/photograph accuracy remains unverified. |
| [W08 #8](https://github.com/artkashin/ai-walking-guide/issues/8) | JVM and two-API instrumented CI, actual GPS adapter replay and APK/checksum verified. Real account, real walk, OEM battery and voice/artwork checks remain physical-phone acceptance. |

## What has NOT been established

CI does not log into the user's ChatGPT account, consume subscription usage or test an entitled real inference request. Official registration/loopback/model admission can still fail for an account/browser; the app reports failure and provides an explicit subscription connection test. No separately billed API, API key, cookie extraction, Google Cloud billing, credit purchase or cloud speech has been used.

Only the user can confirm server-side additional credits/automatic purchases are disabled. There is no reviewed request switch enforcing this. The UI requires a confirmation each process before AI requests; if the server controls cannot be disabled, leave AI requests off. See [W02 report](CHATGPT_FEASIBILITY.md).

Russian voice naturalness/availability, real-time foreground GPS on the user's OEM phone, real external Maps navigation and real museum attribution need the steps in [USER_GUIDE_RU.md](USER_GUIDE_RU.md). Artwork outputs are hypotheses, not verified attribution. Wikipedia coverage and identity metadata vary; unlinked semantic duplicates cannot be perfectly resolved by page IDs alone.

## Cloud test fixes

The original foundation setup required replacing the retired SDK tools package with platform-tools. The expanded API 35 museum UI test needed normal scrolling. The GPS replay was adjusted to respect the requested sampling interval/movement and run with a foreground Activity. Earlier failed/cancelled development runs are superseded by the successful final runs above.

## Access and scope decisions

The user installed the GitHub app on personal artkashin; publishing/issues/Actions work. The user explicitly approved external Maps without new billing and preparation of official subscription-only ChatGPT sign-in. All changes remain within ai-walking-guide. Tokens and real location/photo data are absent from CI.
