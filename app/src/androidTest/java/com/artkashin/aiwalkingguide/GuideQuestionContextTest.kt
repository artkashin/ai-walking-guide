package com.artkashin.aiwalkingguide

import androidx.test.core.app.ApplicationProvider
import com.artkashin.aiwalkingguide.audio.*
import com.artkashin.aiwalkingguide.tour.*
import com.artkashin.aiwalkingguide.voice.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GuideQuestionContextTest {
    private val poi=Poi("fixture","Музей",Position(55.75,37.61),"Факты из источника","https://example.org")
    private fun context(position: Position?,fix: Long,share: Boolean=true): JSONObject {
        val text=GuideQuestionContext.prompt("Почему?",listOf(NarrationItem(poi.id,poi,"Прослушанный рассказ",listened=true)),
            NarrationItem("answer",null,"Частичный ответ","FOLLOW_UP"),PlaybackState("id","Ответ",PlaybackPhase.PAUSED,4500,12000),
            listOf(VoiceTurn("Вопрос до этого","answer","Ответ до этого")),position,fix,50000,listOf(poi),listOf(poi),share)
        return JSONObject(text.substringAfter("Контекст JSON:\n"))
    }
    @Test fun questionHasHeardAndPartialContextButOnlyFreshApproximateLocation() {
        val j=context(Position(55.750234,37.610678,20f),49000)
        assertEquals("Почему?",j.getString("question"))
        assertTrue(j.getJSONArray("recent_heard_topics").getJSONObject(0).getBoolean("playback_completed"))
        val selected=j.getJSONObject("selected_narration")
        assertEquals("PAUSED",selected.getString("playback_state"));assertEquals(4500,selected.getInt("position_ms"))
        assertFalse(selected.getBoolean("playback_completed"))
        val location=j.getJSONObject("current_location")
        assertEquals("fresh_approximate",location.getString("status"))
        assertEquals(55.750,location.getDouble("lat"),0.0000001)
        assertEquals(37.611,location.getDouble("lon"),0.0000001)
        assertEquals(1,location.getJSONArray("nearby").length())
        assertEquals(1,j.getJSONArray("dialogue").length())
        assertFalse(j.getJSONArray("remaining_goals").getJSONObject(0).has("lat"))
    }
    @Test fun stalePoorFutureReplayOrOptedOutFixNeverLeaksAsCurrentLocation() {
        listOf(context(poi.position,10000),context(Position(55.75,37.61,200f),49000),context(poi.position,50001),context(null,49000),context(poi.position,49000,false)).forEach {
            val location=it.getJSONObject("current_location")
            assertFalse(location.has("lat"));assertFalse(location.has("lon"));assertFalse(location.has("nearby"))
        }
    }
    @Test fun sessionDialogueSurvivesRestartWithoutSavingLocationAndDoesNotResetDedup() {
        val app=ApplicationProvider.getApplicationContext<android.content.Context>();val name="voice-${System.nanoTime()}.db"
        try {
            var db=TourStore(app,name);val session=db.session()
            db.reserve(session,poi);db.state(session,poi,NarrationState.SKIPPED)
            repeat(9) { i->val item=NarrationItem("answer:$i",null,"Ответ $i","FOLLOW_UP");db.saveNarration(session,item);db.addVoiceTurn(session,VoiceTurn("Вопрос $i",item.id,item.text)) }
            db.markListened(session,"answer:8");db.close();db=TourStore(app,name)
            val turns=db.voiceTurns(session)
            assertEquals(6,turns.size);assertEquals("Вопрос 3",turns.first().question);assertTrue(turns.last().heard)
            assertNull(db.lastNarration(session)?.poi);assertEquals("Ответ гида",db.lastNarration(session)?.title)
            assertTrue(db.excluded(session).contains(poi.id))
            db.readableDatabase.rawQuery("SELECT payload FROM voice_turns",null).use{assertTrue(it.moveToFirst());assertFalse(it.getString(0).contains("lat"))}
            db.newSession();assertTrue(db.voiceTurns(db.session()).isEmpty())
            db.clear();assertTrue(db.voiceTurns(session).isEmpty());db.close()
        } finally {app.deleteDatabase(name)}
    }
}
