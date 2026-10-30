package com.artkashin.aiwalkingguide

import androidx.test.platform.app.InstrumentationRegistry
import com.artkashin.aiwalkingguide.auth.SecureAccount
import com.artkashin.aiwalkingguide.tour.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

class TourStoreTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun poi(id: String)=Poi(id,id,Position(55.75,37.62),"description","https://example.org")
    @Test fun terminalsPersistRecoverAndMergeAcrossProviderAliases() {
        var store=TourStore(context);store.clear();val session=store.session()
        val p=poi("wiki:ru:123")
        assertTrue(store.reserve(session,p));store.state(session,p,NarrationState.COMPLETED)
        store.close();store=TourStore(context)
        assertFalse(store.reserve(session,p))
        val canonical=store.resolve(p.copy(id="wd:Q123",aliases=setOf(p.id)))
        assertTrue(canonical.id in store.excluded(session));assertFalse(store.reserve(session,canonical))
        val interrupted=poi("interrupted");assertTrue(store.reserve(session,interrupted));store.recover(session)
        assertTrue(store.history(session).any{it.first==interrupted.title && it.second=="SKIPPED"})
        assertFalse(store.reserve(session,interrupted))
        store.state(session,canonical,NarrationState.PLAYING)
        assertTrue(store.history(session).any{it.second=="COMPLETED"})
        assertTrue(store.reserve(store.newSession(),canonical));store.clear();store.close()
    }
    @Test fun concurrentReservationsSelectOneNarration() {
        val store=TourStore(context);store.clear();val session=store.session()
        val results=Collections.synchronizedList(mutableListOf<Boolean>())
        val threads=(1..12).map{Thread{results.add(store.reserve(session,poi("one")))}}
        threads.forEach{it.start()};threads.forEach{it.join()}
        assertEquals(1,results.count{it});store.clear();store.close()
    }
    @Test fun credentialRoundTripUsesEncryptedNoBackupStorage() {
        val store=SecureAccount(context);store.clear();store.save(JSONObject().put("access_token","synthetic-test-only"))
        assertEquals("synthetic-test-only",store.load()?.getString("access_token"))
        val file=java.io.File(context.noBackupFilesDir,"chatgpt.enc")
        assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("synthetic-test-only"))
        store.clear();assertNull(store.load())
    }
}
