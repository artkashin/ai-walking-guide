package com.artkashin.aiwalkingguide.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.artkashin.aiwalkingguide.tour.*

@Composable
fun HandsFreePanel(guide: TourController,s: GuideState) {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    var modelConsent by remember{mutableStateOf(false)}
    var micConsent by remember{mutableStateOf(false)}
    var phrase by rememberSaveable(s.voice.phrase){mutableStateOf(s.voice.phrase)}
    fun service(action: String) {
        if(!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)){guide.notice("Откройте экран гида и включите микрофон вручную.");return}
        try{ContextCompat.startForegroundService(context,Intent(context,GuideService::class.java).setAction(action))}
        catch(_: Exception){guide.notice("Не удалось включить микрофон из текущего состояния Android. Откройте приложение и повторите.")}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted->
        if(granted && guide.state.value.active)service("voice_on") else guide.notice("Микрофон не разрешён или прогулка завершена. Голосовой режим не включён.")
    }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Hands-free: «${s.voice.phrase}»",style=MaterialTheme.typography.titleMedium)
        Text(s.voice.phase.label)
        if(s.voice.error.isNotBlank())Text(s.voice.error)
        Text("Пока гид говорит, микрофон выключен. Для перебивания нажмите паузу в плеере/уведомлении; через две секунды произнесите фразу и команду. Фоновая работа и Bluetooth зависят от телефона.",style=MaterialTheme.typography.bodySmall)
        if(!s.voiceModelReady) {
            Text("Нужна локальная русская модель: около 45 МБ загрузки и 90 МБ на телефоне. Аккаунт и платный API распознавания не нужны.")
            if(s.modelDownload!=null){LinearProgressIndicator(progress={s.modelDownload/100f},modifier=Modifier.fillMaxWidth());Text("Загрузка ${s.modelDownload}%")}
            else OutlinedButton(onClick={modelConsent=true}){Text("Загрузить офлайн-модель")}
        } else {
            OutlinedTextField(value=phrase,onValueChange={phrase=it.take(50)},enabled=!s.voice.enabled,label={Text("Фраза пробуждения, 2–4 русских слова")},modifier=Modifier.fillMaxWidth())
            if(!s.voice.enabled)TextButton(onClick={guide.wakePhrase(phrase)}){Text("Сохранить фразу")}
            Button(onClick={if(s.voice.enabled)service("voice_off") else micConsent=true},enabled=s.active && !s.replay,modifier=Modifier.fillMaxWidth()) {
                Text(if(s.voice.enabled)"Выключить микрофон" else "Включить hands-free на эту прогулку")
            }
            if(!s.active)Text("Сначала начните прогулку. Вне активной прогулки микрофон не запускается.")
        }
    } }
    if(modelConsent)AlertDialog(onDismissRequest={modelConsent=false},title={Text("Загрузить русскую модель?")},text={Text("Около 45 МБ будут загружены с alphacephei.com и проверены по контрольной сумме. Требуется около 90 МБ постоянного и до 200 МБ временного места. Модель Vosk распространяется по Apache 2.0; микрофон во время загрузки не включается.")},confirmButton={TextButton(onClick={modelConsent=false;guide.installVoiceModel()}){Text("Загрузить")}},dismissButton={TextButton(onClick={modelConsent=false}){Text("Отмена")}})
    if(micConsent)AlertDialog(onDismissRequest={micConsent=false},title={Text("Включить микрофон на прогулке?")},text={Text("Микрофон будет работать с постоянным уведомлением, в том числе при заблокированном экране на поддерживаемых телефонах. Фраза и команда распознаются локально; звук ожидания не отправляется и не сохраняется. После фразы вопрос, контекст рассказов/диалога, цели и свежая приблизительная позиция отправятся в ChatGPT с использованием включённого лимита подписки. Команды паузы/продолжения выполняются локально. Во время речи и звонков микрофон отключён. Завершение прогулки останавливает запись. Для запросов нужны разрешённая подписка и отключённые дополнительные кредиты.")},confirmButton={TextButton(onClick={micConsent=false;if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)service("voice_on") else permission.launch(Manifest.permission.RECORD_AUDIO)}){Text("Разрешить на эту прогулку")}},dismissButton={TextButton(onClick={micConsent=false}){Text("Отмена")}})
}

@Composable
fun ConversationPanel(guide: TourController,s: GuideState) {
    var shareLocation by rememberSaveable{mutableStateOf(true)}
    var expanded by rememberSaveable{mutableStateOf(false)}
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Свернуть разговор с гидом" else "Продолжить разговор с гидом")}
        if(expanded || s.questionBusy) {
        Text(s.heardTopics.firstOrNull()?.let{"Последний завершённый рассказ: ${it.title}"}?:"Можно спросить о прогулке и выбранных целях. Прослушанных рассказов ещё нет.")
        OutlinedTextField(s.questionDraft,{guide.editQuestion(it)},label={Text("Вопрос гиду")},modifier=Modifier.fillMaxWidth(),enabled=!s.questionBusy,minLines=2)
        Row{Checkbox(shareLocation,{shareLocation=it});Text("Учесть свежую приблизительную позицию",Modifier.padding(top=12.dp))}
        Text("В GPT отправятся текст вопроса, недавние рассказы и диалог; позиция — только если свежая и разрешена. Запись голоса для hands-free включается отдельно.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={guide.askQuestion(shareLocation=shareLocation)},enabled=s.questionDraft.isNotBlank() && !s.questionBusy && !s.replay){Text("Спросить GPT и озвучить")}
        if(s.questionBusy){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={guide.stopPlayback()}){Text("Отменить ответ")}}
        s.voiceTurns.takeLast(2).forEach { turn->
            Text("Вы: ${turn.question}",style=MaterialTheme.typography.titleSmall)
            Text(turn.answer.take(1500))
            Text(if(turn.heard)"Плеер ответа дошёл до конца" else "Ответ подготовлен; полное воспроизведение не подтверждено",style=MaterialTheme.typography.bodySmall)
        }
        }
    } }
}
