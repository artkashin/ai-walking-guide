# Implementation plan and delivery status

W01 is verified. W02–W08 implementation is in the 0.2.0 candidate. Automated checks and physical-phone acceptance are tracked separately in PROGRESS.md and GitHub issues. The user approved external Maps and official subscription authentication; no paid API or additional credits are authorized.

## W01 — Android Foundation

Dependencies: none.

- [x] Native Kotlin + Compose app launches on API 26+ with accurate feature status.
- [x] Pinned Gradle wrapper, JDK and Android SDK; no credentials needed to build.
- [x] GitHub Actions runs unit tests and lint, assembles debug APK successfully.
- [x] Downloadable workflow APK and checksum; run URL recorded in docs/PROGRESS.md.

## W02 — Sign in with ChatGPT feasibility

Dependencies: W01.

- [x] Review current official SIWC OAuth + PKCE and subscription documentation with dated sources.
- [x] Document Android callback compatibility, registration, account scopes and security constraints.
- [x] Offline PKCE PoC passes tests; implement live local-phone auth only after approval for account permissions.
- [ ] Attempt consented subscription text request if available; record response.completed or exact redacted blocker.
- [x] Determine Russian speech entitlement or explicitly record it as unconfirmed; no unofficial endpoints or separately billed API.

Live account/voice/photo acceptance requires the phone; simulated tests do not establish entitlement, voice naturalness or artwork accuracy.

## W03 — Google Maps walking navigation and GPS

Dependencies: W01.

- [x] Use official Google Maps URLs for walking navigation from current position, as explicitly approved by the user; no embedded billed SDK or API key.
- [x] Research and document the external-Maps scope and its limits; no new billing or account provisioning.
- [x] Implement foreground GPS with explicit location/search consent, permission-denied/approximate/unavailable states and stale-fix rejection.
- [x] Keep POI discovery independent of the goal list, and never persist precise traces remotely; CI uses synthetic GPS without paid secrets.

## W04 — Dynamic route replanning

Dependencies: W03.

- [x] Deviation replans from actual position to remaining goals without forced return.
- [x] Accuracy filtering, movement throttling and stale-request cancellation handle GPS jitter.
- [x] Navigation and POI guide stay independent; empty route does not disable nearby discovery.
- [x] Replay/unit tests demonstrate detours, remaining-goal order and walking Maps delegation without paid credentials. External Maps owns path-service failures and rerouting.

## W05 — POI discovery, identity and session memory

Dependencies: W03.

- [x] Query real POIs around actual position, including off-route candidates; no persistent zones.
- [x] Canonical identity/alias matching handles provider duplicates conservatively.
- [x] Persist completed/skipped session POIs locally; no automatic repeats across process restart or alias merges.
- [x] Atomic reservation prevents concurrent repeats; late results after skip/end are discarded.
- [x] Empty/uninteresting/uncertain results produce silence; tests cover these invariants.

## W06 — Russian narration and audio playback

Dependencies: W02, W05.

- [x] Use an explicitly authorized text generation path; include grounded POI provenance and uncertainty.
- [ ] Evaluate installed offline Russian Android TTS voice; cloud speech only after separate approval.
- [x] Flexible narration duration; synthesized file cache and Media3 pause/resume/skip work.
- [ ] Handle long narration, audio focus, interruptions, cancellation and cache invalidation.
- [ ] Device evaluation confirms acceptable Russian pronunciation/naturalness or records need for approved alternative.

Live account/voice/photo acceptance requires the phone; simulated tests do not establish entitlement, voice naturalness or artwork accuracy.

## W07 — Museum photograph identification

Dependencies: W02, W05, W06.

- [x] Explicit photo permission/picker and upload consent; strip EXIF and minimize image payload.
- [ ] Combine image with available museum/location/tour context using an approved vision model.
- [x] Communicate uncertainty and let user confirm candidate artworks.
- [x] Reuse canonical identity and session deduplication; skipped/completed artwork never auto-repeats.
- [x] No retained museum images by default; CI uses licensed/synthetic fixtures.

Live account/voice/photo acceptance requires the phone; simulated tests do not establish entitlement, voice naturalness or artwork accuracy.

## W08 — GPS replay and end-to-end validation

Dependencies: W04, W05, W06, W07.

- [x] Deterministic replay covers route deviation, nearby off-route POIs and silence.
- [x] Cover repeated POIs, skipped playback, process restart, alias merge and cancellation races.
- [ ] Cover museum context, denied permissions, offline/missing voice and exhausted subscription.
- [x] GitHub Actions runs unit/integration/emulator tests without paid credentials or real traces.
- [ ] Publish installable APK, checksums, test reports and documented manual device verification.

## Scope clarification

W03 uses external Google Maps, chosen by the user. W04 adapts the advisory order of remaining real goals from actual position; Google Maps owns pedestrian path geometry and path replanning. The app does not remotely replace the destination of an already-open Maps session. Raw straight-line distances are labeled accordingly.

W05 uses SQLite transactions instead of Room to retain terminal memory and canonical aliases. W08 tests do not contact OpenAI or spend plan usage. Final on-phone checks remain explicit acceptance work.

## Follow-up issues #10 and #9

Implement #10 first on `feat/persistent-narration-player` (PR #11), then #9 on stacked `feat/hands-free-guide`. Keep both open until required CI and device acceptance; do not merge automatically. Shared-player controls and explicit replay precede local voice command routing. Full-duplex wake/barge-in and target-phone battery acceptance are follow-up gates, not assumed properties of the MVP. Provider/verification details: [HANDS_FREE.md](HANDS_FREE.md).
