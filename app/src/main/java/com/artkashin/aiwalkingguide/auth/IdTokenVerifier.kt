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
        val key=keys.getKeyByKeyId(jwt.header.keyID)?:throw IdentityCheckFailure(AuthError.ID_SIGNATURE)
        val valid=when {
            jwt.header.algorithm==JWSAlgorithm.RS256 && key is RSAKey -> jwt.verify(RSASSAVerifier(key))
            jwt.header.algorithm==JWSAlgorithm.ES256 && key is ECKey -> jwt.verify(ECDSAVerifier(key))
            else -> false
        }
        if(!valid)throw IdentityCheckFailure(AuthError.ID_SIGNATURE)
        val c=jwt.jwtClaimsSet
        if(c.issuer!="https://auth.openai.com")throw IdentityCheckFailure(AuthError.ID_ISSUER)
        if(client !in c.audience)throw IdentityCheckFailure(AuthError.ID_AUDIENCE)
        if(c.expirationTime?.time?.let{it>now}!=true)throw IdentityCheckFailure(AuthError.ID_EXPIRED)
        if(c.notBeforeTime?.time?.let{it<=now+30000}==false || c.issueTime?.time?.let{it<=now+30000}!=true)throw IdentityCheckFailure(AuthError.ID_TIME)
        if(c.audience.size>1 && c.getStringClaim("azp")!=client)throw IdentityCheckFailure(AuthError.ID_AUDIENCE)
        if(nonce!=null && c.getStringClaim("nonce")!=nonce)throw IdentityCheckFailure(AuthError.ID_NONCE)
        if(c.subject.isNullOrBlank())throw IdentityCheckFailure(AuthError.ID_SUBJECT)
        return c.subject
    }
}
