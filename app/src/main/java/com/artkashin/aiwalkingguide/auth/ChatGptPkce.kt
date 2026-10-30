package com.artkashin.aiwalkingguide.auth

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Pure protocol helpers shared by the live local-phone OAuth adapter and JVM tests. */
object ChatGptPkce {
    private fun randomValue(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

    fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)))

    data class Attempt(
        val redirectUri: String,
        val state: String,
        val nonce: String,
        val verifier: String,
        val authorizationUri: String,
        val issuedClient: String? = null
    )

    fun newRegistration(hostId: String, port: Int, issuedClient: String? = null, idTokenHint: String? = null): Attempt {
        require(hostId.startsWith("urn:uuid:"))
        val hostUuid = java.util.UUID.fromString(hostId.removePrefix("urn:uuid:"))
        require(hostUuid.version() == 4 && hostUuid.variant() == 2)
        require(port in 1024..65535)
        val redirect = "http://127.0.0.1:$port/auth/callback"
        val state = randomValue()
        val nonce = randomValue()
        val verifier = randomValue()
        val params = linkedMapOf(
            "client_id" to (issuedClient ?: "dynamic_agent_client"),
            "agent_name_hint" to "AI Walking Guide",
            "ext_agent_host_id" to hostId,
            "response_type" to "code",
            "redirect_uri" to redirect,
            "scope" to "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct",
            "resource" to "https://api.openai.com/v1",
            "state" to state, "nonce" to nonce,
            "code_challenge_method" to "S256", "code_challenge" to challenge(verifier)
        )
        if (issuedClient != null) {
            require(issuedClient.startsWith("oaiapp_"))
            params.remove("agent_name_hint")
            if (!idTokenHint.isNullOrBlank()) params["id_token_hint"] = idTokenHint
        }
        val query = params.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
        return Attempt(redirect, state, nonce, verifier, "https://auth.openai.com/api/accounts/authorize?$query", issuedClient)
    }

    /** State alone is insufficient: pin the loopback endpoint and reject malformed callbacks. */
    fun validateCallback(attempt: Attempt, callback: URI): Pair<String, String> {
        val expected = URI(attempt.redirectUri)
        require(callback.scheme == expected.scheme && callback.host == expected.host &&
            callback.port == expected.port && callback.path == expected.path &&
            callback.userInfo == null && callback.fragment == null)
        val pairs = callback.rawQuery.orEmpty().split('&').map {
            val parts = it.split('=', limit = 2)
            require(parts.size == 2)
            java.net.URLDecoder.decode(parts[0], "UTF-8") to java.net.URLDecoder.decode(parts[1], "UTF-8")
        }
        require(pairs.map { it.first }.distinct().size == pairs.size)
        val params = pairs.toMap()
        require(MessageDigest.isEqual(attempt.state.toByteArray(), params["state"].orEmpty().toByteArray()))
        require("error" !in params)
        val code = requireNotNull(params["code"]).also { require(it.isNotBlank()) }
        val client = requireNotNull(params["client_id"] ?: attempt.issuedClient).also { require(it.startsWith("oaiapp_") && it.length > 7) }
        if (attempt.issuedClient != null) require(client == attempt.issuedClient)
        return code to client
    }
}
