package com.artkashin.aiwalkingguide.auth

import android.content.Context
import com.artkashin.aiwalkingguide.net.*
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.util.UUID

class ChatGptAccount(context: Context) {
    private val store=SecureAccount(context)
    private val prefs=context.getSharedPreferences("auth-host",Context.MODE_PRIVATE)
    private val lock=Mutex()
    @Volatile private var accountVersion=0L
    @Volatile private var listener: ServerSocket?=null
    private var pending: ChatGptPkce.Attempt?=null
    fun connected()=store.load()?.optString("access_token")?.isNotBlank()==true
    fun disconnectLocal(): JSONObject? { val old=store.load();accountVersion++;cancel();store.clear();return old }
    suspend fun revoke(old: JSONObject?) {
        if(old==null || old.optString("refresh_token").isBlank())return
        val discovery=GuideHttp.json("https://auth.openai.com/.well-known/openid-configuration")
        val c=GuideHttp.connection(discovery.getString("revocation_endpoint"))
        withContext(Dispatchers.IO) {
            try {
                c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/x-www-form-urlencoded")
                c.outputStream.use { it.write(GuideHttp.query(mapOf("token" to old.getString("refresh_token"),"client_id" to old.getString("client_id"),"token_type_hint" to "refresh_token")).toByteArray()) }
                GuideHttp.check(c)
            } finally { c.disconnect() }
        }
    }
    fun cancel() { listener?.close();listener=null;pending=null }
    fun begin(): String {
        cancel()
        val host=prefs.getString("host",null)?:"urn:uuid:${UUID.randomUUID()}".also{prefs.edit().putString("host",it).commit()}
        val socket=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).apply { soTimeout=180000 }
        listener=socket
        val saved=store.load()
        return ChatGptPkce.newRegistration(host,socket.localPort,saved?.optString("client_id"),saved?.optString("id_token")).also { pending=it }.authorizationUri
    }
    suspend fun finish(): Unit = withContext(Dispatchers.IO) {
        val socket=listener?:throw ServiceFailure("Вход отменён.")
        val a=pending?:throw ServiceFailure("Вход отменён.")
        try {
            val accepted=socket.accept().use { client ->
                client.soTimeout=5000
                val reader=client.getInputStream().bufferedReader()
                val line=StringBuilder()
                while(line.length<8192) { val ch=reader.read();if(ch<0||ch==10)break;line.append(ch.toChar()) }
                require(line.length<8192)
                val request=line.toString().trim().split(' ')
                require(request.size==3 && request[0]=="GET" && request[1].startsWith("/auth/callback?"))
                val result=ChatGptPkce.validateCallback(a,URI("http://127.0.0.1:${socket.localPort}${request[1]}"))
                val html="Вход получен. Вернитесь в AI Walking Guide.".toByteArray()
                client.getOutputStream().use { out ->
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${html.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray());out.write(html)
                }
                result
            }
            socket.close();listener=null
            val (code,client)=accepted
            val tokens=GuideHttp.json("https://auth.openai.com/api/accounts/oauth/token",GuideHttp.query(mapOf(
                "grant_type" to "authorization_code","client_id" to client,"code" to code,"code_verifier" to a.verifier,
                "redirect_uri" to a.redirectUri,"resource" to "https://api.openai.com/v1")),form=true)
            val claims=validateId(tokens.getString("id_token"),client,a.nonce)
            val saved=store.load()
            if(a.issuedClient!=null) require(saved?.optString("subject")==claims)
            require(tokens.optString("token_type").equals("Bearer",true))
            require(tokens.optString("scope").split(' ').contains("chatgpt.tokens.use.direct")) { "Разрешение использовать подписку не предоставлено." }
            require(tokens.optString("access_token").isNotBlank())
            tokens.put("client_id",client).put("subject",claims).put("expires_at",System.currentTimeMillis()+tokens.getLong("expires_in")*1000)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            require(pending===a)
            store.save(tokens)
        } finally { socket.close();if(listener===socket)listener=null;if(pending===a)pending=null }
    }
    private suspend fun validateId(raw: String,client: String,nonce: String?): String {
        val discovery=GuideHttp.json("https://auth.openai.com/.well-known/openid-configuration")
        require(discovery.getString("issuer")=="https://auth.openai.com")
        val keys=JWKSet.parse(GuideHttp.json(discovery.getString("jwks_uri")).toString())
        return IdTokenVerifier.verify(raw,keys,client,nonce)
    }
    suspend fun access(): String = lock.withLock {
        val version=accountVersion
        val t=store.load()?:throw ServiceFailure("Сначала войдите через ChatGPT.")
        if(t.getLong("expires_at")>System.currentTimeMillis()+60000) return@withLock t.getString("access_token")
        val fresh=GuideHttp.json("https://auth.openai.com/api/accounts/oauth/token",GuideHttp.query(mapOf(
            "grant_type" to "refresh_token","refresh_token" to t.getString("refresh_token"),"client_id" to t.getString("client_id"),"resource" to "https://api.openai.com/v1")),form=true)
        require(fresh.optString("scope").split(' ').contains("chatgpt.tokens.use.direct"))
        if(fresh.has("id_token")) require(validateId(fresh.getString("id_token"),t.getString("client_id"),null)==t.getString("subject"))
        require(fresh.optString("access_token").isNotBlank() && fresh.optString("refresh_token").isNotBlank())
        if(!fresh.has("id_token"))fresh.put("id_token",t.optString("id_token"))
        fresh.put("client_id",t.getString("client_id")).put("subject",t.getString("subject"))
            .put("expires_at",System.currentTimeMillis()+fresh.getLong("expires_in")*1000)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        require(version==accountVersion)
        store.save(fresh);fresh.getString("access_token")
    }
    suspend fun models(): List<Pair<String,String>> {
        val models=GuideHttp.json("https://api.openai.com/v1/models",token=access()).getJSONArray("models")
        return (0 until models.length()).map{models.getJSONObject(it)}.filter{it.optString("visibility")=="list"}
            .map{it.getString("slug") to it.optString("display_name",it.getString("slug"))}
    }
    suspend fun generate(model: String,prompt: String,image: String?=null)=GuideHttp.response(access(),model,prompt,image)
}
