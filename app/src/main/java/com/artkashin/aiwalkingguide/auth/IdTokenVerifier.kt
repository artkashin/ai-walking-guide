package com.artkashin.aiwalkingguide.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jwt.SignedJWT

object IdTokenVerifier {
    fun verify(raw: String,keys: JWKSet,client: String,nonce: String?,now: Long=System.currentTimeMillis()): String {
        val jwt=SignedJWT.parse(raw)
        val key=keys.getKeyByKeyId(jwt.header.keyID)?:throw IllegalArgumentException("Signing key absent")
        val valid=when {
            jwt.header.algorithm==JWSAlgorithm.RS256 && key is RSAKey -> jwt.verify(RSASSAVerifier(key))
            jwt.header.algorithm==JWSAlgorithm.ES256 && key is ECKey -> jwt.verify(ECDSAVerifier(key))
            else -> false
        }
        require(valid)
        val c=jwt.jwtClaimsSet
        require(c.issuer=="https://auth.openai.com" && client in c.audience && c.expirationTime?.time?.let{it>now}==true)
        require(c.notBeforeTime?.time?.let{it<=now+30000}!=false)
        require(c.issueTime?.time?.let{it<=now+30000}==true)
        if(c.audience.size>1)require(c.getStringClaim("azp")==client)
        if(nonce!=null)require(c.getStringClaim("nonce")==nonce)
        require(!c.subject.isNullOrBlank())
        return c.subject
    }
}
