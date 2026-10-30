package com.artkashin.aiwalkingguide

import androidx.test.platform.app.InstrumentationRegistry
import com.artkashin.aiwalkingguide.auth.*
import com.artkashin.aiwalkingguide.net.ServiceFailure
import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.util.Date

/** Complete local browser callback -> fake HTTPS issuer -> real Keystore, with no live account. */
class AuthFlowTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    companion object { private val key=RSAKeyGenerator(2048).keyID("native-fixture").generate() }
    private fun query(raw: String)=raw.split('&').associate { item ->
        val p=item.split('=',limit=2);URLDecoder.decode(p[0],"UTF-8") to URLDecoder.decode(p[1],"UTF-8")
    }
    private class FixtureNetwork(private val plan: Boolean,private val wrongNonce: Boolean=false): AuthNetwork {
        var nonce="";var exchanges=0
        val exchangeStarted=CompletableDeferred<Unit>();val allowExchange=CompletableDeferred<Unit>()
        override suspend fun json(url: String,body: String?,form: Boolean): JSONObject {
            if(url.endsWith("/oauth/token")) {
                exchanges++;exchangeStarted.complete(Unit);allowExchange.await()
                val now=System.currentTimeMillis()
                val jwt=SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID("native-fixture").build(),
                    JWTClaimsSet.Builder().issuer("https://auth.openai.com").audience("oaiapp_native_fixture")
                        .subject("synthetic-native-user").issueTime(Date(now-1000)).expirationTime(Date(now+60000))
                        .claim("nonce",if(wrongNonce)"wrong-fixture-nonce" else nonce).build())
                jwt.sign(RSASSASigner(key))
                return JSONObject().put("id_token",jwt.serialize()).put("token_type","Bearer").put("expires_in",3600)
                    .put("access_token","synthetic-access-only").put("refresh_token","synthetic-refresh-only")
                    .put("scope",if(plan)"openid profile email offline_access resource.invoke chatgpt.tokens.use.direct" else "openid profile email")
            }
            if(url.endsWith("openid-configuration"))return JSONObject().put("issuer","https://auth.openai.com").put("jwks_uri","https://auth.openai.com/fixture-jwks")
            if(url.endsWith("fixture-jwks"))return JSONObject(JWKSet(key.toPublicJWK()).toString())
            throw AssertionError("Unexpected fixture transport request")
        }
    }
    private fun callback(params: Map<String,String>,path: String?=null): String {
        val redirect=URI(params.getValue("redirect_uri"))
        return Socket("127.0.0.1",redirect.port).use { socket ->
            socket.soTimeout=15000
            val target=path?:"${redirect.path}?state=${params.getValue("state")}&code=fixture-code&client_id=oaiapp_native_fixture"
            socket.getOutputStream().write("GET $target HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".toByteArray());socket.getOutputStream().flush()
            socket.getInputStream().bufferedReader().readText()
        }
    }
    @Test fun noPrematureSuccessAndUnrelatedBrowserRequestDoesNotConsumeLogin()=runBlocking {
        val network=FixtureNetwork(true);val account=ChatGptAccount(context,network);account.disconnectLocal()
        try {
            val params=query(URI(account.begin()).rawQuery);network.nonce=params.getValue("nonce")
            val finish=async(Dispatchers.IO){runCatching{account.finish()}}
            assertTrue(withContext(Dispatchers.IO){callback(params,"/favicon.ico")}.contains("404"))
            val page=async(Dispatchers.IO){runCatching{callback(params)}}
            withTimeout(15000){network.exchangeStarted.await()}
            assertFalse(account.connected());assertFalse("Browser cannot show success before token verification",page.isCompleted)
            network.allowExchange.complete(Unit)
            withTimeout(15000){finish.await().getOrThrow()}
            assertTrue(withTimeout(15000){page.await().getOrThrow()}.contains("Сеанс проверен и сохранён"))
            assertTrue(account.connected());assertTrue(account.planEnabled())
            assertTrue(ChatGptAccount(context,network).connected())
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val guide=(context.applicationContext as GuideApp).guide
                guide.refreshConnection()
                assertTrue(guide.state.value.connected);assertTrue(guide.state.value.planEnabled)
            }
        } finally {
            account.disconnectLocal()
            InstrumentationRegistry.getInstrumentation().runOnMainSync{(context.applicationContext as GuideApp).guide.refreshConnection()}
        }
    }
    @Test fun identityOnlyLoginPersistsButCannotInvokeInference()=runBlocking {
        val network=FixtureNetwork(false);network.allowExchange.complete(Unit)
        val account=ChatGptAccount(context,network);account.disconnectLocal()
        try {
            val params=query(URI(account.begin()).rawQuery);network.nonce=params.getValue("nonce")
            val finish=async(Dispatchers.IO){runCatching{account.finish()}}
            val page=async(Dispatchers.IO){runCatching{callback(params)}}
            withTimeout(15000){finish.await().getOrThrow()}
            assertTrue(page.await().getOrThrow().contains("использование подписки не разрешено"))
            assertTrue(account.connected());assertFalse(account.planEnabled())
            val restored=ChatGptAccount(context,network)
            assertTrue(restored.connected());assertFalse(restored.planEnabled())
            val error=runCatching{restored.access()}.exceptionOrNull()
            assertTrue(error is ServiceFailure);assertEquals(1,network.exchanges)
        } finally { account.disconnectLocal() }
    }
    @Test fun invalidNonceShowsSafeStageCodeAndNeverCreatesSession()=runBlocking {
        val network=FixtureNetwork(true,true);network.allowExchange.complete(Unit)
        val account=ChatGptAccount(context,network);account.disconnectLocal()
        try {
            val params=query(URI(account.begin()).rawQuery);network.nonce=params.getValue("nonce")
            val finish=async(Dispatchers.IO){runCatching{account.finish()}}
            val page=async(Dispatchers.IO){runCatching{callback(params)}}
            val error=withTimeout(15000){finish.await()}.exceptionOrNull() as AuthFailure
            assertEquals(AuthError.ID_NONCE,error.code)
            val html=page.await().getOrThrow()
            assertTrue(html.contains("ID_NONCE"));assertFalse(html.contains("synthetic-access-only"))
            assertFalse(account.connected());assertTrue(account.diagnostic().contains("ID_NONCE"))
        } finally { account.disconnectLocal() }
    }
    @Test fun cancelledExchangeCannotRestoreCredentialsLater()=runBlocking {
        val network=FixtureNetwork(true);val account=ChatGptAccount(context,network);account.disconnectLocal()
        try {
            val params=query(URI(account.begin()).rawQuery);network.nonce=params.getValue("nonce")
            val finish=async(Dispatchers.IO){runCatching{account.finish()}}
            val page=async(Dispatchers.IO){runCatching{callback(params)}}
            withTimeout(15000){network.exchangeStarted.await()}
            account.cancel();network.allowExchange.complete(Unit)
            assertTrue(withTimeout(15000){finish.await()}.isFailure)
            withTimeout(15000){page.await()}
            assertFalse(account.connected())
        } finally { account.disconnectLocal() }
    }
}
