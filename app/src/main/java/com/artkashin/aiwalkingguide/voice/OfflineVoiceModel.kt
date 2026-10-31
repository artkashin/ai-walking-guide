package com.artkashin.aiwalkingguide.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

class OfflineVoiceModel(context: Context) {
    private val directory=File(context.noBackupFilesDir,"voice-model")
    val path: File get()=File(directory,"vosk-model-small-ru-0.22")
    fun ready()=File(directory,"verified.sha256").readTextOrNull()==SHA && File(path,"am/final.mdl").isFile && File(path,"graph/HCLr.fst").isFile
    suspend fun install(onProgress: (Int)->Unit) = withContext(Dispatchers.IO) {
        if(ready())return@withContext
        val staging=File(directory.parentFile,"voice-model.staging");val zip=File(directory.parentFile,"voice-model.zip.part")
        staging.deleteRecursively();staging.mkdirs()
        try {
            val connection=URL(URL_STRING).openConnection() as HttpsURLConnection
            connection.connectTimeout=15000;connection.readTimeout=30000;connection.instanceFollowRedirects=false
            try {
                check(connection.responseCode==200){"Модель недоступна: HTTP ${connection.responseCode}"}
                val digest=MessageDigest.getInstance("SHA-256");var count=0L;val expected=connection.contentLengthLong
                connection.inputStream.use { input->zip.outputStream().use { output ->
                    val buffer=ByteArray(32768)
                    while(true){coroutineContext.ensureActive();val n=input.read(buffer);if(n<0)break;count+=n;check(count<=70L*1024*1024);digest.update(buffer,0,n);output.write(buffer,0,n);if(expected>0)onProgress((count*100/expected).toInt().coerceIn(0,99))}
                } }
                check(digest.digest().joinToString(""){"%02x".format(it)}==SHA){"Контрольная сумма модели не совпала"}
            } finally { connection.disconnect() }
            var total=0L;var entries=0
            ZipInputStream(zip.inputStream()).use { input ->
                while(true) {
                    coroutineContext.ensureActive();val entry=input.nextEntry?:break;entries++;check(entries<=100)
                    val target=File(staging,entry.name)
                    check(target.canonicalPath.startsWith(staging.canonicalPath+File.separator))
                    if(entry.isDirectory)target.mkdirs() else {
                        target.parentFile?.mkdirs();target.outputStream().use { output ->
                            val buffer=ByteArray(32768)
                            while(true){coroutineContext.ensureActive();val n=input.read(buffer);if(n<0)break;total+=n;check(total<=130L*1024*1024);output.write(buffer,0,n)}
                        }
                    }
                }
            }
            check(File(staging,"vosk-model-small-ru-0.22/am/final.mdl").isFile)
            File(staging,"verified.sha256").writeText(SHA)
            directory.deleteRecursively();check(staging.renameTo(directory));onProgress(100)
        } finally { zip.delete();staging.deleteRecursively() }
    }
    private fun File.readTextOrNull()=try{if(isFile)readText() else null}catch(_: Exception){null}
    companion object {
        const val URL_STRING="https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
        const val SHA="961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4"
    }
}
