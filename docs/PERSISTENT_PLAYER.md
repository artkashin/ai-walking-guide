# Persistent player — issue #10

The Scaffold bottom bar contains one player above the navigation bar. Content scrolling and tab changes cannot move it away. It uses icon touch targets of at least 48 dp, full screen-reader labels, an independently readable time row and a slider; a narrow/large-font UI test is included.

All POI/museum narration and explicit generic NarrationItem playback share NarrationAudio/Media3. The engine publishes actual state and aggregate position every 250 ms. Chunk durations come from media metadata with a Media3 duration fallback. Seeking maps a global position to its file/offset and retains pause state. MediaSession coordinates system/headset commands; focus loss and headphone unplugging follow Media3 state. This does not claim tested Bluetooth behavior on every OEM.

The collapsible saved-story library on Walk retains access to older POI, museum and generic narration even when nearby search results change; explicit selection uses the same offline player.

Stop retains the selected item and text for explicit play/replay. Completion leaves the title, listened marker, duration and replay control visible. Session narration text and a separate manually-listened flag are stored locally. Database version 1 -> 2 adds this archive without resetting tour memory. After process death the last selected item restores stopped; it does not automatically resume or claim to restore an exact interrupted position.

Explicit replay of completed/skipped content uses saved text, needs no new GPT request, pauses automatic narration, and never reserves/reset its terminal automatic-memory state. Manual completion can mark the archive as listened while automatic history remains skipped. Missing cached audio is synthesized again from the saved text. If narration was skipped before any text existed, an explicit new request still requires the normal account/plan gate.

Tests: pure chunk/timeline boundaries and time formatting; real Media3 silent WAV playback/pause/seek/stop/replay; controller replay with a deterministic audio adapter and real SQLite memory; legacy database migration/archive ordering; persistent controls under scrolling at 240 dp/1.8 font scale. CI runs on API 26 and 35. Voice commands in issue #9 must call these same controller methods, never create an independent player.

Manual checklist: long/multi-chunk narration; seek backward/forward while paused; repeat listened and skipped POIs; verify automatic mode remains off on manual replay; switch all tabs/rotate/lock/unlock; unplug headset, incoming call, Bluetooth control; large system text and gesture/three-button navigation. Device audio-focus/OEM lifecycle behavior still needs physical-phone checks.
