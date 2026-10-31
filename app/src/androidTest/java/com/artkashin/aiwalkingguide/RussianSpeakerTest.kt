package com.artkashin.aiwalkingguide

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.artkashin.aiwalkingguide.audio.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

class RussianSpeakerTest {
    @Test fun actualMedia3ProgressPauseCrossChunkSeekStopAndReplay() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val activity=ActivityScenario.launch(MainActivity::class.java)
        val files=listOf(wav(File(context.cacheDir,"player-fixture-1.wav"),2),wav(File(context.cacheDir,"player-fixture-2.wav"),3))
        val snapshot=AtomicReference(PlaybackState());lateinit var speaker: RussianSpeaker
        fun waitFor(predicate: (PlaybackState)->Boolean) {
            val end=System.nanoTime()+15_000_000_000
            while(System.nanoTime()<end){if(predicate(snapshot.get()))return;Thread.sleep(40)}
            fail("Playback state not reached: ${snapshot.get()}")
        }
        try {
            instrumentation.runOnMainSync {
                speaker=RussianSpeaker(context,{snapshot.set(it)},{},{})
                speaker.play(files,"fixture-player","Тест звука")
            }
            waitFor{it.phase==PlaybackPhase.PLAYING && it.durationMs in 4900..5100 && it.positionMs>100}
            instrumentation.runOnMainSync{speaker.pause();speaker.seekTo(3500)}
            waitFor{it.phase==PlaybackPhase.PAUSED && it.positionMs in 3400..3600}
            instrumentation.runOnMainSync{speaker.seekTo(500)}
            waitFor{it.phase==PlaybackPhase.PAUSED && it.positionMs in 400..600}
            instrumentation.runOnMainSync{speaker.resume()}
            waitFor{it.phase==PlaybackPhase.PLAYING && it.positionMs>650}
            instrumentation.runOnMainSync{speaker.stop()}
            waitFor{it.phase==PlaybackPhase.STOPPED && it.positionMs==0L}
            instrumentation.runOnMainSync{speaker.resume()}
            waitFor{it.phase==PlaybackPhase.PLAYING}
            instrumentation.runOnMainSync{speaker.seekTo(5000)}
            waitFor{it.phase==PlaybackPhase.COMPLETED}
            instrumentation.runOnMainSync{speaker.resume()}
            waitFor{it.phase==PlaybackPhase.PLAYING && it.positionMs<1000}
        } finally { instrumentation.runOnMainSync{speaker.release()};activity.close();files.forEach{it.delete()} }
    }
    private fun wav(file: File,seconds: Int): File {
        val bytes=16000*2*seconds
        val header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(bytes+36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(bytes).array()
        file.outputStream().use{it.write(header);it.write(ByteArray(bytes))};return file
    }
}
