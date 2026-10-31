package com.artkashin.aiwalkingguide.audio

import java.io.File

data class PlaybackCallbacks(val state: (PlaybackState)->Unit,val complete: (String)->Unit,val error: (String)->Unit)
interface NarrationAudio {
    suspend fun prepare(text: String): List<File>
    fun play(files: List<File>,id: String,title: String)
    fun pause()
    fun resume()
    fun seekTo(position: Long)
    fun stop(clear: Boolean=false)
    fun clearCache()
    fun release()
}
