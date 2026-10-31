package com.artkashin.aiwalkingguide.tour

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.artkashin.aiwalkingguide.GuideApp
import com.artkashin.aiwalkingguide.MainActivity
import com.artkashin.aiwalkingguide.R

class GuideService: Service() {
    private val guide get()=(application as GuideApp).guide
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action=="stop") { guide.stop();stopSelf();return START_NOT_STICKY }
        if(intent?.action=="skip") guide.skip()
        if(intent?.action=="pause") guide.pause()
        if(intent?.action=="stop_audio") guide.stopPlayback()
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("tour","Прогулка с гидом",NotificationManager.IMPORTANCE_LOW))
        fun action(name: String,code: Int)=PendingIntent.getService(this,code,Intent(this,GuideService::class.java).setAction(name),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(this,"tour").setSmallIcon(R.drawable.ic_guide)
            .setContentTitle("Гид рядом — прогулка идёт")
            .setContentText("GPS используется для поиска мест рядом. Нажмите, чтобы открыть гид.")
            .setContentIntent(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true).addAction(0,"Пауза",action("pause",1)).addAction(0,"Стоп звук",action("stop_audio",2)).addAction(0,"Завершить",action("stop",3)).build()
        try {
            ServiceCompat.startForeground(this,42,notification,if(android.os.Build.VERSION.SDK_INT>=29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
            if(!guide.state.value.active) guide.start()
        } catch(_: Exception) { guide.notice("Не удалось запустить прогулку. Проверьте разрешение геолокации.");stopSelf() }
        return START_NOT_STICKY
    }
    override fun onDestroy() { guide.stop();super.onDestroy() }
}
