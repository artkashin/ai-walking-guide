package com.artkashin.aiwalkingguide.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.artkashin.aiwalkingguide.tour.SpeechChunks
import com.artkashin.aiwalkingguide.net.ServiceFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class RussianSpeaker(private val context: Context, private val onPlayback: (Boolean,Boolean)->Unit, private val onComplete: ()->Unit,private val onError: ()->Unit) {
    private val ready=CompletableDeferred<Unit>()
    private val jobs=ConcurrentHashMap<String,CompletableDeferred<Unit>>()
    private val directory=File(context.cacheDir,"speech").apply{mkdirs()}
    private val tts=TextToSpeech(context.applicationContext) { status ->
        if(status==TextToSpeech.SUCCESS) ready.complete(Unit) else ready.completeExceptionally(ServiceFailure("Синтез речи недоступен. Установите русский голос в настройках телефона."))
    }
    private val player=ExoPlayer.Builder(context.applicationContext).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).setUsage(C.USAGE_MEDIA).build(),true)
        setHandleAudioBecomingNoisy(true)
        addListener(object: Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { onPlayback(isPlaying,mediaItemCount>0 && playbackState!=Player.STATE_ENDED) }
            override fun onPlaybackStateChanged(playbackState: Int) { if(playbackState==Player.STATE_ENDED) { onPlayback(false,false);onComplete() } }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { onError() }
        })
    }
    init {
        tts.setOnUtteranceProgressListener(object: UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) { jobs.remove(id)?.complete(Unit) }
            @Deprecated("Android compatibility") override fun onError(id: String?) { jobs.remove(id)?.completeExceptionally(ServiceFailure("Не удалось создать звук.")) }
            override fun onStop(id: String?,interrupted: Boolean) { jobs.remove(id)?.cancel() }
        })
    }
    suspend fun prepare(text: String): List<File> = withContext(Dispatchers.Main) {
        withTimeout(15000) { ready.await() }
        val voice=tts.voices?.filter { it.locale.language=="ru" && !it.isNetworkConnectionRequired }?.maxByOrNull{it.quality}
            ?:throw ServiceFailure("Нет установленного офлайн-голоса для русского языка. Откройте настройки синтеза речи и установите его.")
        tts.language=Locale.forLanguageTag("ru-RU");tts.voice=voice;tts.setSpeechRate(1f);tts.setPitch(1f)
        if(tts.voice?.isNetworkConnectionRequired!=false)throw ServiceFailure("Выбранный голос требует сети. Установите офлайн-голос.")
        val engineVersion=try { context.packageManager.getPackageInfo(tts.defaultEngine,0).versionCode } catch(_: Exception) { 0 }
        val chunks=SpeechChunks.split(text,minOf(1800,TextToSpeech.getMaxSpeechInputLength()-1))
        val files=mutableListOf<File>()
        for(chunk in chunks) {
            val key=MessageDigest.getInstance("SHA-256").digest("${tts.defaultEngine}|$engineVersion|${voice.name}|$chunk|rate1-pitch1-v1".toByteArray()).joinToString(""){"%02x".format(it)}
            val file=File(directory,"$key.wav")
            if(!file.exists() || file.length()<64) {
                val temp=File(directory,"$key.part");val done=CompletableDeferred<Unit>();jobs[key]=done
                try {
                    if(tts.synthesizeToFile(chunk,Bundle(),temp,key)!=TextToSpeech.SUCCESS) throw ServiceFailure("Синтез речи не запустился.")
                    withTimeout(90000){done.await()}
                    check(temp.length()>=64 && temp.renameTo(file))
                } finally { jobs.remove(key);temp.delete() }
            }
            file.setLastModified(System.currentTimeMillis());files.add(file)
        }
        // Retain at most 100 MiB, never evict files prepared for the current playlist.
        var total=directory.listFiles()?.sumOf{it.length()}?:0
        directory.listFiles()?.filter{it !in files}?.sortedBy{it.lastModified()}?.forEach {
            if(total>100L*1024*1024) { val size=it.length();if(it.delete())total-=size }
        }
        files
    }
    fun play(files: List<File>) {
        player.setMediaItems(files.map{MediaItem.fromUri(Uri.fromFile(it))});player.prepare();player.play()
    }
    fun pause()=player.pause()
    fun resume()=player.play()
    fun stop() { tts.stop();jobs.values.forEach{it.cancel()};jobs.clear();player.stop();player.clearMediaItems() }
    fun clearCache() { stop();directory.listFiles()?.forEach{it.delete()} }
    fun release() { stop();tts.shutdown();player.release() }
}
