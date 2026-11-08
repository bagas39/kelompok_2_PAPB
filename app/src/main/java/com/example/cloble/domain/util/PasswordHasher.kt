package com.example.cloble.domain.util

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

object PasswordHasher {
    const val DEFAULT_ITERATIONS = 120_000
    private const val MIN_ACCEPTED_ITERATIONS = 10_000
    private const val MAX_ACCEPTED_ITERATIONS = 5_000_000

    private const val SALT_LENGTH_BYTES = 16
    private const val DEFAULT_DK_LEN_BYTES = 32
    private const val HMAC_SHA256_BLOCK_SIZE = 64
    private const val HMAC_SHA256_DIGEST_LENGTH = 32
    private const val ALGORITHM_NAME = "pbkdf2_hmac_sha256"
    private const val JCA_MAC_ALGORITHM = "HmacSHA256"

    private val secureRandom = SecureRandom()

    fun hash(password: String, iterations: Int = DEFAULT_ITERATIONS): String {
        require(iterations in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) {
            "Iterations must be between $MIN_ACCEPTED_ITERATIONS and $MAX_ACCEPTED_ITERATIONS"
        }
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)

        val passwordUtf8 = password.toByteArray(Charsets.UTF_8)
        try {
            val hashBytes = deriveKey(
                passwordUtf8 = passwordUtf8,
                salt = salt,
                iterations = iterations,
                dkLenBytes = DEFAULT_DK_LEN_BYTES
            )
            val hexSalt = toHex(salt)
            val hexHash = toHex(hashBytes)
            return "$ALGORITHM_NAME$$iterations$$hexSalt$$hexHash"
        } finally {
            passwordUtf8.fill(0)
        }
    }

    fun verify(password: String, storedHash: String): Boolean {
        val parts = storedHash.split('$')
        if (parts.size != 4) return false
        if (parts[0] != ALGORITHM_NAME) return false

        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations !in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) return false

        val salt = fromHex(parts[2]) ?: return false
        if (salt.size < SALT_LENGTH_BYTES) return false

        val expectedHash = fromHex(parts[3]) ?: return false
        if (expectedHash.size < DEFAULT_DK_LEN_BYTES) return false

        val passwordUtf8 = password.toByteArray(Charsets.UTF_8)
        try {
            val actualHash = deriveKey(
                passwordUtf8 = passwordUtf8,
                salt = salt,
                iterations = iterations,
                dkLenBytes = expectedHash.size
            )
            return constantTimeEquals(expectedHash, actualHash)
        } catch (_: Exception) {
            return false
        } finally {
            passwordUtf8.fill(0)
        }
    }

    fun needsUpgrade(storedHash: String, targetIterations: Int = DEFAULT_ITERATIONS): Boolean {
        val parts = storedHash.split('$')
        if (parts.size != 4 || parts[0] != ALGORITHM_NAME) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        return iterations < targetIterations
    }

    fun deriveKey(
        passwordUtf8: ByteArray,
        salt: ByteArray,
        iterations: Int,
        dkLenBytes: Int
    ): ByteArray {
        require(iterations > 0) { "Iterations must be positive" }
        require(dkLenBytes > 0) { "Derived key length must be positive" }

        val effectiveKeyBytes = if (passwordUtf8.isEmpty()) {
            ByteArray(HMAC_SHA256_BLOCK_SIZE)
        } else {
            passwordUtf8
        }

        val mac = Mac.getInstance(JCA_MAC_ALGORITHM)
        val keySpec = SecretKeySpec(effectiveKeyBytes, JCA_MAC_ALGORITHM)
        mac.init(keySpec)

        val blockCount = ceil(dkLenBytes.toDouble() / HMAC_SHA256_DIGEST_LENGTH).toInt()
        val result = ByteArray(dkLenBytes)

        val u = ByteArray(HMAC_SHA256_DIGEST_LENGTH)
        val t = ByteArray(HMAC_SHA256_DIGEST_LENGTH)
        val blockIndexBytes = ByteArray(4)

        for (i in 1..blockCount) {

            blockIndexBytes[0] = (i ushr 24).toByte()
            blockIndexBytes[1] = (i ushr 16).toByte()
            blockIndexBytes[2] = (i ushr 8).toByte()
            blockIndexBytes[3] = i.toByte()

            mac.update(salt)
            mac.update(blockIndexBytes)
            mac.doFinal(u, 0)

            System.arraycopy(u, 0, t, 0, HMAC_SHA256_DIGEST_LENGTH)

            for (j in 2..iterations) {
                mac.update(u, 0, HMAC_SHA256_DIGEST_LENGTH)
                mac.doFinal(u, 0)
                for (k in 0 until HMAC_SHA256_DIGEST_LENGTH) {
                    t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
                }
            }

            val offset = (i - 1) * HMAC_SHA256_DIGEST_LENGTH
            val bytesToCopy = (dkLenBytes - offset).coerceAtMost(HMAC_SHA256_DIGEST_LENGTH)
            System.arraycopy(t, 0, result, offset, bytesToCopy)
        }

        return result
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }

    fun toHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val digits = "0123456789abcdef"
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = digits[v ushr 4]
            hexChars[i * 2 + 1] = digits[v and 0x0F]
        }
        return String(hexChars)
    }

    fun fromHex(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val high = Character.digit(hex[i * 2], 16)
            val low = Character.digit(hex[i * 2 + 1], 16)
            if (high == -1 || low == -1) return null
            result[i] = ((high shl 4) or low).toByte()
        }
        return result
    }
}
