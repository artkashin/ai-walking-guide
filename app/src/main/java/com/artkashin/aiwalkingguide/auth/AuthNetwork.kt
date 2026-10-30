package com.artkashin.aiwalkingguide.auth

import com.artkashin.aiwalkingguide.net.GuideHttp
import org.json.JSONObject

/** Injectable transport allows the complete callback/token flow to be tested without an account. */
interface AuthNetwork {
    suspend fun json(url: String,body: String?=null,form: Boolean=false): JSONObject
}

object OpenAiAuthNetwork: AuthNetwork {
    override suspend fun json(url: String,body: String?,form: Boolean)=GuideHttp.json(url,body,form=form)
}
