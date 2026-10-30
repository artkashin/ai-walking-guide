# Russian speech recommendation

**Use Android TextToSpeech with a locally installed Russian voice for the first implementation**, through a replaceable provider interface. Prefer a voice reporting `isNetworkConnectionRequired == false`; if unavailable, offer voice installation and keep narration silent rather than silently enabling a network service. Installation and voice availability vary by device/engine. This recommendation avoids a new separately billed API and keeps narration text local during synthesis.

For pause/resume and cache support, use `synthesizeToFile`, wait for successful completion, then play the file with Media3. Android TTS itself does not provide reliable arbitrary pause/resume. Include locale, engine/version, voice, text hash and synthesis options in the cache key. Break very long narration at sentence boundaries under the engine's input limit. Handle cancellation, failed synthesis, audio focus, headphones and session skip before playing.

This is a provisional engineering choice, not a claim that every phone has a natural Russian voice. W06 must compare pronunciation of Russian names, dates, stress, latency, offline operation and perceived naturalness on a real device. If quality is insufficient, request approval for a cloud provider before integrating it.

OpenAI text-to-speech documents Russian support, but voices are described as optimized for English. It is a candidate for a quality trial after separate billing approval. The reviewed SIWC flow documents eligible Responses requests and does not confirm speech endpoint access; do not reuse OAuth tokens against speech endpoints merely to probe billing. No paid provider has been enabled.

The current OpenAI speech guide also announces shutdown of its legacy TTS models on 2027-01-06 and recommends a Realtime workflow for migration. Any future cloud trial must recheck the supported model, endpoint, entitlement and pricing rather than build against the legacy examples.

Sources accessed 2026-10-30:

- [Android TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
- [Android Voice](https://developer.android.com/reference/android/speech/tts/Voice)
- [OpenAI text to speech and supported languages](https://developers.openai.com/api/docs/guides/text-to-speech)
- [SIWC preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)
