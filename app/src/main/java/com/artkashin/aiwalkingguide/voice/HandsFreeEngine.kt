package com.artkashin.aiwalkingguide.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.sun.jna.NativeLibrary
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.Executors

/** Session-only, local grammar wake detector and local command ASR. No audio file/network output. */
class HandsFreeEngine(private val context: Context,private val onState: (VoicePhase)->Unit,
    private val onWake: ()->Unit,private val onCommand: (String)->Unit,private val onFailure: (String)->Unit) {
    private val executor=Executors.newSingleThreadExecutor()
    private val handler=Handler(Looper.getMainLooper())
    private val machine=WakeStateMachine()
    private val gate=Any();private val recorderLock=Any()
    @Volatile private var generation=0L
    @Volatile private var recorder: AudioRecord?=null
    @Volatile private var outputBusy=false
    val handlingCommand: Boolean get()=synchronized(gate){machine.phase in setOf(VoicePhase.CAPTURING,VoicePhase.RESPONDING)}
    fun setOutputBusy(busy: Boolean) {
        outputBusy=busy
        if(busy)synchronized(recorderLock){try{recorder?.stop()}catch(_: Exception){}}
    }
    fun beginResponse() {
        synchronized(gate){machine.beginResponse()}
        synchronized(recorderLock){try{recorder?.stop()}catch(_: Exception){}}
    }
    fun responseDone(){synchronized(gate){machine.responseDone(SystemClock.elapsedRealtime())}}
    fun start(path: File,phrase: String) {
        stop();val token=++generation
        onState(VoicePhase.LOADING)
        executor.submit{run(token,path,VoiceCommands.normalize(phrase))}
    }
    fun stop() {
        generation++;synchronized(gate){machine.stop()}
        synchronized(recorderLock){try{recorder?.stop()}catch(_: Exception){}}
    }
    fun release(){stop();executor.shutdownNow()}
    private fun run(token: Long,path: File,phrase: String) {
        var model: Model?=null;var wake: Recognizer?=null;var command: Recognizer?=null
        var deviceCallback: AudioDeviceCallback?=null
        val audio=context.getSystemService(AudioManager::class.java)
        var mic: AudioRecord?=null;val ring=PcmRing();var lastPhase=VoicePhase.OFF;var commandMode=false
        fun post(block: ()->Unit){handler.post{if(token==generation)block()}}
        fun closeMic(){synchronized(recorderLock){if(recorder===mic)recorder=null;try{mic?.stop()}catch(_: Exception){};mic?.release();mic=null};ring.clear()}
        try {
            LibVosk.vosk_set_log_level(-1)
            model=Model(path.path)
            val vocabulary=NativeLibrary.getInstance("vosk").getFunction("vosk_model_find_word")
            check(phrase.split(' ').all{vocabulary.invokeInt(arrayOf(model.pointer,it))>=0}){"PHRASE_VOCABULARY"}
            wake=Recognizer(model,16000f,JSONArray(listOf(phrase,"[unk]")).toString())
            command=Recognizer(model,16000f);command.setWords(true)
            if(token!=generation)return
            synchronized(gate){machine.start(SystemClock.elapsedRealtime())}
            deviceCallback=object: AudioDeviceCallback() {
                override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
                    if(token==generation && removed.any{it.isSource}) {
                        stop();onState(VoicePhase.ERROR)
                        onFailure("Микрофон или гарнитура отключены. Hands-free остановлен; включите его заново после проверки аудиоустройства.")
                    }
                }
            }
            audio.registerAudioDeviceCallback(deviceCallback,handler)
            val frame=ShortArray(1600)
            while(token==generation) {
                if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw SecurityException()
                val now=SystemClock.elapsedRealtime()
                val phase=synchronized(gate){machine.setOutput(outputBusy || audio.mode!=AudioManager.MODE_NORMAL,now);machine.tick(now)}
                if(phase!=lastPhase){lastPhase=phase;post{onState(phase)}}
                if(phase !in setOf(VoicePhase.WAIT_WAKE,VoicePhase.CAPTURING)) {
                    if(mic!=null){closeMic();wake.reset();command.reset();commandMode=false}
                    Thread.sleep(40);continue
                }
                if(mic==null) {
                    val min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
                    check(min>0)
                    synchronized(recorderLock) {
                        if(token!=generation)return
                        mic=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(min*4,64000))
                        check(mic!!.state==AudioRecord.STATE_INITIALIZED)
                        recorder=mic;mic!!.startRecording()
                    }
                }
                val count=mic!!.read(frame,0,frame.size,AudioRecord.READ_NON_BLOCKING)
                if(count<0) {
                    val muted=outputBusy || audio.mode!=AudioManager.MODE_NORMAL || token!=generation
                    closeMic();if(muted)continue else error("MIC_READ")
                }
                if(count==0){Thread.sleep(20);continue}
                if(phase==VoicePhase.WAIT_WAKE) {
                    ring.add(frame,count)
                    val final=wake.acceptWaveForm(frame,count)
                    val text=JSONObject(if(final)wake.result else wake.partialResult).optString(if(final)"text" else "partial")
                    if(VoiceCommands.containsWake(text,phrase) && synchronized(gate){machine.wake(now)}) {
                        command.reset();val prior=ring.snapshot();command.acceptWaveForm(prior,prior.size);ring.clear();commandMode=true
                        post{onState(VoicePhase.CAPTURING);onWake()}
                    }
                } else if(commandMode && command.acceptWaveForm(frame,count)) {
                    val result=JSONObject(command.result)
                    val text=VoiceCommands.stripWake(result.optString("text"),phrase)
                    if(text.isBlank()){command.reset();continue}
                    val words=result.optJSONArray("result")
                    val confidence=if(words!=null && words.length()>0)(0 until words.length()).map{words.getJSONObject(it).optDouble("conf",0.0)}.average() else 0.0
                    if(confidence<0.45){command.reset();continue}
                    val accepted=synchronized(gate){machine.command(text,now)}
                    if(accepted!=null){closeMic();commandMode=false;post{onState(VoicePhase.RESPONDING);onCommand(accepted)}}
                }
            }
        } catch(_: InterruptedException) { }
        catch(e: LinkageError) { synchronized(gate){machine.stop()};post{onState(VoicePhase.ERROR);onFailure("Локальный голосовой движок несовместим с этим устройством. Микрофон выключен.")} }
        catch(e: Exception) {
            synchronized(gate){machine.stop()}
            val message=when {
                e is SecurityException->"Разрешение микрофона отозвано. Голосовой режим выключен."
                e.message=="PHRASE_VOCABULARY"->"Слова фразы отсутствуют в словаре модели. Попробуйте «окей гид» или «привет гид»."
                else->"Не удалось запустить локальный микрофон или модель. Голосовой режим выключен; используйте плеер и текстовый вопрос."
            }
            post{onState(VoicePhase.ERROR);onFailure(message)}
        } finally { deviceCallback?.let{audio.unregisterAudioDeviceCallback(it)};closeMic();wake?.close();command?.close();model?.close() }
    }
}
