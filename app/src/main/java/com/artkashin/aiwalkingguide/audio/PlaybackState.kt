package com.artkashin.aiwalkingguide.audio

enum class PlaybackPhase(val label: String) {
    EMPTY("Выберите рассказ"), GENERATING("Готовим рассказ"), BUFFERING("Подготовка звука"),
    PLAYING("Воспроизводится"), PAUSED("Пауза"), STOPPED("Остановлено"), COMPLETED("Завершено"), ERROR("Ошибка звука")
}

data class PlaybackState(val id: String="",val title: String="",val phase: PlaybackPhase=PlaybackPhase.EMPTY,
    val positionMs: Long=0,val durationMs: Long=0,val listened: Boolean=false) {
    val canSeek get()=durationMs>0 && phase !in setOf(PlaybackPhase.EMPTY,PlaybackPhase.GENERATING)
}

object PlaybackTimeline {
    fun locate(durations: List<Long>,position: Long): Pair<Int,Long> {
        require(durations.isNotEmpty() && durations.all{it>=0})
        var left=position.coerceIn(0,durations.sum())
        for(i in durations.indices) {
            if(left<durations[i] || i==durations.lastIndex)return i to left.coerceAtMost(durations[i])
            left-=durations[i]
        }
        return 0 to 0L
    }
    fun time(ms: Long): String {
        val seconds=ms.coerceAtLeast(0)/1000
        return if(seconds>=3600)"${seconds/3600}:${(seconds/60%60).toString().padStart(2,'0')}:${(seconds%60).toString().padStart(2,'0')}"
        else "${seconds/60}:${(seconds%60).toString().padStart(2,'0')}"
    }
}
