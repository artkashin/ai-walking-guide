package com.artkashin.aiwalkingguide.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureAccount(context: Context) {
    private val file=File(context.noBackupFilesDir,"chatgpt.enc")
    private val alias="guide.chatgpt.v1"
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun load(): JSONObject? {
        if(!file.exists()) return null
        return try {
            val bytes=file.readBytes();require(bytes.size>12)
            val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            JSONObject(c.doFinal(bytes.copyOfRange(12,bytes.size)).toString(Charsets.UTF_8))
        } catch(_: Exception) { file.delete();null }
    }
    @Synchronized fun save(j: JSONObject) {
        val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key())
        val temp=File(file.parentFile,"chatgpt.new")
        temp.outputStream().use{it.write(c.iv);it.write(c.doFinal(j.toString().toByteArray()));it.flush()}
        check(temp.renameTo(file)) { "Не удалось сохранить защищённый сеанс." }
    }
    @Synchronized fun clear() { file.delete() }
}
