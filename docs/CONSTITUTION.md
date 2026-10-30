# Project Constitution

These requirements govern every implementation and acceptance review.

1. **Flexible navigation.** A walking route is advisory. Users may deviate freely. Replan from actual position toward remaining goals without forcing return to the old route.
2. **Independent AI guide.** Discover real interesting POIs near the user's actual position, including off-route objects. Route membership must not determine narration eligibility.
3. **No repetition.** Persist narrated and skipped POIs within the tour session. Never automatically repeat completed or skipped narration, including after process restart or identity alias resolution.
4. **No artificial zones.** Use real POIs and contextual proximity searches. Do not introduce persistent interest-zone entities.
5. **Silence is valid.** When no new worthwhile, sufficiently grounded candidate exists, produce no narration. Never invent an attraction to fill silence.
6. **Museum mode.** Accept user photographs to identify artworks using available location and tour context. State uncertainty and request clarification rather than inventing attribution.
7. **Russian audio.** Generate natural Russian narration with flexible duration, pause, resume, skip, and caching. Do not treat a fixed duration as mandatory.
8. **Subscription first.** Investigate official Sign in with ChatGPT and subscription entitlements. No separately billed OpenAI API without explicit approval; no unofficial endpoints or extracted ChatGPT session credentials.
9. **Privacy.** Keep location history local wherever possible. Secure credentials with Android Keystore-backed encryption; redact sensitive logs and exclude credentials/history from backups and CI.
10. **Cloud first.** Builds and automated tests run in GitHub Actions; a local development computer is unnecessary.

New paid services, account permissions, or security-sensitive operations require user approval. Device runtime permissions are requested in context. Keep facts, planned behavior, simulated tests, and live verified behavior clearly distinguished.
