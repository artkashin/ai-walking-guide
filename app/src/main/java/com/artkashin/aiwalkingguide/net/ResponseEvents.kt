package com.artkashin.aiwalkingguide.net

import org.json.JSONObject

/** Never promotes a partial stream to a completed narration. */
class ResponseEvents {
    private val text=StringBuilder()
    var completed=false
        private set
    fun accept(raw: String) {
        if(raw=="[DONE]") return
        check(!completed)
        val j=JSONObject(raw)
        when(j.optString("type")) {
            "response.output_text.delta" -> text.append(j.optString("delta"))
            "response.completed" -> completed=true
            "response.failed","response.incomplete","error" -> throw ServiceFailure("ChatGPT не завершил ответ: возможен лимит подписки или недоступная модель. Проверьте использование в ChatGPT.")
        }
        if(text.length>100000)throw ServiceFailure("Рассказ слишком большой.")
    }
    fun result(): String {
        if(!completed || text.isBlank())throw ServiceFailure("Ответ ChatGPT прерван. Неполный рассказ не воспроизводится.")
        return text.toString()
    }
}
