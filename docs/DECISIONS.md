# Decisions

## D001 — Native Android foundation (2026-10-30)

Use Kotlin, Compose and a pinned Gradle wrapper. Java 17 in CI; min SDK 26. Keep the first screen honest about capabilities. No fake tour or login controls.

## D002 — Official subscription flow only (2026-10-30)

Current official OSS SIWC documentation describes dynamic registration, PKCE, loopback redirect and eligible Responses requests. Do not assume API keys are necessary or that all Android integrations are officially certified. Start with offline protocol tests and require user approval before account permission grants. See CHATGPT_FEASIBILITY.md. No Codex client-ID reuse, private backend, cookie extraction or API-key billing fallback.

## D003 — Speech starts with a device provider (2026-10-30)

Recommend Android TextToSpeech with an installed offline Russian voice for the first no-new-service implementation, synthesizing to cached files played by Media3. Voice availability and naturalness require device evaluation. Cloud speech remains unselected; OpenAI speech is a separately approved candidate, not a confirmed subscription entitlement.

## D004 — Independent guide and terminal session memory (2026-10-30)

Keep discovery independent from route planning. Store completed/skipped canonical POIs locally and make those states terminal for automatic playback. Silence is a valid result. No persistent zones.

## D005 — Repository creation denied (2026-10-30)

Connected GitHub identity is artkashin. A private creation attempt returned an insufficient-permissions error. The user then created the public repository. Reads work and report ADMIN membership, but both git push and connector file writes return HTTP 403, and issue creation is also inaccessible to the integration. Prepare sources and issue specifications in the cloud workspace until installation permissions are corrected. Do not switch owner, credentials or permission settings automatically.

Resolution: the connected app was installed only on AdaptiveBS. The user explicitly requested reconnection and installed ChatGPT Codex Connector on the personal artkashin account. Publishing, creating W01–W08 issues, and dispatching Actions now succeed. Keep subsequent work scoped to ai-walking-guide.

## D006 — CI SDK setup requests supported packages (2026-10-30)

The first real Actions run exposed a setup-android default requesting the removed SDK `tools` package. Specify `platform-tools` and install SDK 35/build-tools 35.0.0 explicitly. Keep the workflow failure and successful replacement recorded in PROGRESS.md.

## D007 — Approved implementation scope for 0.2.0 (2026-10-30)

The user explicitly selected external Google Maps without new billing and approved preparing official ChatGPT sign-in with included subscription usage only. Implement Maps URLs, not an embedded Google Cloud SDK. No additional service account, API key, paid API, credits purchase or billing upgrade is authorized. Wikipedia provides real nearby POIs with provenance after in-app location disclosure/consent.

## D008 — SQLite and foreground tour lifecycle (2026-10-30)

Use Android SQLite directly for atomic session reservations/alias migration without adding Room/KSP. An explicitly started location/media foreground service maintains the tour while Maps is open; no boot-started service or background-location permission. New sessions and data deletion are explicit actions. Audio uses cached offline Russian TTS files with Media3.

## D009 — Client cannot enforce subscription credit settings (2026-10-30)

The documented OAuth inference contract has no reviewed client flag guaranteeing included-plan-only billing if the user has enabled extra credits/automatic purchases on the server. The app has no purchase/key fallback, requires disabling these server settings and a per-process confirmation before AI requests, and explains that it cannot verify this setting. If it cannot be disabled, keep AI requests disabled. Live account entitlement remains a phone acceptance test.
