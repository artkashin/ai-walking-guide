package com.artkashin.aiwalkingguide.auth

enum class AuthStage(val label: String) {
    WAITING("Ожидаем завершения входа в браузере"),
    CALLBACK("Ответ браузера получен"),
    EXCHANGE("Получаем токены OpenAI"),
    VERIFY("Проверяем подпись и параметры входа"),
    SAVE("Сохраняем защищённый сеанс"),
    CONNECTED("Аккаунт и разрешение подписки подключены"),
    IDENTITY_ONLY("Аккаунт подключён; использование подписки не разрешено")
}

enum class AuthError(val description: String) {
    CALLBACK_TIMEOUT("Браузер не вернул результат вовремя. Повторите вход."),
    CALLBACK_INVALID("Ответ браузера не прошёл проверку безопасности. Повторите вход."),
    TOKEN_HTTP("OpenAI не выдал токены. Возможно ограничение аккаунта или регистрации приложения."),
    ID_SIGNATURE("Подпись ответа OpenAI не прошла проверку."),
    ID_ISSUER("Ответ пришёл от неподходящего издателя."),
    ID_AUDIENCE("Ответ выдан другому приложению."),
    ID_EXPIRED("Токен истёк. Проверьте автоматическую дату и время телефона."),
    ID_TIME("Время токена некорректно. Проверьте автоматическую дату и время телефона."),
    ID_NONCE("Ответ не соответствует этому входу. Повторите вход."),
    ID_SUBJECT("Не удалось подтвердить выбранный аккаунт."),
    ID_FORMAT("Формат ответа OpenAI не удалось проверить."),
    SESSION_SAVE("Не удалось сохранить защищённый сеанс на телефоне."),
    NETWORK("Не удалось связаться с OpenAI. Проверьте интернет и повторите вход."),
    CANCELLED("Вход отменён.")
}

class IdentityCheckFailure(val code: AuthError): IllegalArgumentException(code.name)
class AuthFailure(val stage: AuthStage,val code: AuthError,val httpMessage: String?=null): Exception(code.name) {
    fun userMessage()="${stage.label}: ${code.description} Код: ${code.name}." + (httpMessage?.let{" $it"}?:"")
}
