# Session-only hands-free MVP (#9)

Depends on the shared player in #10 / PR #11. No default-assistant role, API key, paid speech provider or account registration is introduced.

## Usage and boundaries

1. Start an ordinary tour with location permission. In Settings explicitly download the Russian offline model (~45 MB download, ~90 MB installed, allow ~200 MB temporary free space).
2. Confirm microphone/session/data-sharing information, grant RECORD_AUDIO, then enable hands-free from the visible activity. Android promotes the existing foreground tour service to microphone type before recording. Its persistent notification and the OS microphone indicator remain visible as applicable to the OS.
3. Wait for “Слушаю фразу…” and say “Окей, гид, куда дальше?” or a question about the last story. The detector recognizes a configurable 2–4-word Russian phrase; all words must exist in the model's vocabulary.
4. Wake detection and command transcription run locally. Only an accepted question's text, recent story/dialogue context, remaining goal names and a fresh approximate fix/nearby POIs are sent through the existing official subscription request path. Local pause/stop/resume/end-tour commands do not call GPT. The additional-credit confirmation/model/plan guards still apply.
5. Answers enter the same offline Russian synthesis/cache and shared player as POIs and museum stories. Follow-up text and the last six question/answer turns are session-local. A manual question does not reset POI deduplication or automatically repeat stories.
6. End the tour or disable hands-free to stop capture and free native recognizers/model. Process death does not restart the microphone. Revoked permission/model/audio errors stop the voice mode. Removing an input device stops it; enable it again after checking the route. Telephone/audio-mode interruptions close capture and impose a two-second cooldown afterwards.

**Half-duplex fallback:** while guide audio plays/buffers, or while the guide prepares a response, recording is physically stopped. Echo cannot wake the guide during its own speech. This MVP cannot hear a spoken “pause” over ongoing guide speech: use the fixed player, headset media pause where supported, or notification pause, then wait two seconds before the wake phrase. Voice pause/stop/resume are available when listening. Full-duplex barge-in is deferred pending device echo-cancellation/false-wake testing.

Bluetooth microphone routing is not forced; Android's default input is used. Screen-lock/background operation is possible with the foreground service on supported phones, but OEM restrictions, OS global microphone controls and headset routing must be verified on the user's device. No universal locked-screen/battery-performance claim is made.

The conversation panel is available on Walk and Museum as a collapsible “Продолжить разговор с гидом”. Typed questions work without microphone/model installation. The location checkbox excludes current coordinates and nearby POIs for that typed request. Voice consent explicitly includes approximate location; turn off hands-free and use the typed checkbox to omit it. Tour story text and goal names may themselves reveal places.

## Context and privacy

`GuideQuestionContext` supplies up to three completed POI stories, the selected story/answer and actual playback state/progress, six dialogue turns, eight advisory goals and up to six nearby real POIs. Completion is described as a player event, not proof every word was heard (seeking is possible). Stale (>30 s), poor (>100 m), absent or future fixes are omitted. Position uses the original Android fix timestamp, is rounded to three decimals and is not replaced with a narrated object's location. “This building” remains ambiguous without gaze/photo selection: the prompt asks for clarification rather than inventing an identity.

A bounded two-second PCM ring in RAM carries overlap from wake into the command recognizer; it is cleared when capture closes. No idle/command audio files, audio uploads, transcripts in logs/notifications, or stored location snapshots are added. Questions/answers and narration text persist in the app-private SQLite database with backup disabled; Clear Data removes them. Native models live in noBackupFilesDir; only the explicit model download uses the model host.

## Engine evaluation (official/upstream sources checked 2026-10-31)

| Candidate | Russian / Android / terms | Decision |
| --- | --- | --- |
| [Porcupine Android quick start](https://picovoice.ai/docs/quick-start/porcupine-android/) | Android SDK; Picovoice account AccessKey and generated keyword/model setup. Licensing/account terms require review for the chosen use. | Not enabled: no new account/credentials/service authorization requested by this MVP. |
| [openWakeWord](https://github.com/dscripka/openWakeWord) | Upstream describes current pretrained support as English; code Apache 2.0, pretrained models CC BY-NC-SA 4.0. Custom Russian/Android integration would require additional work and licensing checks. | Not selected for Russian Android MVP. |
| [Vosk models](https://alphacephei.com/vosk/models) / [Android](https://alphacephei.com/vosk/android) / [source](https://github.com/alphacep/vosk-api) | Android native bindings, Russian small model `vosk-model-small-ru-0.22`, Apache 2.0. No AccessKey. | Selected: grammar-constrained offline ASR wake detector plus full Russian command ASR. This is not dedicated DSP/low-power keyword spotting. CPU/RAM/battery costs need physical measurement. |

Model ZIP: `https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip`; SHA-256 `961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4`. Installation checks HTTPS status/size/hash, path traversal and extraction limits before replacing the ready model. Native library: `com.alphacephei:vosk-android:0.3.75` with transitive JNA. Model/data terms remain separate from app code; the publisher lists this model as Apache 2.0. The original model README is retained; Apache/JNA license notices ship in `assets/licenses/`.

## Verification

JVM tests exercise session activation, duplicate-wake rejection, timeout, response gating, speech/call mute, cooldown, stop, Russian control/question routing and bounded PCM clearing. Instrumented tests cover fresh/stale/opted-out context, partial playback, local bounded dialogue/restart, terminal dedup preservation and migration. Existing player/Media3/location/photo/auth tests remain required on API 26 and 35.

The opt-in `Offline Russian voice model smoke` Actions workflow downloads the pinned public model only for its test APK, loads the actual Android native model, checks Russian wake vocabulary and confirms both wake grammar and full recognizer reject silence. It needs no microphone or user credentials. This validates native compatibility/model integration, not acoustic wake accuracy or battery performance.

Before enabling automatic broad use, manually record outcomes (currently **unmeasured**) for: 30–60 minutes on/off battery and RAM on the target phone; real Russian phrases at varied distances/noise; false wakes with unrelated speech; lockscreen/OEM battery saver; calls; wired/Bluetooth disconnect and reconnection; OS microphone toggle/permission revocation; rapid enable/disable/end tour; guide echo and two-second cooldown; subscription failure/limit; questions about a heard story after walking elsewhere. Positive acoustic recognition, full-duplex barge-in and physical battery/device acceptance are not established by emulator silence tests.
