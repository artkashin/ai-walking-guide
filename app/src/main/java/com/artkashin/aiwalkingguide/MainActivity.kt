package com.artkashin.aiwalkingguide

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artkashin.aiwalkingguide.tour.*
import com.artkashin.aiwalkingguide.audio.PersistentPlayer
import java.io.File

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF176B59),secondary=Color(0xFF715930))) { GuideHome() } }
    }
    override fun onResume() {
        super.onResume()
        (application as GuideApp).guide.refreshConnection()
    }
}

@Composable
fun GuideHome() {
    val context=LocalContext.current
    val guide=(context.applicationContext as GuideApp).guide
    val s by guide.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var consent by remember { mutableStateOf(false) }
    var autoConsent by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var newConfirm by remember { mutableStateOf(false) }
    fun open(url: String) { try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) } catch(_: Exception) { guide.notice("Нет приложения для открытия ссылки.") } }
    fun startService() { try { ContextCompat.startForegroundService(context,Intent(context,GuideService::class.java)) } catch(_: Exception) { guide.notice("Не удалось запустить прогулку. Откройте приложение и разрешите геолокацию.") } }
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) startService()
        else guide.notice("Геолокация не разрешена. Музейный режим доступен без GPS.")
    }
    fun requestWalk() {
        val items=mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)
        if(Build.VERSION.SDK_INT>=33)items.add(Manifest.permission.POST_NOTIFICATIONS)
        permissions.launch(items.toTypedArray())
    }
    Scaffold(bottomBar={ Column {
        PersistentPlayer(s.playback,{guide.resume()},{guide.pause()},{guide.stopPlayback()},{guide.seekTo(it)})
        NavigationBar {
        listOf("Прогулка","Музей","Настройки").forEachIndexed { i,label -> NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("↗","▣","☰")[i])},label={Text(label)}) }
    } } }) { padding ->
        Column(Modifier.padding(padding).safeDrawingPadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("Гид рядом",style=MaterialTheme.typography.headlineLarge)
            Text("Идите своим путём",style=MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) { Text(s.message,Modifier.padding(16.dp)) }
            when(tab) {
                0 -> {
                    if(s.replay) Text("ТЕСТОВЫЙ GPS-РЕПЛЕЙ • сетевые запросы отключены",color=MaterialTheme.colorScheme.error)
                    s.position?.let { Text("Точность: ±${it.accuracy.toInt()} м • ${if(s.replay)"тестовая позиция" else "текущая позиция"}") }
                    Text("Маршрут — рекомендация. Места ищутся рядом с вашей фактической позицией, независимо от выбранных целей.")
                    Button(onClick={ if(s.active) { context.stopService(Intent(context,GuideService::class.java));guide.stop() } else if(!guide.searchConsent())consent=true else requestWalk() },Modifier.fillMaxWidth()) {
                        Text(if(s.active)"Завершить прогулку" else "Начать прогулку")
                    }
                    Text("Во время прогулки GPS работает и поверх Google Maps; уведомление позволяет остановить гид. Настройки экономии батареи могут ограничивать работу.",style=MaterialTheme.typography.bodySmall)
                    Row { Checkbox(checked=s.automatic,onCheckedChange={if(it)autoConsent=true else guide.automatic(false)});Text("Автоматические рассказы рядом",Modifier.padding(top=12.dp)) }
                    if(s.current!=null) {
                        Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(s.current!!.title,style=MaterialTheme.typography.titleLarge)
                            Text(if(s.text.isBlank())"Создаём рассказ…" else s.text)
                            Text("Пауза, остановка и перемотка — в плеере внизу экрана.",style=MaterialTheme.typography.bodySmall)
                        } }
                    }
                    if(s.goals.isNotEmpty()) {
                        Text("Цели прогулки",style=MaterialTheme.typography.titleLarge)
                        Text("Порядок адаптируется от текущей позиции. Это список целей, не построенный пешеходный путь: путь рассчитывает Google Maps.",style=MaterialTheme.typography.bodySmall)
                        s.goals.forEachIndexed { i,p ->
                            Text("${i+1}. ${p.title}")
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick={open(GuidePolicy.mapsUrl(p))}) { Text("Идти в Google Maps") }
                                TextButton(onClick={guide.addGoal(p,false)}) { Text("Убрать") }
                            }
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Рядом",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                        TextButton(onClick={guide.searchAgain()},enabled=s.active && !s.busy) { Text("Обновить") }
                    }
                    if(s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(s.pois.isEmpty()) Text("Если интересных новых объектов нет, гид молчит. Откройте настройки, чтобы подключить ChatGPT и русский голос.")
                    s.pois.sortedBy{s.position?.distance(it.position)?:0.0}.forEach { p ->
                        val used=p.id in s.excluded || p.aliases.any(s.excluded::contains)
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(p.title,style=MaterialTheme.typography.titleMedium)
                            s.position?.let { Text("${it.distance(p.position).toInt()} м по прямой${if(p.id in s.listened || p.aliases.any(s.listened::contains))" • Прослушано" else if(used)" • Пропущено или уже начато" else ""}") }
                            Text(p.extract.take(420))
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Button(onClick={guide.narrate(p)},enabled=!s.replay) { Text(if(used)"Повторить" else "Рассказать") }
                                TextButton(onClick={guide.addGoal(p,true)},enabled=!s.replay) { Text("В маршрут") }
                            }
                            TextButton(onClick={open(p.source)}) { Text("Источник Wikipedia · CC BY-SA") }
                        } }
                    }
                    if(s.history.isNotEmpty()) {
                        Text("Память экскурсии",style=MaterialTheme.typography.titleLarge)
                        Text("Завершённые и пропущенные рассказы не запускаются автоматически повторно, даже после перезапуска.")
                        s.history.take(30).forEach { (title,status) -> Text("• $title — ${when(status){"COMPLETED"->"прослушано";"SKIPPED"->"пропущено";else->"в работе"}}") }
                    }
                }
                1 -> MuseumScreen(guide,s)
                2 -> {
                    Text("ChatGPT и голос",style=MaterialTheme.typography.titleLarge)
                    Text(if(s.connected)"Аккаунт ChatGPT подключён. ${if(s.planEnabled)"Разрешение использовать подписку подтверждено." else "Разрешение использовать подписку пока не предоставлено."}" else "Вход через официальный OAuth с PKCE. Пароли, cookie и API-ключи не запрашиваются.")
                    if(s.authStatus.isNotBlank())Text("Статус входа: ${s.authStatus}",style=MaterialTheme.typography.bodyMedium)
                    if(s.authenticating) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Завершите вход в браузере и вернитесь. Ожидание — до трёх минут.")
                        OutlinedButton(onClick={guide.cancelAuth()}) { Text("Отменить вход") }
                    } else Button(onClick={guide.beginAuth(::open)},Modifier.fillMaxWidth()) { Text("Continue with ChatGPT") }
                    if(s.connected) {
                        if(!s.planEnabled && !s.authenticating)Button(onClick={guide.beginAuth(::open,true)}) { Text("Разрешить использование подписки") }
                        TextButton(onClick={guide.logout()}) { Text("Выйти и удалить токены с телефона") }
                        Text(if(s.planEnabled)"Разрешение подписки предоставлено. Управляйте лимитами в ChatGPT; приложение не покупает кредиты и не переключается на API-ключ." else "Вход в профиль выполнен. AI-запросы отключены, пока использование подписки не будет отдельно разрешено.")
                    }
                    TextButton(onClick={open("https://chatgpt.com/settings/usage")}) { Text("Управлять использованием ChatGPT") }
                    Text("Перед AI-запросами отключите использование дополнительных кредитов и автоматические покупки для этого приложения в ChatGPT. Приложение не может проверить эти настройки на сервере. Если отключить их нельзя — не включайте AI-запросы.")
                    Row { Checkbox(s.creditsDisabled,{guide.billingConfirmed(it)});Text("Подтверждаю: дополнительные кредиты и автопокупки отключены",Modifier.padding(top=12.dp)) }
                    if(s.connected && s.planEnabled) {
                        OutlinedButton(onClick={guide.loadModels()}) { Text("Загрузить доступные модели") }
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick={expanded=true},enabled=s.models.isNotEmpty()) { Text(s.models.firstOrNull{it.first==s.model}?.second?:"Выберите модель") }
                            DropdownMenu(expanded,onDismissRequest={expanded=false}) { s.models.forEach { (id,name) -> DropdownMenuItem(text={Text(name)},onClick={guide.model(id);expanded=false}) } }
                        }
                        Button(onClick={guide.testSubscription()},enabled=s.creditsDisabled && s.model.isNotBlank()) { Text("Проверить связь по подписке") }
                    }
                    HorizontalDivider()
                    Text("Русская озвучка",style=MaterialTheme.typography.titleLarge)
                    Text("Используется установленный офлайн-голос телефона. Звук создан ИИ. Если голоса нет, установите русский язык в настройках синтеза речи. Облачная платная озвучка не подключена.")
                    OutlinedButton(onClick={try { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }catch(_: Exception){context.startActivity(Intent(Settings.ACTION_SETTINGS))}}) { Text("Настроить русский голос") }
                    Text("Объём рассказа: ${s.detail}. Длительность зависит от содержания.")
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Краткий","Обычный","Подробный").forEach { detail -> FilterChip(selected=s.detail==detail,onClick={guide.detail(detail)},label={Text(detail)}) } }
                    HorizontalDivider()
                    Text("Данные и тестирование",style=MaterialTheme.typography.titleLarge)
                    Text("История мест и звук хранятся локально. Во время поиска координаты отправляются Wikipedia; для AI-рассказа — факты о выбранном объекте. Фото отправляется OpenAI только после отдельного подтверждения. Весь трек прогулки на сервер не загружается.")
                    TextButton(onClick={newConfirm=true}) { Text("Новая экскурсия") }
                    TextButton(onClick={clearConfirm=true}) { Text("Удалить историю и кэш") }
                    TextButton(onClick={context.stopService(Intent(context,GuideService::class.java));guide.replay();tab=0}) { Text("Запустить GPS-реплей без сети") }
                    if(s.replay)TextButton(onClick={guide.exitReplay()}) { Text("Завершить GPS-реплей") }
                    TextButton(onClick={open("https://github.com/artkashin/ai-walking-guide")}) { Text("Исходный код и ограничения") }
                    Text("Версия 0.2.1 · личный тестовый выпуск",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if(consent) AlertDialog(onDismissRequest={consent=false},title={Text("Начать прогулку?")},text={Text("Для поиска реальных объектов текущие координаты передаются публичным API Wikipedia. Полный трек не сохраняется. GPS продолжит работать с уведомлением, если открыть Google Maps. Автоматические AI-рассказы включаются отдельно.")},confirmButton={TextButton(onClick={guide.consentSearch(true);consent=false;requestWalk()}){Text("Разрешить и начать")}},dismissButton={TextButton(onClick={consent=false}){Text("Отмена")}})
    if(autoConsent) AlertDialog(onDismissRequest={autoConsent=false},title={Text("Автоматические рассказы")},text={Text("Разрешить во время активной прогулки автоматически отправлять факты о новых местах в ChatGPT, расходуя включённый лимит подписки, в том числе когда открыт Google Maps? Требуются подключённый аккаунт, модель и отключённые платные кредиты. В любое время можно выключить этот режим или завершить прогулку.")},confirmButton={TextButton(onClick={guide.automatic(true);autoConsent=false}){Text("Разрешить")}},dismissButton={TextButton(onClick={autoConsent=false}){Text("Отмена")}})
    if(clearConfirm) AlertDialog(onDismissRequest={clearConfirm=false},title={Text("Удалить локальные данные?")},text={Text("История экскурсий, сохранённые места и звук будут удалены. Это действие необратимо.")},confirmButton={TextButton(onClick={context.stopService(Intent(context,GuideService::class.java));guide.clearData();clearConfirm=false}){Text("Удалить")}},dismissButton={TextButton(onClick={clearConfirm=false}){Text("Отмена")}})
    if(newConfirm) AlertDialog(onDismissRequest={newConfirm=false},title={Text("Новая экскурсия?")},text={Text("Текущая прогулка остановится. В новой экскурсии места могут быть рассказаны заново.")},confirmButton={TextButton(onClick={context.stopService(Intent(context,GuideService::class.java));guide.newSession();newConfirm=false}){Text("Начать новую")}},dismissButton={TextButton(onClick={newConfirm=false}){Text("Отмена")}})
}

