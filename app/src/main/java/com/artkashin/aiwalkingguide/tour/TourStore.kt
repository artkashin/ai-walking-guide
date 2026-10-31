package com.artkashin.aiwalkingguide.tour

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import com.artkashin.aiwalkingguide.audio.NarrationItem

/** SQLite transactions keep reservations and alias resolution atomic across process restart. */
class TourStore(context: Context,name: String="tours.db") : SQLiteOpenHelper(context, name, null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE session(id TEXT PRIMARY KEY, active INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE memory(session TEXT NOT NULL, poi TEXT NOT NULL, state TEXT NOT NULL, title TEXT NOT NULL, PRIMARY KEY(session,poi))")
        db.execSQL("CREATE TABLE alias(name TEXT PRIMARY KEY, canonical TEXT NOT NULL)")
        db.execSQL("CREATE TABLE cache(poi TEXT PRIMARY KEY, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE goal(session TEXT NOT NULL, poi TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(session,poi))")
        createNarrations(db)
    }
    private fun createNarrations(db: SQLiteDatabase) { db.execSQL("CREATE TABLE narration(session TEXT NOT NULL, id TEXT NOT NULL, payload TEXT NOT NULL, listened INTEGER NOT NULL DEFAULT 0, touched INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(session,id))") }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) { if(old<2)createNarrations(db) }
    @Synchronized fun saveNarration(session: String,item: NarrationItem) {
        val payload=JSONObject().put("id",item.id).put("poi",JSONObject(encode(item.poi))).put("text",item.text).put("kind",item.kind).toString()
        writableDatabase.execSQL("INSERT OR IGNORE INTO narration(session,id,payload,listened) VALUES(?,?,?,?)",arrayOf(session,item.id,payload,if(item.listened)1 else 0))
        writableDatabase.execSQL("UPDATE narration SET payload=?,touched=(SELECT COALESCE(MAX(touched),0)+1 FROM narration WHERE session=?) WHERE session=? AND id=?",arrayOf(payload,session,session,item.id))
    }
    private fun decodeNarration(payload: String,listened: Boolean): NarrationItem {
        val j=JSONObject(payload);return NarrationItem(j.getString("id"),decode(j.getJSONObject("poi").toString()),j.getString("text"),j.optString("kind","POI"),listened)
    }
    @Synchronized fun narration(session: String,id: String): NarrationItem? {
        val key=canonical(id)
        return readableDatabase.rawQuery("SELECT payload,listened FROM narration WHERE session=? AND (id=? OR id IN (SELECT name FROM alias WHERE canonical=?)) ORDER BY touched DESC,rowid DESC LIMIT 1",arrayOf(session,key,key)).use {
            if(it.moveToFirst())decodeNarration(it.getString(0),it.getInt(1)==1) else null
        }
    }
    @Synchronized fun narrations(session: String): List<NarrationItem> = readableDatabase.rawQuery("SELECT payload,listened FROM narration WHERE session=? ORDER BY touched DESC,rowid DESC",arrayOf(session)).use {
        buildList { while(it.moveToNext())add(decodeNarration(it.getString(0),it.getInt(1)==1)) }
    }
    @Synchronized fun lastNarration(session: String): NarrationItem? = readableDatabase.rawQuery("SELECT payload,listened FROM narration WHERE session=? ORDER BY touched DESC,rowid DESC LIMIT 1",arrayOf(session)).use {
        if(it.moveToFirst())decodeNarration(it.getString(0),it.getInt(1)==1) else null
    }
    @Synchronized fun markListened(session: String,id: String) { writableDatabase.execSQL("UPDATE narration SET listened=1,touched=(SELECT COALESCE(MAX(touched),0)+1 FROM narration WHERE session=?) WHERE session=? AND id=?",arrayOf(session,session,id)) }
    @Synchronized fun listened(session: String): Set<String> = readableDatabase.rawQuery("SELECT id FROM narration WHERE session=? AND listened=1 UNION SELECT poi FROM memory WHERE session=? AND state='COMPLETED'",arrayOf(session,session)).use {
        buildSet { while(it.moveToNext())add(canonical(it.getString(0))) }
    }
    @Synchronized fun session(): String {
        readableDatabase.rawQuery("SELECT id FROM session WHERE active=1 LIMIT 1", null).use { if(it.moveToFirst()) return it.getString(0) }
        return UUID.randomUUID().toString().also { writableDatabase.execSQL("INSERT INTO session VALUES(?,1)", arrayOf(it)) }
    }
    @Synchronized fun newSession(): String {
        writableDatabase.execSQL("UPDATE session SET active=0")
        return session()
    }
    private fun canonical(id: String): String = readableDatabase.rawQuery("SELECT canonical FROM alias WHERE name=?", arrayOf(id)).use {
        if(it.moveToFirst()) it.getString(0) else id
    }
    @Synchronized fun resolve(poi: Poi): Poi {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val names = (poi.aliases + poi.id).flatMap { listOf(it, canonical(it)) }.toSet()
            // Wikidata is provider-independent; otherwise retain an existing canonical identity.
            val target = names.firstOrNull { it.startsWith("wd:") } ?: names.firstOrNull { it != poi.id && !it.startsWith("wiki:") } ?: canonical(poi.id)
            for(name in names) {
                db.execSQL("INSERT OR REPLACE INTO memory SELECT session,?,state,title FROM memory WHERE poi=? AND state IN ('COMPLETED','SKIPPED')", arrayOf(target,name))
                db.execSQL("INSERT OR IGNORE INTO memory SELECT session,?,state,title FROM memory WHERE poi=?", arrayOf(target,name))
                db.execSQL("UPDATE alias SET canonical=? WHERE canonical=?", arrayOf(target,name))
                db.execSQL("INSERT OR REPLACE INTO alias VALUES(?,?)", arrayOf(name,target))
            }
            db.setTransactionSuccessful()
            return poi.copy(id=target, aliases=names)
        } finally { db.endTransaction() }
    }
    @Synchronized fun excluded(session: String): Set<String> = readableDatabase.rawQuery("SELECT poi FROM memory WHERE session=?", arrayOf(session)).use {
        buildSet { while(it.moveToNext()) add(it.getString(0)) }
    }
    @Synchronized fun history(session: String): List<Pair<String,String>> = readableDatabase.rawQuery("SELECT title,state FROM memory WHERE session=? ORDER BY rowid DESC", arrayOf(session)).use {
        buildList { while(it.moveToNext()) add(it.getString(0) to it.getString(1)) }
    }
    @Synchronized fun reserve(session: String, poi: Poi): Boolean {
        val c=canonical(poi.id)
        return writableDatabase.insertWithOnConflict("memory",null,ContentValues().apply {
            put("session",session);put("poi",c);put("state","RESERVED");put("title",poi.title)
        },SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }
    @Synchronized fun state(session: String, poi: Poi, state: NarrationState) {
        writableDatabase.execSQL("UPDATE memory SET state=? WHERE session=? AND poi=? AND state NOT IN ('COMPLETED','SKIPPED')", arrayOf(state.name,session,canonical(poi.id)))
    }
    @Synchronized fun recover(session: String) {
        // Interrupted narration is never automatically started again after a process death.
        writableDatabase.execSQL("UPDATE memory SET state='SKIPPED' WHERE session=? AND state NOT IN ('COMPLETED','SKIPPED')", arrayOf(session))
    }
    fun encode(p: Poi) = JSONObject().put("id",p.id).put("title",p.title).put("lat",p.position.lat).put("lon",p.position.lon)
        .put("extract",p.extract).put("source",p.source).put("aliases",JSONArray(p.aliases.toList())).toString()
    fun decode(s: String): Poi {
        val j=JSONObject(s);val a=j.optJSONArray("aliases")?:JSONArray()
        return Poi(j.getString("id"),j.getString("title"),Position(j.getDouble("lat"),j.getDouble("lon")),j.getString("extract"),j.getString("source"),(0 until a.length()).map{a.getString(it)}.toSet())
    }
    @Synchronized fun cache(pois: List<Poi>) {
        pois.forEach { writableDatabase.execSQL("INSERT OR REPLACE INTO cache VALUES(?,?)",arrayOf(it.id,encode(it))) }
        writableDatabase.execSQL("DELETE FROM cache WHERE rowid NOT IN (SELECT rowid FROM cache ORDER BY rowid DESC LIMIT 2000)")
    }
    @Synchronized fun cached(position: Position): List<Poi> = readableDatabase.rawQuery("SELECT payload FROM cache",null).use {
        buildList { while(it.moveToNext()) { val p=decode(it.getString(0));if(position.distance(p.position)<2000) add(p) } }
    }
    @Synchronized fun goals(session: String): List<Poi> = readableDatabase.rawQuery("SELECT payload FROM goal WHERE session=?",arrayOf(session)).use {
        buildList { while(it.moveToNext()) add(decode(it.getString(0))) }
    }
    @Synchronized fun goal(session: String,p: Poi,add: Boolean) {
        if(add) writableDatabase.execSQL("INSERT OR REPLACE INTO goal VALUES(?,?,?)",arrayOf(session,p.id,encode(p)))
        else writableDatabase.execSQL("DELETE FROM goal WHERE session=? AND poi=?",arrayOf(session,p.id))
    }
    @Synchronized fun clear() { writableDatabase.beginTransaction();try {
        listOf("memory","alias","cache","goal","narration","session").forEach { writableDatabase.delete(it,null,null) }
        writableDatabase.setTransactionSuccessful()
    } finally { writableDatabase.endTransaction() } }
}
