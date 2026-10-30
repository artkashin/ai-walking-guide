package com.artkashin.aiwalkingguide.auth

import android.content.Context
import com.artkashin.aiwalkingguide.net.*
import com.nimbusds.jose.jwk.JWKSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.SocketException
import java.net.URI
import java.util.UUID

class ChatGptAccount(context: Context,private val authNetwork: AuthNetwork=OpenAiAuthNetwork) {
    private val store=SecureAccount(context)
    private val prefs=context.getSharedPreferences("auth-host",Context.MODE_PRIVATE)
    private val lock=Mutex()
    @Volatile private var accountVersion=0L
    @Volatile private var listener: ServerSocket?=null
    @Volatile private var browser: Socket?=null
    @Volatile private var pending: ChatGptPkce.Attempt?=null

    fun connected(): Boolean = store.load()?.let { it.optString("subject").isNotBlank() && it.optString("id_token").isNotBlank() }==true
    fun planEnabled(): Boolean = store.load()?.let(::hasPlan)==true
    private fun hasPlan(t: JSONObject)=t.optString("scope").split(Regex("\\s+")).contains("chatgpt.tokens.use.direct") &&
        t.optString("access_token").isNotBlank() && t.optString("token_type").equals("Bearer",true)
    fun diagnostic(): String {
        val stage=AuthStage.entries.firstOrNull{it.name==prefs.getString("stage",null)}?:return "Вход ещё не выполнен"
        val error=AuthError.entries.firstOrNull{it.name==prefs.getString("failure",null)}
        if(error!=null)return AuthFailure(stage,error).userMessage()
        if(connected())return if(planEnabled())AuthStage.CONNECTED.label else AuthStage.IDENTITY_ONLY.label
        if(pending==null)return "Предыдущий вход не завершён. Повторите вход. Последний этап: ${stage.label}."
        return stage.label
    }
    private fun record(stage: AuthStage,error: AuthError?=null) {
        prefs.edit().putString("stage",stage.name).putString("failure",error?.name).apply()
    }
    @Synchronized private fun updateStage(a: ChatGptPkce.Attempt,stage: AuthStage,onStage: (AuthStage)->Unit) {
        if(pending!==a)throw CancellationException("Authorization replaced")
        record(stage);onStage(stage)
    }
    @Synchronized fun disconnectLocal(): JSONObject? {
        val old=store.load();accountVersion++;cancel();store.clear();prefs.edit().remove("stage").remove("failure").apply();return old
    }
    suspend fun revoke(old: JSONObject?) {
        if(old==null || old.optString("refresh_token").isBlank())return
        val discovery=authNetwork.json("https://auth.openai.com/.well-known/openid-configuration")
        val c=GuideHttp.connection(discovery.getString("revocation_endpoint"))
        withContext(Dispatchers.IO) {
            try {
                c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/x-www-form-urlencoded")
                c.outputStream.use { it.write(GuideHttp.query(mapOf("token" to old.getString("refresh_token"),"client_id" to old.getString("client_id"),"token_type_hint" to "refresh_token")).toByteArray()) }
                GuideHttp.check(c)
            } finally { c.disconnect() }
        }
    }
    @Synchronized fun cancel() {
        listener?.close();listener=null
        try { browser?.close() }catch(_: Exception){};browser=null;pending=null
    }
    @Synchronized fun begin(requestPlanConsent: Boolean=false): String {
        cancel()
        val host=prefs.getString("host",null)?:"urn:uuid:${UUID.randomUUID()}".also{prefs.edit().putString("host",it).commit()}
        val socket=ServerSocket(0,4,InetAddress.getByName("127.0.0.1")).apply { soTimeout=180000 }
        listener=socket
        val saved=store.load()
        val a=ChatGptPkce.newRegistration(host,socket.localPort,saved?.optString("client_id"),saved?.optString("id_token"),requestPlanConsent)
        pending=a;record(AuthStage.WAITING)
        return a.authorizationUri
    }
    private fun respond(socket: Socket,status: Int,message: String) {
        try {
            fun escape(text: String)=text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
            val html=("<!doctype html><html lang=ru><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"+
                "<title>Гид рядом — вход</title><style>body{font:18px sans-serif;padding:28px;line-height:1.5}a{display:block;margin-top:24px;padding:16px;background:#176b59;color:white;text-decoration:none;border-radius:12px}</style>"+
                "<h1>Гид рядом</h1><p>${escape(message)}</p><a href='aiwalkingguide://auth-complete'>Вернуться в приложение</a>"+
                "<p>Можно также вернуться обычной кнопкой «Назад».</p></html>").toByteArray()
            val reason=if(status==200)"OK" else "Not Found"
            socket.getOutputStream().write(("HTTP/1.1 $status $reason\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.size}\r\n"+
                "Cache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'\r\nConnection: close\r\n\r\n").toByteArray())
            socket.getOutputStream().write(html);socket.getOutputStream().flush()
        } catch(_: Exception) { /* A closed browser cannot undo an already saved verified account. */ }
    }
    suspend fun finish(onStage: (AuthStage)->Unit={}): Unit = withContext(Dispatchers.IO) {
        val server=listener?:throw ServiceFailure("Вход отменён.")
        val a=pending?:throw ServiceFailure("Вход отменён.")
        var client: Socket?=null;var stage=AuthStage.WAITING
        fun progress(next: AuthStage) { stage=next;updateStage(a,next,onStage) }
        try {
            val deadline=System.nanoTime()+180_000_000_000L
            var accepted: Pair<String,String>?=null
            while(accepted==null) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val remaining=(deadline-System.nanoTime())/1_000_000
                if(remaining<=0)throw SocketTimeoutException()
                server.soTimeout=remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                val candidate=server.accept();browser=candidate;candidate.soTimeout=minOf(5000,remaining.toInt())
                try {
                    val reader=candidate.getInputStream().bufferedReader();val line=StringBuilder()
                    while(line.length<16384) { val ch=reader.read();if(ch<0||ch==10)break;line.append(ch.toChar()) }
                    val request=line.toString().trim().split(' ')
                    if(line.isNotEmpty() && line.length<16384) {
                        var headerBytes=0
                        while(true) {
                            val header=StringBuilder()
                            while(header.length<16384) { val ch=reader.read();if(ch<0||ch==10)break;header.append(ch.toChar()) }
                            headerBytes+=header.length
                            require(header.length<16384 && headerBytes<=32768)
                            if(header.toString().trim().isEmpty())break
                        }
                    }
                    if(line.length>=16384 || request.size!=3 || request[0]!="GET" || !request[1].startsWith("/auth/callback?")) {
                        respond(candidate,404,"Это служебный адрес входа. Продолжите вход через ChatGPT.");candidate.close();browser=null;continue
                    }
                    client=candidate
                    accepted=ChatGptPkce.validateCallback(a,URI("http://127.0.0.1:${server.localPort}${request[1]}"))
                } catch(e: SocketTimeoutException) { candidate.close();browser=null;continue }
                catch(e: SocketException) {
                    candidate.close();browser=null
                    if(pending!==a)throw CancellationException("Authorization cancelled")
                    continue
                }
            }
            server.close();if(listener===server)listener=null
            progress(AuthStage.CALLBACK)
            val (code,issuedClient)=accepted
            progress(AuthStage.EXCHANGE)
            val tokens=authNetwork.json("https://auth.openai.com/api/accounts/oauth/token",GuideHttp.query(mapOf(
                "grant_type" to "authorization_code","client_id" to issuedClient,"code" to code,"code_verifier" to a.verifier,
                "redirect_uri" to a.redirectUri,"resource" to "https://api.openai.com/v1")),form=true)
            progress(AuthStage.VERIFY)
            val subject=validateId(tokens.getString("id_token"),issuedClient,a.nonce)
            val saved=store.load()
            if(a.issuedClient!=null && saved?.optString("subject")!=subject)throw IdentityCheckFailure(AuthError.ID_SUBJECT)
            tokens.put("client_id",issuedClient).put("subject",subject)
                .put("expires_at",System.currentTimeMillis()+tokens.optLong("expires_in",3600).coerceAtLeast(0)*1000)
            // Identity and plan permission are separate. A verified identity must survive declined plan use.
            progress(AuthStage.SAVE)
            kotlinx.coroutines.currentCoroutineContext().ensureActive();publishAccount(a,tokens)
            val status=if(hasPlan(tokens))AuthStage.CONNECTED else AuthStage.IDENTITY_ONLY
            progress(status)
            respond(client!!,200,status.label+". Сеанс проверен и сохранён на телефоне.")
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) {
            if(pending!==a)throw CancellationException("Authorization cancelled")
            val error=when(e) {
                is IdentityCheckFailure->e.code
                is SocketTimeoutException->if(stage==AuthStage.WAITING)AuthError.CALLBACK_TIMEOUT else AuthError.NETWORK
                is ServiceFailure->if(stage==AuthStage.EXCHANGE)AuthError.TOKEN_HTTP else AuthError.NETWORK
                else->when(stage) {
                    AuthStage.WAITING,AuthStage.CALLBACK->AuthError.CALLBACK_INVALID
                    AuthStage.SAVE->AuthError.SESSION_SAVE
                    AuthStage.VERIFY->AuthError.ID_FORMAT
                    else->AuthError.NETWORK
                }
            }
            val failure=AuthFailure(stage,error,(e as? ServiceFailure)?.safeMessage)
            record(stage,error);client?.let{respond(it,200,failure.userMessage())}
            throw failure
        } finally {
            server.close();client?.close()
            if(listener===server)listener=null
            if(browser===client)browser=null
            if(pending===a)pending=null
        }
    }
    @Synchronized private fun publishAccount(attempt: ChatGptPkce.Attempt,tokens: JSONObject) {
        if(pending!==attempt)throw CancellationException("Authorization cancelled")
        store.save(tokens)
    }
    @Synchronized private fun publishRefresh(version: Long,tokens: JSONObject) {
        if(version!=accountVersion)throw CancellationException("Account changed")
        store.save(tokens)
    }
    private suspend fun validateId(raw: String,client: String,nonce: String?): String {
        val discovery=authNetwork.json("https://auth.openai.com/.well-known/openid-configuration")
        if(discovery.getString("issuer")!="https://auth.openai.com")throw IdentityCheckFailure(AuthError.ID_ISSUER)
        val keys=JWKSet.parse(authNetwork.json(discovery.getString("jwks_uri")).toString())
        return IdTokenVerifier.verify(raw,keys,client,nonce)
    }
    suspend fun access(): String = lock.withLock {
        val version=accountVersion
        val t=store.load()?:throw ServiceFailure("Сначала войдите через ChatGPT.")
        if(!hasPlan(t))throw ServiceFailure("Аккаунт подключён, но использование подписки не разрешено. Нажмите «Разрешить использование подписки» в настройках гида.")
        if(t.getLong("expires_at")>System.currentTimeMillis()+60000)return@withLock t.getString("access_token")
        if(t.optString("refresh_token").isBlank())throw ServiceFailure("Для продления доступа повторите вход через ChatGPT.")
        val fresh=authNetwork.json("https://auth.openai.com/api/accounts/oauth/token",GuideHttp.query(mapOf(
            "grant_type" to "refresh_token","refresh_token" to t.getString("refresh_token"),"client_id" to t.getString("client_id"),"resource" to "https://api.openai.com/v1")),form=true)
        if(!fresh.has("scope"))fresh.put("scope",t.optString("scope"))
        if(!fresh.has("token_type"))fresh.put("token_type",t.optString("token_type"))
        if(fresh.has("id_token") && validateId(fresh.getString("id_token"),t.getString("client_id"),null)!=t.getString("subject"))throw IdentityCheckFailure(AuthError.ID_SUBJECT)
        if(!fresh.has("id_token"))fresh.put("id_token",t.getString("id_token"))
        fresh.put("client_id",t.getString("client_id")).put("subject",t.getString("subject"))
            .put("expires_at",System.currentTimeMillis()+fresh.getLong("expires_in")*1000)
        kotlinx.coroutines.currentCoroutineContext().ensureActive();publishRefresh(version,fresh)
        if(!hasPlan(fresh))throw ServiceFailure("Вход сохранён, но разрешение использовать подписку больше не предоставлено. Разрешите его в настройках гида.")
        return@withLock fresh.getString("access_token")
    }
    suspend fun models(): List<Pair<String,String>> {
        val models=GuideHttp.json("https://api.openai.com/v1/models",token=access()).getJSONArray("models")
        return (0 until models.length()).map{models.getJSONObject(it)}.filter{it.optString("visibility")=="list"}
            .map{it.getString("slug") to it.optString("display_name",it.getString("slug"))}
    }
    suspend fun generate(model: String,prompt: String,image: String?=null)=GuideHttp.response(access(),model,prompt,image)
}
