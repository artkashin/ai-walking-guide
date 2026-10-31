package com.artkashin.aiwalkingguide.tour

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.artkashin.aiwalkingguide.GuideApp
import com.artkashin.aiwalkingguide.MainActivity
import com.artkashin.aiwalkingguide.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class GuideService: Service() {
    private val guide get()=(application as GuideApp).guide
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var promoted=false
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("tour","Прогулка с гидом",NotificationManager.IMPORTANCE_LOW))
        scope.launch {
            guide.state.map{Triple(it.voice,it.playback.phase,it.active)}.distinctUntilChanged().collect {
                if(promoted)try{getSystemService(NotificationManager::class.java).notify(42,notification())}catch(_: SecurityException){}
            }
        }
    }
    private fun notification(micStarting: Boolean=false): Notification {
        fun action(name: String,code: Int)=PendingIntent.getService(this,code,Intent(this,GuideService::class.java).setAction(name),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val s=guide.state.value
        val text=if(micStarting)"Запускаем локальный микрофон с уведомлением…" else if(s.voice.enabled)"Микрофон: ${s.voice.phase.label}. Фраза: ${s.voice.phrase}" else "GPS активен. Голосовой микрофон выключен."
        return NotificationCompat.Builder(this,"tour").setSmallIcon(R.drawable.ic_guide)
            .setContentTitle("Гид рядом — прогулка идёт").setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true).addAction(0,"Пауза",action("pause",1)).addAction(0,"Стоп звук",action("stop_audio",2)).addAction(0,"Завершить",action("stop",3)).build()
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        val action=intent?.action.orEmpty()
        if(action=="stop") { guide.stop();stopSelf();return START_NOT_STICKY }
        if(action=="voice_off")guide.stopHandsFree()
        if(action=="pause")guide.pause()
        if(action=="resume")guide.resume()
        if(action=="stop_audio")guide.stopPlayback()
        if(action=="skip")guide.skip()
        if(!guide.state.value.active && action in setOf("voice_off","pause","resume","stop_audio","skip")){stopSelf();return START_NOT_STICKY}
        if(action=="voice_on" && !guide.state.value.active){guide.notice("Начните прогулку перед включением микрофона.");stopSelf();return START_NOT_STICKY}
        val mic=action=="voice_on" || guide.state.value.voice.enabled
        var types=if(Build.VERSION.SDK_INT>=29)ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        if(Build.VERSION.SDK_INT>=30 && mic)types=types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        try {
            // Microphone type is promoted only from the explicit, visible user action.
            ServiceCompat.startForeground(this,42,notification(action=="voice_on"),types)
            promoted=true
            if(!guide.state.value.active)guide.start()
            if(action=="voice_on")guide.startHandsFree()
        } catch(_: Exception) {
            guide.stopHandsFree();guide.notice("Не удалось включить службу. Откройте приложение и проверьте разрешения геолокации/микрофона.");stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onDestroy(){promoted=false;scope.cancel();guide.stop();getSystemService(NotificationManager::class.java).cancel(42);super.onDestroy()}
}
