package ru.franh.modemmon.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Шифрование пароля роутера ключом из AndroidKeyStore.
 * Ключ не покидает хранилище системы: в prefs лежит только шифртекст.
 * Используется javax.crypto из платформы — отдельных библиотек не нужно.
 */
object Vault {

    private const val ALIAS = "modemmon_creds"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** Шифртекст в Base64 или null, если шифрование не удалось. */
    fun encrypt(plain: String): String? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.iv + cipher.doFinal(plain.toByteArray())
        Base64.encodeToString(out, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    /** Исходный текст или null, если данные повреждены либо ключ сменился. */
    fun decrypt(stored: String): String? {
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            if (raw.size <= IV_BYTES) {
                null
            } else {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key(),
                    GCMParameterSpec(TAG_BITS, raw.copyOf(IV_BYTES))
                )
                String(cipher.doFinal(raw.copyOfRange(IV_BYTES, raw.size)))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }
}
