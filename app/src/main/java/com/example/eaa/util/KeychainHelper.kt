package com.example.eaa.util

import android.content.Context
import android.security.keystore.KeyProperties
import android.security.keystore.KeyGenParameterSpec
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

/**
 * Simple wrapper around Android Keystore for storing the ElevenLabs API key.
 * The key is encrypted with a generated AES‑GCM secret stored in the system keystore.
 */
object KeychainHelper {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "ElevenLabsApiKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    fun set(context: Context, apiKey: String) {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        val encoded = Base64.encodeToString(combined, Base64.DEFAULT)
        context.getSharedPreferences("eaa_prefs", Context.MODE_PRIVATE).edit().putString("api_key", encoded).apply()
    }

    fun get(context: Context): String? {
        val encoded = context.getSharedPreferences("eaa_prefs", Context.MODE_PRIVATE).getString("api_key", null) ?: return null
        val combined = Base64.decode(encoded, Base64.DEFAULT)
        val iv = combined.sliceArray(0 until 12) // GCM IV is 12 bytes
        val encrypted = combined.sliceArray(12 until combined.size)
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }
}