@Composable
private fun MuseumScreen(guide: TourController,s: GuideState) {
    val context=LocalContext.current
    var museum by rememberSaveable{mutableStateOf("")}
    var pending by rememberSaveable{mutableStateOf<String?>(null)}
    var camera by rememberSaveable{mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->pending=uri?.toString()}
    val capture=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->pending=if(ok)camera else null;if(!ok)File(context.cacheDir,"museum").listFiles()?.forEach{it.delete()} }
    Text("Музейный режим",style=MaterialTheme.typography.headlineMedium)
    Text("Сфотографируйте произведение или этикетку. Гид использует доступный контекст музея и предложит гипотезу. Сверяйте атрибуцию с этикеткой.")
    OutlinedTextField(value=museum,onValueChange={museum=it.take(300)},label={Text("Название музея или зала")},modifier=Modifier.fillMaxWidth())
    Button(onClick={picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!s.museumBusy,modifier=Modifier.fillMaxWidth()) { Text("Выбрать фотографию") }
    OutlinedButton(onClick={
        val file=File(File(context.cacheDir,"museum").apply{mkdirs()},"capture-${System.currentTimeMillis()}.jpg")
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.photos",file);camera=uri.toString()
        try { capture.launch(uri) } catch(_: Exception) { file.delete();guide.notice("Камера недоступна. Выберите готовую фотографию.") }
    },enabled=!s.museumBusy,modifier=Modifier.fillMaxWidth()) { Text("Сфотографировать") }
    if(s.museumBusy) { LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={guide.cancelMuseum()}) { Text("Отменить распознавание") } }
    if(s.museumAnswer.isNotBlank()) {
        Card { Text(s.museumAnswer,Modifier.padding(16.dp)) }
        s.museumCandidate?.let { p -> Button(onClick={guide.narrate(p)},enabled=s.current==null) { Text("Подтверждаю по этикетке — рассказать") } }
        TextButton(onClick={guide.cancelMuseum()}) { Text("Не подтверждаю — очистить") }
    }
    if(pending!=null) AlertDialog(onDismissRequest={pending=null},title={Text("Отправить фото в ChatGPT?")},text={Text("Фото будет уменьшено, метаданные EXIF удалены. Изображение, название музея и названия ближайших объектов отправятся OpenAI с использованием выбранной модели и лимита подписки. Не отправляйте фотографии людей и документов без необходимости. После обработки локальная копия снимка камеры удаляется.")},confirmButton={TextButton(onClick={val uri=Uri.parse(pending);pending=null;guide.museum(uri,museum)}){Text("Отправить")}},dismissButton={TextButton(onClick={pending=null;File(context.cacheDir,"museum").listFiles()?.forEach{it.delete()}}){Text("Отмена")}})
}
