# AI Walking Guide

Personal Android tour guide, Kotlin + Jetpack Compose. Russian narration, advisory walking routes, independent nearby discovery, museum photo context, and local session memory are the intended product.

**Current state:** W01 source and CI prepared; W02 official documentation reviewed with an offline PKCE protocol PoC. No map, GPS, AI requests, narration or live sign-in is enabled yet. See [progress](docs/PROGRESS.md) for verified outcomes and blockers.

## Build entirely in the cloud

Push to `main` or open a pull request: **Android debug APK** runs unit tests, lint and `assembleDebug`. Under Actions → successful run → Artifacts, download `ai-walking-guide-debug-<run>`, unzip it, and install `app-debug.apk` on Android 8.0+. Allow installation from your chosen file manager when Android asks. Debug builds are for personal testing, not Play Store distribution. Artifacts expire after 14 days; rerun the workflow to regenerate.

The manual **Android emulator smoke test** workflow verifies launch and the authentication status UI. No secrets or paid AI services are needed to build. GitHub Actions usage remains subject to the account's quota; do not enable paid overages without approval.

Optional cloud-shell command, with JDK 17 and Android SDK available:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Documentation

- [Constitution](docs/CONSTITUTION.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Implementation plan](docs/IMPLEMENTATION_PLAN.md)
- [Decisions](docs/DECISIONS.md)
- [Progress](docs/PROGRESS.md)
- [ChatGPT feasibility](docs/CHATGPT_FEASIBILITY.md)
- [Russian speech recommendation](docs/SPEECH_PROVIDER.md)

`planning/issues.json` holds W01–W08 issue specifications. `python3 scripts/create_issues.py` creates missing issues using an authenticated `gh` CLI, with dependency links. Run from a cloud shell with repository access. Never upload tokens, location history, signing keys, or account credentials to issues or artifacts.
