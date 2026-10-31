package com.artkashin.aiwalkingguide.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.media.MediaMetadataRetriever
import androidx.media3.session.MediaSession
import androidx.media3.common.MediaMetadata
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

class RussianSpeaker(private val context: Context, private val onState: (PlaybackState)->Unit, private val onComplete: (String)->Unit,private val onError: (String)->Unit): NarrationAudio {
    private val ready=CompletableDeferred<Unit>()
    private val jobs=ConcurrentHashMap<String,CompletableDeferred<Unit>>()
    private val directory=File(context.cacheDir,"speech").apply{mkdirs()}
    private val tts=TextToSpeech(context.applicationContext) { status ->
        if(status==TextToSpeech.SUCCESS) ready.complete(Unit) else ready.completeExceptionally(ServiceFailure("Синтез речи недоступен. Установите русский голос в настройках телефона."))
    }
    private var itemId=""
    private var title=""
    private var explicitlyStopped=false
    private var endedReported=false
    private var durations=emptyList<Long>()
    private val fileDurations=ConcurrentHashMap<String,Long>()
    private val handler=Handler(Looper.getMainLooper())
    private val player=ExoPlayer.Builder(context.applicationContext).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).setUsage(C.USAGE_MEDIA).build(),true)
        setHandleAudioBecomingNoisy(true)
        addListener(object: Player.Listener {
            override fun onEvents(player: Player,events: Player.Events) { publish() }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { publish();onError(itemId) }
        })
    }
    private val mediaSession=MediaSession.Builder(context.applicationContext,player).setId("guide-${java.util.UUID.randomUUID()}").build()
    private val ticker=object: Runnable { override fun run() { publish();handler.postDelayed(this,250) } }
    fun snapshot(): PlaybackState {
        val current=player.currentMediaItemIndex
        if(current in durations.indices && player.duration>0 && durations[current]<=0)durations=durations.toMutableList().also{it[current]=player.duration}
        val total=durations.sum()
        val position=(durations.take(current.coerceAtLeast(0)).sum()+player.currentPosition.coerceAtLeast(0)).coerceIn(0,total.coerceAtLeast(0))
        val phase=when {
            itemId.isEmpty()->PlaybackPhase.EMPTY
            player.playerError!=null->PlaybackPhase.ERROR
            explicitlyStopped->PlaybackPhase.STOPPED
            player.playbackState==Player.STATE_IDLE->PlaybackPhase.STOPPED
            player.playbackState==Player.STATE_ENDED->PlaybackPhase.COMPLETED
            player.playbackState==Player.STATE_BUFFERING->PlaybackPhase.BUFFERING
            player.isPlaying->PlaybackPhase.PLAYING
            else->PlaybackPhase.PAUSED
        }
        return PlaybackState(itemId,title,phase,position,total)
    }
    private fun publish() {
        val s=snapshot();onState(s)
        if(s.phase==PlaybackPhase.COMPLETED && !endedReported) { endedReported=true;onComplete(s.id) }
    }
    init {
        handler.post(ticker)
        tts.setOnUtteranceProgressListener(object: UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) { jobs.remove(id)?.complete(Unit) }
            @Deprecated("Android compatibility") override fun onError(id: String?) { jobs.remove(id)?.completeExceptionally(ServiceFailure("Не удалось создать звук.")) }
            override fun onStop(id: String?,interrupted: Boolean) { jobs.remove(id)?.cancel() }
        })
    }
    override suspend fun prepare(text: String): List<File> = withContext(Dispatchers.Main) {
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
        withContext(Dispatchers.IO) { files.forEach{fileDurations[it.path]=duration(it)} }
        files
    }
    private fun duration(file: File): Long {
        val reader=MediaMetadataRetriever()
        return try { reader.setDataSource(file.path);reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0L }
        catch(_: Exception){0L} finally { reader.release() }
    }
    override fun play(files: List<File>,id: String,title: String) {
        require(files.isNotEmpty())
        itemId=id;this.title=title;explicitlyStopped=false;endedReported=false
        durations=files.map{fileDurations[it.path]?:duration(it)}
        player.setMediaItems(files.map { MediaItem.Builder().setUri(Uri.fromFile(it)).setMediaId(id).setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()).build() })
        player.prepare();player.play();publish()
    }
    override fun pause() { player.pause();publish() }
    override fun resume() {
        if(player.mediaItemCount==0)return
        explicitlyStopped=false
        if(player.playbackState==Player.STATE_ENDED){player.seekToDefaultPosition(0);endedReported=false}
        if(player.playbackState==Player.STATE_IDLE)player.prepare()
        player.play();publish()
    }
    override fun seekTo(position: Long) {
        if(durations.isEmpty() || durations.sum()<=0)return
        val playing=player.playWhenReady && player.playbackState!=Player.STATE_ENDED && !explicitlyStopped
        val (index,offset)=PlaybackTimeline.locate(durations,position)
        explicitlyStopped=false;endedReported=false
        player.seekTo(index,offset)
        if(player.playbackState==Player.STATE_IDLE)player.prepare()
        player.playWhenReady=playing;publish()
    }
    override fun stop(clear: Boolean) {
        tts.stop();jobs.values.forEach{it.cancel()};jobs.clear()
        explicitlyStopped=true;player.pause();player.stop()
        if(player.mediaItemCount>0)player.seekToDefaultPosition(0)
        if(clear){itemId="";title="";durations=emptyList();player.clearMediaItems()}
        publish()
    }
    override fun clearCache() { stop(true);directory.listFiles()?.forEach{it.delete()} }
    override fun release() { handler.removeCallbacks(ticker);stop(true);tts.shutdown();mediaSession.release();player.release() }
}
