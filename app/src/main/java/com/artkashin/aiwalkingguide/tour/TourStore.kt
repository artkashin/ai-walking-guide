package com.artkashin.aiwalkingguide.tour

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** SQLite transactions keep reservations and alias resolution atomic across process restart. */
class TourStore(context: Context) : SQLiteOpenHelper(context, "tours.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE session(id TEXT PRIMARY KEY, active INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE memory(session TEXT NOT NULL, poi TEXT NOT NULL, state TEXT NOT NULL, title TEXT NOT NULL, PRIMARY KEY(session,poi))")
        db.execSQL("CREATE TABLE alias(name TEXT PRIMARY KEY, canonical TEXT NOT NULL)")
        db.execSQL("CREATE TABLE cache(poi TEXT PRIMARY KEY, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE goal(session TEXT NOT NULL, poi TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(session,poi))")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
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
        listOf("memory","alias","cache","goal","session").forEach { writableDatabase.delete(it,null,null) }
        writableDatabase.setTransactionSuccessful()
    } finally { writableDatabase.endTransaction() } }
}
