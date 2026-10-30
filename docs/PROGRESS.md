# Progress — 2026-10-30

## W01 — Android Foundation

The Kotlin/Compose Android project is published in [artkashin/ai-walking-guide](https://github.com/artkashin/ai-walking-guide).

- SDK 35, minimum API 26, JDK 17 and checksum-pinned Gradle 8.13 wrapper.
- Russian Compose home/status screen; no location, AI or paid service enabled.
- Five offline PKCE protocol JVM tests pass in the cloud workspace.
- Cloud workspace wrapper build, lint and application/instrumentation APK compilation passed. Lint: zero errors, four dependency-version notices.
- Android backup and device-transfer exclusions are explicit. No credentials or signing keys are committed.
- **GitHub Actions build passed:** [run 41134562116](https://github.com/artkashin/ai-walking-guide/actions/runs/41134562116), source commit `7da3e13ff5df59e6b649a7e6565e181602961e99`. Five JVM tests passed (zero failures/errors/skips), lint and debug APK assembly succeeded. Downloaded CI reports confirm zero lint errors and five warnings: four dependency-version notices and an older target-SDK notice. These are follow-up maintenance items; this is a personal sideload build, not a Play Store submission.
- **Download:** [ai-walking-guide-debug-2 ZIP](https://github.com/artkashin/ai-walking-guide/actions/runs/41134562116/artifacts/13016390027), containing APK and SHA256SUMS; artifact retention 14 days. Downloaded successfully through the GitHub connector, unpacked, checksum compared and APK signature verified.
- APK SHA-256: `8ff3af1c1920d9e164822058196a7aad1be4e916e45dffad8567d1a9c7e2f0f6`.
- **Emulator smoke test passed:** [run 41134533036](https://github.com/artkashin/ai-walking-guide/actions/runs/41134533036), API 35 x86_64. It installs/launches the app and verifies the Russian title and expandable authentication-status UI. Source commit `9ef98e2` has the same app code as the APK build; the later commit only changes CI SDK setup. Physical-device installation and API 26 runtime compatibility have not been tested. The debug key is generated per runner; see README for test-build upgrade limitations.
- **W01 complete.** W02 remains open for live authentication and subscription compatibility testing.

The first Actions attempt failed because setup-android defaults included the retired `tools` package. The workflow now requests `platform-tools` explicitly.

## W02 — ChatGPT feasibility

Official SIWC OSS/local documentation was reviewed. The offline Kotlin PKCE/callback protocol PoC passes five tests. **No live Android OAuth, subscription text generation, or speech entitlement has been verified.** No consented OAuth token is available, and the live phone auth adapter remains unimplemented. An emulator UI smoke test does not test ChatGPT login. See [feasibility report](CHATGPT_FEASIBILITY.md) for exact findings, gaps, official sources and the next experiment.

Recommend installed offline Russian Android TTS for the first speech implementation, synthesizing files for cached Media3 playback. Actual Russian voice availability and naturalness require a device quality check. Cloud speech remains subject to explicit approval; see [speech recommendation](SPEECH_PROVIDER.md).

## Issues and dependencies

All issues have acceptance criteria and dependency links:

| Milestone | Issue |
| --- | --- |
| W01 Android Foundation | [#1](https://github.com/artkashin/ai-walking-guide/issues/1) |
| W02 ChatGPT feasibility | [#2](https://github.com/artkashin/ai-walking-guide/issues/2) |
| W03 Maps and GPS | [#3](https://github.com/artkashin/ai-walking-guide/issues/3) |
| W04 Adaptive routes | [#4](https://github.com/artkashin/ai-walking-guide/issues/4) |
| W05 POIs and session memory | [#5](https://github.com/artkashin/ai-walking-guide/issues/5) |
| W06 Russian narration/playback | [#6](https://github.com/artkashin/ai-walking-guide/issues/6) |
| W07 Museum identification | [#7](https://github.com/artkashin/ai-walking-guide/issues/7) |
| W08 Replay and end-to-end tests | [#8](https://github.com/artkashin/ai-walking-guide/issues/8) |

W03–W08 implementation has not started. Google service/billing choices and any additional account permissions require approval before enabling live adapters. Location history stays local by design.

## GitHub access resolved

The initial connection was installed only on AdaptiveBS and denied writes to the personal repository. At the user's request the connection was removed and reestablished. The user installed ChatGPT Codex Connector on artkashin; repository push, issue creation and Actions dispatch now work. No further GitHub permission change is needed for this milestone.
