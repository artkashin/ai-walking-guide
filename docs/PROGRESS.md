# Progress — 0.2.1, 2026-10-30

## 0.2.1 authentication follow-up

The user saw the 0.2.0 browser callback confirmation but the app stayed unconnected. Code inspection established premature browser success and loss of a valid identity when plan scope was absent; the exact original post-callback device failure was not observable with the old generic diagnostic.

- Source: `06e934a7fbd5dcf4378eb87043a6b1d0bb26edb8`.
- [Build 41145932167](https://github.com/artkashin/ai-walking-guide/actions/runs/41145932167) passed: 20 JVM tests, zero failures/errors; lint zero errors and 22 maintenance warnings.
- [Emulators 41145930388](https://github.com/artkashin/ai-walking-guide/actions/runs/41145930388) passed: 11 tests on each of API 26/35. Four new full-flow regressions cover no premature success/noise connections, identity-only persistence with inference blocked, rejected nonce with safe diagnostics, and cancel/replacement fencing. The successful-flow test also verifies the controller reads the stored session.
- [Publisher 41146443754](https://github.com/artkashin/ai-walking-guide/actions/runs/41146443754) passed after checking both source runs refer to the same main-branch commit. [Direct APK](https://github.com/artkashin/ai-walking-guide/releases/download/v0.2.1/ai-walking-guide-0.2.1.apk).
- Downloaded release APK; checksum and signature verified. SHA-256: `32a8eb2821983fa4d34ef9490a2f76f00ad510aede27d386b31074a7801e3324`.
- Browser completion now follows validated protected persistence; profile/plan are separate states. Resume reconciles UI state; explicit plan consent recovery uses the official flow. Signature/state/nonce checks were not weakened.
- Actual successful sign-in/inference on the user's phone still needs a retry. If it fails, only the short safe stage/error code is needed. Test flows use signed fixtures, not the user's account.
- Published 0.2.1 and 0.2.0 debug signing certificates differ. Android cannot update across them; uninstalling 0.2.0 removes its local history. This limitation is disclosed in release/user instructions; no signing key was added to the repository or account secrets.

## 0.2.0 baseline delivery

Source: `607370aac93195d53aaf6c994cbe8d072fde8352`. App versionCode 2/versionName 0.2.0; Android 8.0+.

- **GitHub Actions build passed:** [41139927009](https://github.com/artkashin/ai-walking-guide/actions/runs/41139927009). Downloaded its reports: 19 JVM tests, zero failures/errors; lint zero errors and 20 maintenance warnings (dependency/target API, KTX suggestions, platform Exif recommendation and stopService lint notices).
- **Both emulator jobs passed:** [41139924579](https://github.com/artkashin/ai-walking-guide/actions/runs/41139924579), 7 tests on API 26 and 7 on API 35, no skipped/failed tests. Tests cover UI, stale/fresh actual Android location adapter replay, SQLite atomic reservations/restart/aliases, encrypted Keystore storage and photo resize/orientation/EXIF stripping.
- Downloaded the workflow APK through the GitHub connector, compared its SHA256SUMS and verified its APK signature with apksigner.
- APK SHA-256: `2abae1965614b066e79b55231b315de56ba9cedd7bde1211c3277affe86f9b49`.
- **Direct APK published:** [v0.2.0 release](https://github.com/artkashin/ai-walking-guide/releases/tag/v0.2.0), [APK without ZIP](https://github.com/artkashin/ai-walking-guide/releases/download/v0.2.0/ai-walking-guide-0.2.0.apk), plus checksum file. Publisher [41140713343](https://github.com/artkashin/ai-walking-guide/actions/runs/41140713343) passed. It downloads only the successful source build and requires the successful emulator run; no rebuild or private signing secret is involved. The release is marked prerelease pending physical-phone acceptance.

## Implemented scope

| Milestone | Implementation and acceptance status |
| --- | --- |
| [W01 #1](https://github.com/artkashin/ai-walking-guide/issues/1) | Foundation verified and closed; original build [41134562116](https://github.com/artkashin/ai-walking-guide/actions/runs/41134562116). |
| [W02 #2](https://github.com/artkashin/ai-walking-guide/issues/2) | Official local-phone OAuth, PKCE, signed claims, secure storage, refresh/revocation, model list and SSE implemented. Live account consent/inference remains unverified. |
| [W03 #3](https://github.com/artkashin/ai-walking-guide/issues/3) | External Maps chosen/approved by user; no billed SDK. Foreground GPS/permission UI and fresh/stale replay implemented/tested. Real walking/device battery behavior still requires phone acceptance. |
| [W04 #4](https://github.com/artkashin/ai-walking-guide/issues/4) | Closed: actual-position nearest-next advisory goals, reached-goal removal and Maps walking URL without stale origin; route/silence/replay unit tests pass. Google Maps owns path geometry/replanning. |
| [W05 #5](https://github.com/artkashin/ai-walking-guide/issues/5) | Closed: real Wikipedia search, canonical Wikidata/page aliases, local transactional session memory and terminal recovery verified. No zones. Public English API smoke returned three real POIs; Russian API was temporarily busy and fallback is handled. |
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

## 2026-10-31 — User issues #10 / #9

- **#10 / [PR #11](https://github.com/artkashin/ai-walking-guide/pull/11): ready for review.** Shared pinned player, actual Media3 progress/timeline, paused backward/forward seek, stop/replay, saved-story library and explicit completed/skipped replay without resetting automatic memory. [Build/lint/22 JVM tests passed](https://github.com/artkashin/ai-walking-guide/actions/runs/41152321662); [15 instrumented tests on each of API 26 and 35 passed](https://github.com/artkashin/ai-walking-guide/actions/runs/41152316821), including real WAV playback, migration/controller invariants and narrow/large-font tap/thumb drag. Verified code: `966bb1060eb0a3966c4780ef7d8dc5d6ca4481f6`.
- **#9 / [PR #12](https://github.com/artkashin/ai-walking-guide/pull/12): incremental MVP ready for review, physical acceptance remains open.** Explicit session-only microphone foreground service; optional pinned Vosk Russian model; local wake/command ASR; shared local player commands; contextual GPT follow-ups; bounded local dialogue. [Build/lint/26 JVM tests passed](https://github.com/artkashin/ai-walking-guide/actions/runs/41152323296). [18 instrumented tests on each of API 26 and 35 passed](https://github.com/artkashin/ai-walking-guide/actions/runs/41152318783). The first API 26 attempt received a Maven dependency 404; its clean rerun passed without changing code or using another repository. Verified code: `be53987318ec963ed2635e67b2e6c5bf7bb007a6`.
- **Actual native Russian model smoke passed:** [41152323187](https://github.com/artkashin/ai-walking-guide/actions/runs/41152323187), API 35, one test loading the pinned model, checking wake vocabulary and rejecting silence with wake/full recognizers. This does not establish positive acoustic accuracy or physical power consumption.
- Downloaded the voice branch workflow APK and verified both SHA-256 against its workflow checksum and Android v2 signature with apksigner. APK is a 55,029,467-byte debug/test build from the verified source; SHA-256 `78cdad862d53084f2b5f5f3fe8b3e8826fa4bba4386844a500e1dffff44b67f4`. Optional ~45 MB voice model is downloaded separately after user consent. No new release or merge was performed; the PRs are stacked, #12 depending on #11.
- No new paid service/account, unofficial ChatGPT endpoint or microphone audio upload is introduced. Half-duplex fallback is explicit: pause via player/notification before voice interruption. Acoustic accuracy, OEM lockscreen/headsets, battery and actual subscription follow-up remain unmeasured physical-device acceptance. No user's microphone, credentials or subscription were used by CI. See [HANDS_FREE.md](HANDS_FREE.md) and the updated [Russian user guide](USER_GUIDE_RU.md).

## 2026-10-31 — Main integration and 0.3.0

At the user's explicit request, PR #11 and then PR #12 were merged into main (integration commit `87841d9`). Version 0.3.0 / code 4 packages both features. The release workflow requires successful main build and API 26/35 test runs for the same source commit before publishing a direct APK and checksum. Release notes distinguish verified CI from remaining physical acceptance and explain debug-signature update limitations. Issue #9 remains open for physical acceptance; #10 was closed by its merged PR.
