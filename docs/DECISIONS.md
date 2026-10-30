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

Connected GitHub identity is artkashin. Repository lookup returned not found; a private creation attempt returned an insufficient-permissions error. Prepare sources and issue specifications locally in the cloud workspace; do not create a repository under a different owner or change account permissions. Resume publishing when the user creates the repository or authorizes suitable access.
