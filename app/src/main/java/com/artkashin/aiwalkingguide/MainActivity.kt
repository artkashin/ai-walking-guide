package com.artkashin.aiwalkingguide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { GuideHome() } }
    }
}

@Composable
fun GuideHome() {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Гид рядом", style = MaterialTheme.typography.headlineLarge)
            Text("Идите своим путём", style = MaterialTheme.typography.titleLarge)
            Text("Маршрут — рекомендация. Будущий гид будет искать интересные места рядом с вами, даже если вы свернёте с пути.")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Основа приложения готовится", style = MaterialTheme.typography.titleMedium)
                    Text("Карта, GPS и экскурсии появятся на следующих этапах. Сейчас приложение не собирает геолокацию и не отправляет запросы ИИ.")
                }
            }
            Text("Если рядом нет ничего нового, гид будет молчать. Прослушанные и пропущенные рассказы не повторяются автоматически.")
            OutlinedButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "Скрыть статус подключения" else "Статус подключения ChatGPT")
            }
            if (showDetails) {
                Text("Вход пока не включён", style = MaterialTheme.typography.titleMedium)
                Text("Изучен официальный вход с PKCE для открытых приложений. Совместимость с Android и доступ по подписке ещё требуют проверки на устройстве и вашего разрешения. Платный API не подключён.")
            }
            HorizontalDivider()
            Text("Музейный режим", style = MaterialTheme.typography.titleMedium)
            Text("В следующих версиях можно будет сфотографировать произведение и получить рассказ с учётом музея и контекста экскурсии.")
        }
    }
}
