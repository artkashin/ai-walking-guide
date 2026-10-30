package com.artkashin.aiwalkingguide.auth

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class ChatGptPkceTest {
    private fun attempt() = ChatGptPkce.newRegistration("urn:uuid:ec0aeb5d-ec6b-4420-a63d-515ed79ab52d", 1455)
    @Test fun rfc7636ChallengeVector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", ChatGptPkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
    @Test fun freshSecretsAndOfficialEndpoint() {
        val a = attempt()
        val b = attempt()
        assertNotEquals(a.state, b.state)
        assertNotEquals(a.nonce, b.nonce)
        assertNotEquals(a.verifier, b.verifier)
        assertEquals(43, a.verifier.length)
        assertEquals("auth.openai.com", URI(a.authorizationUri).host)
        assertFalse(a.authorizationUri.contains(a.verifier))
        assertTrue(a.authorizationUri.contains("chatgpt.tokens.use.direct"))
    }
    @Test fun acceptsIssuedClientOnlyAfterValidState() {
        val a = attempt()
        assertEquals("code" to "oaiapp_test", ChatGptPkce.validateCallback(a, URI("${a.redirectUri}?state=${a.state}&code=code&client_id=oaiapp_test")))
    }
    @Test fun rejectsInjectionDenialAndEndpointChanges() {
        val a = attempt()
        val valid = "state=${a.state}&code=code&client_id=oaiapp_test"
        listOf(
            "${a.redirectUri}?state=wrong&code=code&client_id=oaiapp_test",
            "${a.redirectUri}?$valid&state=${a.state}",
            "${a.redirectUri}?$valid&error=access_denied",
            "http://localhost:1455/auth/callback?$valid",
            "http://127.0.0.1:1456/auth/callback?$valid",
            "http://127.0.0.1:1455/callback?$valid",
            "${a.redirectUri}?state=${a.state}&code=code&client_id=dynamic_agent_client"
        ).forEach { uri -> assertThrows(IllegalArgumentException::class.java) { ChatGptPkce.validateCallback(a, URI(uri)) } }
    }
}
