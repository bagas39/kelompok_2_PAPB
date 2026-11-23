package com.example.cloble.data.local.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionTokenCipher internal constructor(
    private val keyProvider: () -> SecretKey?
) {
    constructor() : this({ getOrCreateKeystoreSecretKey() })

    @Volatile
    private var cachedKey: SecretKey? = null
    private val instanceKeyLock = Any()

    private fun resolveSecretKey(): SecretKey? {
        cachedKey?.let { return it }
        return synchronized(instanceKeyLock) {
            cachedKey?.let { return@synchronized it }
            keyProvider()?.also { cachedKey = it }
        }
    }

    private fun invalidateCachedKey() {
        synchronized(instanceKeyLock) {
            cachedKey = null
        }
        synchronized(keyLock) {
            cachedKeystoreKey = null
        }
    }

    private fun encryptWithKey(plainToken: String, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        if (iv == null || iv.size != GCM_IV_LENGTH_BYTES) {
            throw IllegalStateException("Invalid GCM IV generated")
        }
        val encrypted = cipher.doFinal(plainToken.toByteArray(StandardCharsets.UTF_8))
        return "$PREFIX${encodeBase64(iv)}:${encodeBase64(encrypted)}"
    }

    fun encrypt(plainToken: String): String {
        if (plainToken.isBlank()) return ""
        val secretKey = resolveSecretKey()
            ?: throw IllegalStateException("Session encryption key unavailable")
        return try {
            encryptWithKey(plainToken, secretKey)
        } catch (e: Exception) {
            invalidateCachedKey()
            val refreshedKey = resolveSecretKey()
                ?: throw IllegalStateException("Failed to encrypt session token", e)
            try {
                encryptWithKey(plainToken, refreshedKey)
            } catch (retryError: Exception) {
                invalidateCachedKey()
                throw IllegalStateException("Failed to encrypt session token", retryError)
            }
        }
    }

    fun decrypt(storedValue: String?): String? {
        if (storedValue.isNullOrBlank()) return null
        if (!storedValue.startsWith(PREFIX)) {
            return storedValue
        }
        val payload = storedValue.removePrefix(PREFIX)
        val parts = payload.split(':')
        if (parts.size != 2) return null
        val iv = decodeBase64(parts[0]) ?: return null
        val ciphertext = decodeBase64(parts[1]) ?: return null
        if (iv.size != GCM_IV_LENGTH_BYTES || ciphertext.isEmpty()) return null
        val secretKey = resolveSecretKey() ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val decrypted = cipher.doFinal(ciphertext)
            String(decrypted, StandardCharsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    internal companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "cloble_session_token_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFIX = "enc_v1:"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val BASE64_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

        @Volatile
        private var cachedKeystoreKey: SecretKey? = null
        private val keyLock = Any()

        private val BASE64_DECODE_TABLE = IntArray(256) { -1 }.apply {
            for (i in BASE64_ALPHABET.indices) {
                this[BASE64_ALPHABET[i].code] = i
            }
        }

        private fun getOrCreateKeystoreSecretKey(): SecretKey? {
            cachedKeystoreKey?.let { return it }
            return synchronized(keyLock) {
                cachedKeystoreKey?.let { return@synchronized it }
                try {
                    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                    val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                    if (existing != null) {
                        cachedKeystoreKey = existing
                        return@synchronized existing
                    }
                    val keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        ANDROID_KEYSTORE
                    )
                    val spec = KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                    keyGenerator.init(spec)
                    keyGenerator.generateKey().also { cachedKeystoreKey = it }
                } catch (_: Exception) {
                    null
                }
            }
        }

        internal fun encodeBase64(data: ByteArray): String {
            if (data.isEmpty()) return ""
            val out = StringBuilder(((data.size + 2) / 3) * 4)
            var i = 0
            while (i < data.size) {
                val b0 = data[i++].toInt() and 0xFF
                val hasB1 = i < data.size
                val b1 = if (hasB1) data[i++].toInt() and 0xFF else 0
                val hasB2 = i < data.size
                val b2 = if (hasB2) data[i++].toInt() and 0xFF else 0

                out.append(BASE64_ALPHABET[b0 ushr 2])
                out.append(BASE64_ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
                out.append(if (hasB1) BASE64_ALPHABET[((b1 and 0x0F) shl 2) or (b2 ushr 6)] else '=')
                out.append(if (hasB2) BASE64_ALPHABET[b2 and 0x3F] else '=')
            }
            return out.toString()
        }

        internal fun decodeBase64(input: String): ByteArray? {
            val clean = input.trim()
            if (clean.isEmpty()) return ByteArray(0)
            if (clean.length % 4 != 0) return null

            var padding = 0
            if (clean.endsWith("==")) {
                padding = 2
            } else if (clean.endsWith("=")) {
                padding = 1
            }

            val outputLen = (clean.length / 4) * 3 - padding
            val result = ByteArray(outputLen)
            var outIdx = 0
            var i = 0
            while (i < clean.length) {
                val c0 = decodeChar(clean[i++]) ?: return null
                val c1 = decodeChar(clean[i++]) ?: return null
                val ch2 = clean[i++]
                val ch3 = clean[i++]
                val c2 = if (ch2 == '=') 0 else decodeChar(ch2) ?: return null
                val c3 = if (ch3 == '=') 0 else decodeChar(ch3) ?: return null
                if (ch2 == '=' && ch3 != '=') return null

                val triple = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
                if (outIdx < outputLen) result[outIdx++] = ((triple ushr 16) and 0xFF).toByte()
                if (outIdx < outputLen) result[outIdx++] = ((triple ushr 8) and 0xFF).toByte()
                if (outIdx < outputLen) result[outIdx++] = (triple and 0xFF).toByte()
            }
            return result
        }

        private fun decodeChar(ch: Char): Int? {
            val code = ch.code
            if (code !in 0..255) return null
            val v = BASE64_DECODE_TABLE[code]
            return if (v >= 0) v else null
        }
    }
}
