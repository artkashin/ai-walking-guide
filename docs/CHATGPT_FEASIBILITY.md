# Sign in with ChatGPT feasibility — 2026-10-30

## Result

**Promising official protocol path; Android end-to-end compatibility and this account's subscription entitlement remain UNVERIFIED.** This is a documentation investigation plus an offline Kotlin protocol PoC, not a successful sign-in test. No account permissions have been granted and no inference request has been made.

The official OSS guide now documents dynamic registration for open-source/local apps. It uses a public OAuth client with no secret, Authorization Code + S256 PKCE, system browser, stable per-install host ID, and an HTTP `127.0.0.1` callback. These primitives can be implemented on Android, but the sources reviewed do not explicitly certify an Android SDK, package-signature redirect, custom scheme or Android App Link alternative. A real-device test is needed to check browser-to-loopback reachability, app lifecycle and registration admission. The commercial website/plugin trial restrictions should not be conflated with this separate OSS flow.

## Official contract examined

- First registration starts with `dynamic_agent_client`, the actual application name, and a stable opaque host identifier. Retain the issued per-user/workspace client ID; never borrow another app's client ID.
- Request identity scopes separately from plan-use scopes. The latter include `offline_access`, `resource.invoke` and `chatgpt.tokens.use.direct`; receiving an ID token alone is insufficient.
- Bind fresh state/nonce/verifier to one attempt and exact loopback redirect. Validate state, then exchange the code at the documented token endpoint using the issued client ID. Verify ID-token signature/JWKS, issuer, audience, expiry and nonce before accepting identity.
- Use the account-specific model catalog. Eligible HTTP inference uses the official `/v1/responses`, array input, `store: false`, `stream: true`. Success requires `response.completed`; partial text is not success. Respect missing scopes, refusal, expiry, revocation and usage exhaustion without paid fallback.
- The preview documents image inputs for compatible models, making museum identification plausible. Audio/video input and transcription are excluded. It does not establish an entitlement for `/v1/audio/speech`, Realtime or Russian audio output. ChatGPT voice inside ChatGPT is not evidence of a third-party speech entitlement.
- SIWC terms require protected local token persistence and user-controlled runtime. Do not store SIWC tokens in CI or a shared backend. Background activity requires express consent.

## Proof of concept and test inventory

Implemented `ChatGptPkce` builds the documented initial URL without launching it. Tests cover the RFC 7636 challenge vector, randomness, official hostname, accepted issued-client callback, and rejection of state mismatch, repeated query keys, access denial, callback host/port/path changes and placeholder client IDs.

Not implemented: loopback listener, browser launch, token exchange, OIDC verifier, secure token store/refresh, HTTP inference/SSE adapter. This is intentionally a protocol-only PoC until the account-permission gate and device-testing path are resolved. Offline tests do not verify OpenAI admission or Android networking.

| Check | Outcome |
| --- | --- |
| Official OAuth + PKCE flow documented | Yes, OSS/local flow |
| Android-specific official support statement found | No, in sources reviewed |
| Kotlin offline protocol experiment | Implemented; execution status in PROGRESS.md |
| Real Android browser/loopback sign-in | Not run; no device/browser session available |
| Subscription-authorized text request | Not run; no consented OAuth token |
| Russian speech subscription entitlement | Not established by documentation |
| Paid API calls / session credential extraction | None |

## Next compatibility experiment

After explicit user approval for identity and plan-use permissions, implement the local-phone adapter with Keystore-backed no-backup storage. Start a one-shot listener on 127.0.0.1 before launching a Custom Tab; bind only loopback, enforce timeout/size limits and lifecycle cancellation. Use issuer discovery/JWKS over HTTPS; do not weaken global Android cleartext policy. Verify how the browser handles loopback HTTP on the chosen Android versions.

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
