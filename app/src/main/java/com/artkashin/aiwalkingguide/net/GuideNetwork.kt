package com.artkashin.aiwalkingguide.net

import com.artkashin.aiwalkingguide.tour.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

class ServiceFailure(val safeMessage: String): Exception(safeMessage)

object GuideHttp {
    fun query(params: Map<String,String>) = params.entries.joinToString("&") { (k,v)->"$k=${URLEncoder.encode(v,"UTF-8")}" }
    fun connection(url: String, token: String?=null): HttpsURLConnection {
        require(URL(url).host in setOf("ru.wikipedia.org","en.wikipedia.org","auth.openai.com","api.openai.com"))
        return (URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout=15000;readTimeout=90000;instanceFollowRedirects=false
            setRequestProperty("User-Agent","AIWalkingGuide/0.2 (https://github.com/artkashin/ai-walking-guide)")
            if(token!=null) setRequestProperty("Authorization","Bearer $token")
        }
    }
    fun check(c: HttpsURLConnection) {
        if(c.responseCode !in 200..299) throw ServiceFailure(when(c.responseCode) {
            401->"Сеанс ChatGPT истёк. Войдите снова."
            403->"Сервис не разрешил доступ этому приложению или аккаунту."
            429->"Достигнут лимит сервиса. Повторите позже; платного переключения нет."
            else->"Сервис временно недоступен (HTTP ${c.responseCode})."
        })
    }
    suspend fun json(url: String, body: String?=null, token: String?=null, form: Boolean=false): JSONObject = withContext(Dispatchers.IO) {
        val c=connection(url,token)
        try {
            if(body!=null) { c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type",if(form) "application/x-www-form-urlencoded" else "application/json");c.outputStream.use{it.write(body.toByteArray())} }
            check(c)
            val bytes=c.inputStream.use { input ->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) { coroutineContext.ensureActive();val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n);if(out.size()>2_000_000)throw ServiceFailure("Ответ сервиса слишком большой.") }
                out.toByteArray()
            }
            if(bytes.size>2_000_000) throw ServiceFailure("Ответ сервиса слишком большой.")
            JSONObject(bytes.toString(Charsets.UTF_8))
        } finally { c.disconnect() }
    }
    suspend fun response(token: String,model: String,prompt: String,image: String?=null): String = withContext(Dispatchers.IO) {
        val content=JSONArray().put(JSONObject().put("type","input_text").put("text",prompt))
        if(image!=null) content.put(JSONObject().put("type","input_image").put("image_url","data:image/jpeg;base64,$image"))
        val body=JSONObject().put("model",model).put("store",false).put("stream",true)
            .put("input",JSONArray().put(JSONObject().put("role","user").put("content",content)))
        val c=connection("https://api.openai.com/v1/responses",token)
        try {
            c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json")
            c.outputStream.use{it.write(body.toString().toByteArray())};check(c)
            val events=ResponseEvents();val data=StringBuilder();var size=0
            fun event() {
                if(data.isEmpty()) return
                val raw=data.toString().trim();data.setLength(0)
                events.accept(raw)
            }
            c.inputStream.bufferedReader().use { reader ->
                while(true) {
                    coroutineContext.ensureActive()
                    val lineBuffer=StringBuilder()
                    var eof=false
                    while(true) {
                        val ch=reader.read()
                        if(ch<0) { eof=true;break }
                        if(ch==10)break
                        lineBuffer.append(ch.toChar())
                        if(lineBuffer.length>262144)throw ServiceFailure("Событие сервиса слишком большое.")
                    }
                    val line=lineBuffer.toString().removeSuffix("\r")
                    size+=line.length+1;if(size>2_000_000) throw ServiceFailure("Ответ ChatGPT слишком большой.")
                    if(line.isEmpty()) { event();if(events.completed) break }
                    else if(line.startsWith("data:")) { if(data.isNotEmpty()) data.append('\n');data.append(line.removePrefix("data:").trimStart()) }
                    if(eof)break
                }
                if(!events.completed) event()
            }
            events.result()
        } finally { c.disconnect() }
    }
}

class WikipediaPois {
    suspend fun nearby(position: Position): List<Poi> {
        val ru=try { search(position,"ru") } catch(e: CancellationException) { throw e } catch(_: Exception) { emptyList() }
        if(ru.size>=4)return ru
        val en=try { search(position,"en") } catch(e: CancellationException) { throw e } catch(e: Exception) {
            if(ru.isEmpty())throw e else emptyList()
        }
        return (ru+en).distinctBy{it.id}
    }
    private suspend fun search(p: Position,lang: String): List<Poi> {
        val params=mapOf("action" to "query","format" to "json","formatversion" to "2","generator" to "geosearch",
            "ggscoord" to "${p.lat}|${p.lon}","ggsradius" to "1200","ggslimit" to "20","ggsnamespace" to "0",
            "prop" to "extracts|coordinates|pageprops|info","exintro" to "1","explaintext" to "1","exchars" to "1200","inprop" to "url")
        val result=GuideHttp.json("https://$lang.wikipedia.org/w/api.php?${GuideHttp.query(params)}")
        if(result.has("error")) throw ServiceFailure("Поиск Wikipedia временно недоступен.")
        val pages=result.optJSONObject("query")?.optJSONArray("pages")?:return emptyList()
        return (0 until pages.length()).mapNotNull { i ->
            val j=pages.getJSONObject(i);val pos=j.optJSONArray("coordinates")?.optJSONObject(0)?:return@mapNotNull null
            val page="wiki:$lang:${j.getLong("pageid")}";val wd=j.optJSONObject("pageprops")?.optString("wikibase_item").orEmpty()
            val id=if(wd.matches(Regex("Q[0-9]+"))) "wd:$wd" else page
            Poi(id,j.getString("title"),Position(pos.getDouble("lat"),pos.getDouble("lon")),j.optString("extract"),j.optString("fullurl"),setOf(page))
        }.filter { it.extract.isNotBlank() }
    }
}
