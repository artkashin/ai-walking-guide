# Sign in with ChatGPT feasibility — 2026-10-30

## Result

**Promising official protocol path; Android end-to-end compatibility and this account's subscription entitlement remain UNVERIFIED.** The 0.2.0 build now includes a local-phone OAuth adapter, encrypted token store, ID-token verification, refresh/revocation, model listing and streamed text/image requests. The user approved preparing this integration, but actual account consent and a successful subscription request still require the phone browser; neither has been performed in CI.

The official OSS guide now documents dynamic registration for open-source/local apps. It uses a public OAuth client with no secret, Authorization Code + S256 PKCE, system browser, stable per-install host ID, and an HTTP `127.0.0.1` callback. These primitives can be implemented on Android, but the sources reviewed do not explicitly certify an Android SDK, package-signature redirect, custom scheme or Android App Link alternative. A real-device test is needed to check browser-to-loopback reachability, app lifecycle and registration admission. The commercial website/plugin trial restrictions should not be conflated with this separate OSS flow.

## Official contract examined

- First registration starts with `dynamic_agent_client`, the actual application name, and a stable opaque host identifier. Retain the issued per-user/workspace client ID; never borrow another app's client ID.
- Request identity scopes separately from plan-use scopes. The latter include `offline_access`, `resource.invoke` and `chatgpt.tokens.use.direct`; receiving an ID token alone is insufficient.
- Bind fresh state/nonce/verifier to one attempt and exact loopback redirect. Validate state, then exchange the code at the documented token endpoint using the issued client ID. Verify ID-token signature/JWKS, issuer, audience, expiry and nonce before accepting identity.
- Use the account-specific model catalog. Eligible HTTP inference uses the official `/v1/responses`, array input, `store: false`, `stream: true`. Success requires `response.completed`; partial text is not success. Respect missing scopes, refusal, expiry, revocation and usage exhaustion without paid fallback.
- The preview documents image inputs for compatible models, making museum identification plausible. Audio/video input and transcription are excluded. It does not establish an entitlement for `/v1/audio/speech`, Realtime or Russian audio output. ChatGPT voice inside ChatGPT is not evidence of a third-party speech entitlement.
- SIWC terms require protected local token persistence and user-controlled runtime. Do not store SIWC tokens in CI or a shared backend. Background activity requires express consent.

## Proof of concept and test inventory

Implemented: PKCE and client/callback pinning, loopback listener, browser launch, token exchange, JWKS verification of signed identity claims, Keystore-encrypted no-backup token store, serialized refresh, server revocation attempt on sign-out, model catalog and Responses SSE adapter. Tests use generated fixture keys and synthetic events, never a real account.

A completed real device sign-in and generation are still required to establish Android/browser compatibility and account admission. CI tests do not impersonate a subscriber and do not substitute for that verification. The UI includes an explicit subscription connection test.

| Check | Outcome |
| --- | --- |
| Official OAuth + PKCE flow documented | Yes, OSS/local flow |
| Android-specific official support statement found | No, in sources reviewed |
| Kotlin protocol, OIDC and inference adapters | Implemented with synthetic tests; execution status in PROGRESS.md |
| Real Android browser/loopback sign-in | Not run; no device/browser session available |
| Subscription-authorized text request | Not run; no consented OAuth token |
| Russian speech subscription entitlement | Not established by documentation |
| Paid API calls / session credential extraction | None |

## Next compatibility experiment

The user approved preparing identity/plan-use sign-in and the local-phone adapter is implemented with Keystore-backed no-backup storage. Complete the following on-phone acceptance checks before claiming live compatibility. Start a one-shot listener on 127.0.0.1 before launching a Custom Tab; bind only loopback, enforce timeout/size limits and lifecycle cancellation. Use issuer discovery/JWKS over HTTPS; do not weaken global Android cleartext policy. Verify how the browser handles loopback HTTP on the chosen Android versions.

On a real Android device test API 26 and a current API, Chrome/default-browser return, rotation, background/process death, user denial, changed state, expired code, revoked/rotated tokens and unavailable subscription. Do not keep state/verifier in saved-instance bundles. After consent, select an available model and submit a minimal Russian prompt without location. Record only status/event names and whether `response.completed` arrived. Keep tokens and response headers out of reports. If Android callbacks are rejected, record the exact redacted error and request official guidance rather than substituting an undocumented redirect.

Stop if permission would allow automatic purchases or separately billed credits; seek approval first. The initial experiment must use included plan usage only.

## Sources

Official pages accessed 2026-10-30; availability and preview behavior may change.

1. [OSS/local overview](https://developers.openai.com/siwc/token-sharing-open-source)
2. [Registration and sign-in](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)
3. [Models and inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
4. [Preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)
5. [Using your plan in other apps](https://help.openai.com/en/articles/20001542-using-your-chatgpt-plan-in-other-apps-and-sites)
6. [SIWC terms](https://openai.com/policies/sign-in-with-chatgpt-terms/)
7. [Website integration and partner availability](https://developers.openai.com/siwc/website)

## Included-plan-only control

No reviewed OAuth request flag enforces a no-extra-credits policy. Before requests the app requires the user to disable additional credits/automatic purchases in ChatGPT settings and confirm that they did so. This is a user-controlled gate, not server-side billing verification. If that control is unavailable, keep AI requests disabled. The app neither purchases credits nor falls back to an API key.

## Phone feedback and 0.2.1 correction

The user tested 0.2.0 and observed the local browser message “Вход получен”, followed by an unconnected app. That establishes that the browser reached the Android loopback callback on that phone. It does not establish successful token exchange, OIDC validation, protected persistence or subscription inference: 0.2.0 incorrectly emitted that message before those steps.

Two defects were established by code inspection: premature browser confirmation, and discarding a verified identity if chatgpt.tokens.use.direct was not granted. The specific post-callback failure on the user's device is not yet known because the old diagnostic hid the stage.

0.2.1 delays confirmation until verification and storage, distinguishes identity from plan permission, provides safe per-stage diagnostics, requests explicit plan consent only when selected by the user, and rereads the stored session on Activity resume. The added aiwalkingguide://auth-complete link only reopens the UI; it is not an OAuth callback, carries no credential and cannot set authentication state. The registered OAuth callback remains HTTP 127.0.0.1 with PKCE/state/nonce and validated JWT signatures. A no-scope identity cannot call inference. Cancellation/replacement is fenced before storage and old cleanup cannot cancel a new attempt.

Regression tests exercise the real Android callback listener, signed fixture tokens, protected storage and UI state reconciliation through an injected issuer transport. They do not access a real account or prove the user's live subscription entitlement. If the phone still cannot finish, only the displayed stage and short diagnostic code should be shared; never callback URLs, codes, tokens or cookies.

Official source reaffirming identity-only sign-in retention and explicit consent recovery: [Errors and recovery](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery).
