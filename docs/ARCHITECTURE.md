# Architecture

## Implemented foundation

One Android `app` module, Kotlin 2.1.20, AGP 8.9.2, Gradle 8.13, Java 17 bytecode, SDK 35, min SDK 26. Compose Material 3 renders a Russian status screen with an expandable authentication explanation. No permission is declared and no network service is active.

`auth/ChatGptPkce` is an offline protocol experiment: fresh verifier/state/nonce, S256 challenge, official dynamic-registration URL, strict callback validation. It deliberately performs no token exchange or sign-in. A production auth adapter needs validated OIDC signatures, one-shot attempts and lifecycle handling, secure credential storage/renewal, scope checks, consent and real Android tests before enablement.

## Intended runtime

Compose UI → session coordinator → independent navigation and guide pipelines:

- **Navigation:** foreground GPS adapter → accuracy/speed filtering → advisory route state → Google walking directions adapter. A deviation invalidates old route requests and replans from current position. Research Google Maps SDK vs Routes/Navigation SDK walking support before choosing; no assumption that Navigation SDK offers walking turn-by-turn. Map, routes and POI billing are separate approval gates.
- **Guide:** actual-position/context snapshots → proximity POI search → canonical identity resolution → session eligibility filter → relevance/confidence decision → Russian grounded text → speech cache → playback. Empty results are a normal idle state. Navigation cannot gate this search.
- **Museum:** explicit photo capture/picker → local EXIF stripping/downsampling → contextual identification → confidence/candidate confirmation → shared identity, session memory and narration pipeline. Image upload requires informed consent.

Use interfaces for `LocationSource`, `WalkingRouteProvider`, `PoiProvider`, `NarrationGenerator`, `SpeechSynthesizer`, and `PlaybackController`. Use fake providers and a deterministic clock for CI. Implement adapters only when their milestones begin.

## Data and concurrency

Room will hold local `TourSession`, `Poi`, `PoiAlias`, `SessionPoiState`, and narration cache metadata. There is no zone entity. Provider IDs take priority; coordinate/name/category matching is conservative, records provenance and supports aliases rather than merging unrelated buildings/artworks.

Session narration states: eligible → reserved → generating → playing ↔ paused → completed; skipping from any active stage marks skipped. Completed/skipped are terminal for automatic playback. Transactional reservation and unique `(sessionId, canonicalPoiId)` prevent concurrent duplicate narration. Persist transition before advancing playback, handle process death, and apply alias merges transactionally. A late generation result after skip/end-session must be discarded. Explicit manual replay can be a separate future feature.

Route request generations discard stale results. POI search requests are throttled by movement, accuracy and context; these ephemeral query radii are not persisted interest zones. Poor GPS quality pauses automatic decisions rather than inventing movement.

Speech cache key includes canonical POI, text hash, locale, voice, provider and synthesis version. Store files in app-private storage with size/age limits; remove session/location data on user request. Media3 will handle file playback, interruption/audio focus, pause/resume and progress. A synthesized file gives reliable seeking compared with restarting Android TTS directly.

## Privacy and deployment

Builds contain no AI credentials. OAuth browser and loopback callback run on the phone; tokens must never pass through GitHub Actions. Use Keystore AES-GCM and app-private no-backup storage. Never log codes, tokens, precise traces or museum images. Sign out clears credentials and cancels pending work; ending a session cancels automatic requests. Only approved requests send minimal POI context, not complete location history. No analytics by default.

Debug CI uses ephemeral debug signing; no release identity or signing key is created. Production signing, cloud billing, API key restrictions and permissions are future explicit gates.
