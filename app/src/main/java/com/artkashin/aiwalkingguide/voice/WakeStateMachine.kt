package com.artkashin.aiwalkingguide.voice

enum class VoicePhase(val label: String) {
    OFF("Микрофон выключен"), LOADING("Загружаем локальную модель"), WAIT_WAKE("Слушаю фразу пробуждения локально"),
    CAPTURING("Слушаю команду"), RESPONDING("Готовим ответ"), OUTPUT_MUTED("Микрофон выключен на время звука или звонка"),
    COOLDOWN("Пауза после звука"), ERROR("Голосовой режим остановлен")
}
data class VoiceState(val enabled: Boolean=false,val phase: VoicePhase=VoicePhase.OFF,val phrase: String="окей гид",val error: String="")

/** No navigation or network policy lives in the wake layer. */
class WakeStateMachine(private val cooldownMs: Long=2000,private val commandTimeoutMs: Long=15000) {
    var phase=VoicePhase.OFF;private set
    private var active=false;private var output=false;private var until=0L;private var captureUntil=0L
    fun start(now: Long) { active=true;phase=if(output)VoicePhase.OUTPUT_MUTED else VoicePhase.COOLDOWN;until=now+cooldownMs }
    fun stop() { active=false;phase=VoicePhase.OFF }
    fun setOutput(busy: Boolean,now: Long) {
        if(output==busy)return
        output=busy
        if(!active)return
        if(busy)phase=VoicePhase.OUTPUT_MUTED else { phase=VoicePhase.COOLDOWN;until=now+cooldownMs }
    }
    fun tick(now: Long): VoicePhase {
        if(active && !output && phase==VoicePhase.COOLDOWN && now>=until)phase=VoicePhase.WAIT_WAKE
        if(active && phase==VoicePhase.CAPTURING && now>=captureUntil){phase=VoicePhase.COOLDOWN;until=now+cooldownMs}
        return phase
    }
    fun wake(now: Long): Boolean {
        tick(now)
        if(!active || output || phase!=VoicePhase.WAIT_WAKE)return false
        phase=VoicePhase.CAPTURING;captureUntil=now+commandTimeoutMs;return true
    }
    fun command(text: String,now: Long): String? {
        tick(now)
        if(phase!=VoicePhase.CAPTURING || text.isBlank())return null
        phase=VoicePhase.RESPONDING;return text.trim().take(2000)
    }
    fun responseDone(now: Long) {
        if(!active)return
        phase=if(output)VoicePhase.OUTPUT_MUTED else VoicePhase.COOLDOWN;until=now+cooldownMs
    }
    fun beginResponse() { if(active)phase=VoicePhase.RESPONDING }
}

class PcmRing(private val capacity: Int=32000) {
    private val data=ShortArray(capacity);private var cursor=0;private var count=0
    fun add(input: ShortArray,n: Int) { for(i in 0 until n){data[cursor]=input[i];cursor=(cursor+1)%capacity;count=minOf(count+1,capacity)} }
    fun snapshot()=ShortArray(count){data[(cursor-count+it+capacity)%capacity]}
    fun clear(){data.fill(0);cursor=0;count=0}
}

enum class LocalVoiceCommand { PAUSE, STOP_AUDIO, RESUME, END_TOUR, QUESTION }
object VoiceCommands {
    fun normalize(text: String)=text.lowercase(java.util.Locale.ROOT).replace('ё','е').replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
    fun classify(text: String): LocalVoiceCommand = when(normalize(text)) {
        "подожди","пауза","помолчи","поставь на паузу","приостанови рассказ"->LocalVoiceCommand.PAUSE
        "стоп","хватит","останови рассказ","останови звук","замолчи"->LocalVoiceCommand.STOP_AUDIO
        "продолжи","продолжай","возобнови","продолжи рассказ","продолжай рассказ"->LocalVoiceCommand.RESUME
        "заверши прогулку","закончить прогулку","закончи прогулку"->LocalVoiceCommand.END_TOUR
        else->LocalVoiceCommand.QUESTION
    }
    fun stripWake(text: String,phrase: String): String {
        val normalized=normalize(text);val wake=normalize(phrase)
        val index=normalized.indexOf(wake)
        return if(index>=0)normalized.substring(index+wake.length).trim() else normalized
    }
    fun containsWake(text: String,phrase: String): Boolean = (" ${normalize(text)} ").contains(" ${normalize(phrase)} ")
}
