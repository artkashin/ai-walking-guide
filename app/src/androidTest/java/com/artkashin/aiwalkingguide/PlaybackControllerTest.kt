package com.artkashin.aiwalkingguide

import androidx.test.platform.app.InstrumentationRegistry
import com.artkashin.aiwalkingguide.audio.*
import com.artkashin.aiwalkingguide.tour.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PlaybackControllerTest {
    @Test fun schemaOneUpgradeKeepsTourMemoryAndRestoresLastManuallySelectedItem() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="player-migration-fixture.db";context.deleteDatabase(name)
        val path=context.getDatabasePath(name);path.parentFile?.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path,null).use { old ->
            old.execSQL("CREATE TABLE session(id TEXT PRIMARY KEY,active INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE memory(session TEXT NOT NULL,poi TEXT NOT NULL,state TEXT NOT NULL,title TEXT NOT NULL,PRIMARY KEY(session,poi))")
            old.execSQL("CREATE TABLE alias(name TEXT PRIMARY KEY,canonical TEXT NOT NULL)")
            old.execSQL("CREATE TABLE cache(poi TEXT PRIMARY KEY,payload TEXT NOT NULL)")
            old.execSQL("CREATE TABLE goal(session TEXT NOT NULL,poi TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(session,poi))")
            old.execSQL("INSERT INTO session VALUES('legacy',1)")
            old.execSQL("INSERT INTO memory VALUES('legacy','old','COMPLETED','Старый рассказ')")
            old.version=1
        }
        val poi=Poi("old","Старый рассказ",Position(55.75,37.62),"facts","https://example.org")
        val store=TourStore(context,name)
        try {
            assertEquals("legacy",store.session());assertFalse(store.reserve("legacy",poi))
            val first=NarrationItem("old",poi,"Первый сохранённый текст")
            val second=NarrationItem("other",poi.copy(id="other"),"Второй текст")
            store.saveNarration("legacy",first);store.saveNarration("legacy",second);store.saveNarration("legacy",first)
            assertEquals("old",store.lastNarration("legacy")?.id)
            store.markListened("legacy","old");assertTrue(store.lastNarration("legacy")!!.listened)
        } finally { store.close();context.deleteDatabase(name) }
    }
    private class Audio(private val events: PlaybackCallbacks): NarrationAudio {
        var state=PlaybackState();var plays=0
        override suspend fun prepare(text: String)=listOf(File("fixture.wav"))
        override fun play(files: List<File>,id: String,title: String){plays++;state=PlaybackState(id,title,PlaybackPhase.PLAYING,0,5000);events.state(state)}
        override fun pause(){state=state.copy(phase=PlaybackPhase.PAUSED);events.state(state)}
        override fun resume(){state=state.copy(phase=PlaybackPhase.PLAYING);events.state(state)}
        override fun seekTo(position: Long){state=state.copy(positionMs=position.coerceIn(0,5000));events.state(state)}
        override fun stop(clear: Boolean){state=if(clear)PlaybackState() else state.copy(phase=PlaybackPhase.STOPPED,positionMs=0);events.state(state)}
        override fun clearCache()=stop(true)
        override fun release()=Unit
        fun finish(){state=state.copy(phase=PlaybackPhase.COMPLETED,positionMs=5000);events.state(state);events.complete(state.id)}
    }
    @Test fun completedAndSkippedNarrationsReplayOfflineWithoutResettingAutomaticMemory() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val name="player-replay-fixture.db";context.deleteDatabase(name)
        val store=TourStore(context,name);val session=store.session()
        val poi=Poi("wd:fixture","Тестовый объект",Position(55.75,37.62),"Факты. ".repeat(40),"https://example.org")
        assertTrue(store.reserve(session,poi));store.state(session,poi,NarrationState.SKIPPED)
        store.saveNarration(session,NarrationItem(poi.id,poi,"Сохранённый рассказ для явного повтора."))
        lateinit var guide: TourController;lateinit var audio: Audio;var created=false
        try {
            instrumentation.runOnMainSync {
                guide=TourController(context,store){events->Audio(events).also{audio=it}}
                created=true
                assertEquals(PlaybackPhase.STOPPED,guide.state.value.playback.phase)
                guide.resume();assertEquals(PlaybackPhase.PLAYING,guide.state.value.playback.phase)
                guide.pause();guide.seekTo(3500);assertEquals(PlaybackPhase.PAUSED,guide.state.value.playback.phase)
                assertEquals(3500L,guide.state.value.playback.positionMs)
                guide.resume();audio.finish()
                assertTrue(guide.state.value.playback.listened)
                assertEquals(PlaybackPhase.COMPLETED,guide.state.value.playback.phase)
                assertTrue(store.history(session).any{it.second=="SKIPPED"})
                assertFalse(store.reserve(session,poi))
                assertNull(GuidePolicy.candidate(poi.position,listOf(poi),guide.state.value.excluded))
                guide.resume();assertEquals(2,audio.plays)
                guide.stopPlayback();assertEquals(PlaybackPhase.STOPPED,guide.state.value.playback.phase)
                assertTrue(poi.id in guide.state.value.listened)
                guide.playNarrationItem(NarrationItem("answer:fixture",poi.copy(title="Ответ гида"),"Ответ на вопрос.","FOLLOW_UP"))
                assertEquals("FOLLOW_UP",guide.state.value.selectedNarration?.kind)
                assertEquals("Ответ гида",guide.state.value.playback.title)
                audio.finish();assertFalse(store.reserve(session,poi))
            }
        } finally { instrumentation.runOnMainSync{if(created)guide.release()};context.deleteDatabase(name) }
    }
}
