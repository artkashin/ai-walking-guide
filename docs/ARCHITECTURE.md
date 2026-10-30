# Architecture — 0.2.1

## Runtime

Native Kotlin/Compose, SDK 35/minSdk 26, JDK 17, Gradle 8.13. `GuideApp` owns a single `TourController` with a Main-thread coroutine scope and observable state. `GuideService` keeps an explicitly started tour alive with a location/media foreground notification while Google Maps is open. It is not boot-started or sticky. Stopping a tour cancels discovery, pending speech and museum work; no background location permission is requested.

`PhoneLocation` accepts fresh GPS/network fixes only. Approximate or inaccurate fixes can be displayed, but automatic narration requires <=60 m reported accuracy and a fix younger than 30 seconds. Search is throttled by movement/time. No position track is stored.

## Navigation and discovery remain independent

The user approved external Google Maps URLs instead of a billed embedded SDK. Selected real POIs are advisory goals. Their nearest-next order is recomputed from actual position; close goals are removed when GPS accuracy permits. Google Maps receives a walking navigation URL with destination and no pinned origin. Google Maps calculates pedestrian paths and adapts them itself. Internal goal ordering does not remotely control an already-open Maps session. Distances in the guide are straight-line distances, explicitly labeled.

Discovery queries Wikipedia independently of goals. Russian geosearch falls back to English, respects the documented 1200-character extract limit, and returns coordinates, provenance, page identity and Wikidata identity when available. Results are conservative and provider coverage is incomplete. Nearby real POIs are cached locally (maximum 2000). Empty/unusable results mean silence, not invented facts. No persistent zone entity exists.

## Local identity and narration memory

`TourStore` uses Android SQLite transactions rather than Room/KSP: session, memory, alias, cached POI and goal tables. Reservations are unique on session + canonical POI and synchronized. Wikidata IDs unify language/provider aliases when available; names/coordinates alone are not aggressively merged. Alias resolution carries completed/skipped states to the canonical identity.

Reserved -> playing <-> paused -> completed; skip is terminal. Terminal states cannot be reset by playback callbacks. On process recreation, interrupted reservations become skipped and never auto-restart. A new session is an explicit user action. Generation counters and coroutine cancellation discard late results after skip/stop; search is separate from route membership.

## AI and credentials

`ChatGptPkce` and `ChatGptAccount` implement the documented official OSS dynamic-registration flow: system browser, loopback-only callback, fresh state/nonce/S256 verifier, exact callback/client pinning and one-shot timeout. `IdTokenVerifier` verifies RS256/ES256 JWKS signatures plus issuer, audience, nonce, expiry, issue/not-before times and authorized-party binding for multi-audience tokens. Returning sign-in uses the saved client when available. Token refresh is serialized and sign-out invalidates refresh writes. Credentials are encrypted with an Android Keystore AES-GCM key and atomically stored under noBackupFilesDir. Sign-out clears local credentials immediately and attempts documented server revocation; if it cannot confirm revocation, the UI points the user to ChatGPT settings.

Inference lists account-eligible models and uses only OAuth bearer tokens on the official Responses endpoint with store:false, stream:true and array input. No API key, unofficial backend, session-cookie extraction, credit purchase or billing fallback exists. Only response.completed permits narration. Incomplete/failed streams remain failed even after partial text. HTTP errors and usage exhaustion show bounded, non-sensitive messages. No response body or token is logged.

The user approved implementing official subscription sign-in. Actual account consent still happens in the phone browser. Before AI requests, the UI requires the user to disable additional credit usage/automatic purchases in ChatGPT and confirm this. The client has no documented server-side switch to verify or enforce that setting; if it cannot be disabled, leave AI requests disabled. No claim of verified subscription entitlement is made without a real successful device request.

## Russian speech

`RussianSpeaker` requires an installed Russian voice reporting no network requirement. Text is chunked below the engine's input limit and synthesized to files. Cache keys include engine/version, voice, text and fixed synthesis settings. Media3 handles pause/resume, playlist progression, audio focus and unplugged-headphone pause. Skip cancels synthesis and playback; only completed synthesis files are published. Audio cache is capped at 100 MiB; generated text cache at 200 entries. Naturalness is device-dependent and must be evaluated on the user's phone.

## Museum

Android Photo Picker or delegated camera + narrow FileProvider grant. Upload requires separate confirmation. Images are decoded/downsampled and re-encoded as JPEG without EXIF before sending. Only image + explicit museum text + nearby place names are sent. Raw capture files are removed after processing/cancellation and on next process startup; gallery originals are not deleted. Model attribution is a hypothesis, with confidence and a mandatory user confirmation before narration. Low-confidence results cannot start narration. Normalized museum/title/artist keys prevent repeating the same confirmed identification; different uncertain spellings may require user judgment. Museum identification is not authoritative art authentication.

## Verification and limitations

JVM tests cover PKCE, returning client substitution, signed-token claims, SSE completion/failure, selection/silence, detours, replay and speech chunking. Android instrumentation tests exercise SQLite restart/alias/atomic reservations, encrypted credential storage, navigation through UI and offline GPS replay. CI requires no live accounts, real photographs, GPS traces or paid credentials. Real walking, background battery behavior, installed Russian voice quality and real OAuth/vision entitlement remain phone acceptance tests.

## Authentication completion correction (0.2.1)

The listener tolerates unrelated/empty browser connections within a bounded deadline and reads bounded headers before replying. Browser success follows verified credential publication; a fixed return link reopens the Activity without transporting codes or being trusted for identity. Account identity and plan permission are separate states. A valid identity is retained without inference scope and has a user-initiated consent recovery action. Activity resume synchronizes the UI from protected storage. Diagnostics persist only enumerated stage/error labels and allowlisted OAuth server code names, not raw responses or personal data. Publish/cancel/refresh fences prevent stale attempts from writing credentials or canceling replacement attempts.
