# Implementation plan

W01 and W02 begin immediately. Validate the foundation before W03–W08 implementation. Independent domain work may proceed after dependencies, but live providers require approved access.

## W01 — Android Foundation

Dependencies: none.

- [ ] Native Kotlin + Compose app launches on API 26+ with accurate feature status.
- [ ] Pinned Gradle wrapper, JDK and Android SDK; no credentials needed to build.
- [ ] GitHub Actions runs unit tests and lint, assembles debug APK successfully.
- [ ] Downloadable workflow APK and checksum; run URL recorded in docs/PROGRESS.md.

## W02 — Sign in with ChatGPT feasibility

Dependencies: W01.

- [ ] Review current official SIWC OAuth + PKCE and subscription documentation with dated sources.
- [ ] Document Android callback compatibility, registration, account scopes and security constraints.
- [ ] Offline PKCE PoC passes tests; implement live local-phone auth only after approval for account permissions.
- [ ] Attempt consented subscription text request if available; record response.completed or exact redacted blocker.
- [ ] Determine Russian speech entitlement or explicitly record it as unconfirmed; no unofficial endpoints or separately billed API.

## W03 — Google Maps walking navigation and GPS

Dependencies: W01.

- [ ] Investigate walking support in Google Maps/Routes/Navigation products and document selected approach.
- [ ] Obtain explicit approval for required billing/account permissions before enabling Google services.
- [ ] Render map and advisory walking route; foreground GPS permission denied/approximate/unavailable states work.
- [ ] Never persist precise traces remotely; CI uses fake GPS and providers without paid secrets.

## W04 — Dynamic route replanning

Dependencies: W03.

- [ ] Deviation replans from actual position to remaining goals without forced return.
- [ ] Accuracy filtering, movement throttling and stale-request cancellation handle GPS jitter.
- [ ] Navigation and POI guide stay independent; empty route does not disable nearby discovery.
- [ ] Replay tests demonstrate multiple detours, stale responses and route service failures.

## W05 — POI discovery, identity and session memory

Dependencies: W03.

- [ ] Query real POIs around actual position, including off-route candidates; no persistent zones.
- [ ] Canonical identity/alias matching handles provider duplicates conservatively.
- [ ] Persist completed/skipped session POIs locally; no automatic repeats across process restart or alias merges.
- [ ] Atomic reservation prevents concurrent repeats; late results after skip/end are discarded.
- [ ] Empty/uninteresting/uncertain results produce silence; tests cover these invariants.

## W06 — Russian narration and audio playback

Dependencies: W02, W05.

- [ ] Use an explicitly authorized text generation path; include grounded POI provenance and uncertainty.
- [ ] Evaluate installed offline Russian Android TTS voice; cloud speech only after separate approval.
- [ ] Flexible narration duration; synthesized file cache and Media3 pause/resume/skip work.
- [ ] Handle long narration, audio focus, interruptions, cancellation and cache invalidation.
- [ ] Device evaluation confirms acceptable Russian pronunciation/naturalness or records need for approved alternative.

## W07 — Museum photograph identification

Dependencies: W02, W05, W06.

- [ ] Explicit photo permission/picker and upload consent; strip EXIF and minimize image payload.
- [ ] Combine image with available museum/location/tour context using an approved vision model.
- [ ] Communicate uncertainty and let user confirm candidate artworks.
- [ ] Reuse canonical identity and session deduplication; skipped/completed artwork never auto-repeats.
- [ ] No retained museum images by default; CI uses licensed/synthetic fixtures.

## W08 — GPS replay and end-to-end validation

Dependencies: W04, W05, W06, W07.

- [ ] Deterministic replay covers route deviation, nearby off-route POIs and silence.
- [ ] Cover repeated POIs, skipped playback, process restart, alias merge and cancellation races.
- [ ] Cover museum context, denied permissions, offline/missing voice and exhausted subscription.
- [ ] GitHub Actions runs unit/integration/emulator tests without paid credentials or real traces.
- [ ] Publish installable APK, checksums, test reports and documented manual device verification.

## First milestone gate

Requires repository publication, a successful GitHub Actions run, downloadable signed debug APK, honest W02 compatibility test/blocker report and Russian speech recommendation. A local/cloud-shell build does not replace GitHub Actions validation. Account consent cannot be delegated to CI.
