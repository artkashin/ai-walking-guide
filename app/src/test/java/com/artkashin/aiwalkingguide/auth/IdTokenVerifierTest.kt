package com.artkashin.aiwalkingguide.auth

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.*
import java.util.Date
import org.junit.Assert.*
import org.junit.Test

class IdTokenVerifierTest {
    companion object { private val key=RSAKeyGenerator(2048).keyID("fixture").generate() }
    private val now=1_790_000_000_000L
    private fun token(issuer: String="https://auth.openai.com",aud: String="oaiapp_test",nonce: String="nonce",expiry: Long=now+60000): String {
        val claims=JWTClaimsSet.Builder().issuer(issuer).audience(aud).subject("synthetic-user").issueTime(Date(now-1000)).expirationTime(Date(expiry)).claim("nonce",nonce).build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID("fixture").build(),claims).apply{sign(RSASSASigner(key))}.serialize()
    }
    @Test fun validSignatureAndBoundClaimsAcceptIdentity() {
        assertEquals("synthetic-user",IdTokenVerifier.verify(token(),JWKSet(key.toPublicJWK()),"oaiapp_test","nonce",now))
    }
    @Test fun invalidIssuerAudienceNonceAndExpiredTokenAreRejected() {
        listOf(token(issuer="https://attacker.invalid"),token(aud="other"),token(nonce="other"),token(expiry=now-1)).forEach {
            assertThrows(IllegalArgumentException::class.java){IdTokenVerifier.verify(it,JWKSet(key.toPublicJWK()),"oaiapp_test","nonce",now)}
        }
    }
    @Test fun differentSigningKeyCannotAuthenticate() {
        val wrong=RSAKeyGenerator(2048).keyID("fixture").generate()
        assertThrows(IllegalArgumentException::class.java){IdTokenVerifier.verify(token(),JWKSet(wrong.toPublicJWK()),"oaiapp_test","nonce",now)}
    }
}
