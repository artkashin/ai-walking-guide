package com.artkashin.aiwalkingguide

import androidx.test.platform.app.InstrumentationRegistry
import com.artkashin.aiwalkingguide.voice.VoiceCommands
import com.sun.jna.NativeLibrary
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.vosk.LibVosk
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/** Opt-in CI assets only; no user's microphone, credentials or tour data. */
class OfflineRussianModelTest {
    @Test fun nativeRussianModelLoadsAndWakeGrammarRejectsSilence() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val assets=instrumentation.context.assets
        val directory=File(instrumentation.targetContext.noBackupFilesDir,"model-smoke")
        fun copyAsset(name: String,target: File) {
            val children=assets.list(name)?:emptyArray()
            if(children.isEmpty()){target.parentFile!!.mkdirs();assets.open(name).use{input->target.outputStream().use{input.copyTo(it)}}}
            else {target.mkdirs();children.forEach{copyAsset("$name/$it",File(target,it))}}
        }
        try {
            copyAsset("vosk-model-small-ru-0.22",directory)
            LibVosk.vosk_set_log_level(-1)
            Model(directory.path).use { model->
                val vocabulary=NativeLibrary.getInstance("vosk").getFunction("vosk_model_find_word")
                for(word in listOf("окей","гид","привет"))assertTrue("Wake word absent: $word",vocabulary.invokeInt(arrayOf(model.pointer,word))>=0)
                Recognizer(model,16000f,JSONArray(listOf("окей гид","[unk]")).toString()).use { recognizer->
                    repeat(40){recognizer.acceptWaveForm(ShortArray(1600),1600)}
                    assertFalse(VoiceCommands.containsWake(JSONObject(recognizer.finalResult).optString("text"),"окей гид"))
                }
                Recognizer(model,16000f).use { recognizer->
                    recognizer.setWords(true)
                    repeat(20){recognizer.acceptWaveForm(ShortArray(1600),1600)}
                    assertTrue(JSONObject(recognizer.finalResult).optString("text").isBlank())
                }
            }
        } finally {directory.deleteRecursively()}
    }
}
