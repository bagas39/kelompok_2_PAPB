package com.example.cloble.data.local.session

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator

class SessionTokenCipherUnitTest {

    private fun newCipher(): SessionTokenCipher {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val secretKey = keyGen.generateKey()
        return SessionTokenCipher { secretKey }
    }

    @Test
    fun encryptAndDecrypt_roundTripsAndUsesUniqueIvPerCall() {
        val cipher = newCipher()
        val token = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"

        val enc1 = cipher.encrypt(token)
        val enc2 = cipher.encrypt(token)

        assertTrue(enc1.startsWith("enc_v1:"))
        assertTrue(enc2.startsWith("enc_v1:"))
        assertNotEquals(enc1, enc2)
        assertEquals(token, cipher.decrypt(enc1))
        assertEquals(token, cipher.decrypt(enc2))
    }

    @Test
    fun decrypt_detectsTamperedCiphertextOrMalformedHeader() {
        val cipher = newCipher()
        val enc = cipher.encrypt("secret-session-token-123")
        val parts = enc.split(":")
        assertEquals(3, parts.size)

        val mutatedLastChar = if (parts[2].last() == 'A') 'B' else 'A'
        val tampered = "${parts[0]}:${parts[1]}:${parts[2].dropLast(1)}$mutatedLastChar"

        assertNull(cipher.decrypt(tampered))
        assertNull(cipher.decrypt("enc_v1:bad_base64"))
        assertNull(cipher.decrypt("enc_v1:short:payload"))
        assertNull(cipher.decrypt(null))
        assertNull(cipher.decrypt("   "))
    }

    @Test
    fun decrypt_supportsLegacyPlaintextFallback() {
        val cipher = newCipher()
        val legacyToken = "legacy-uuid-token-without-prefix"
        assertEquals(legacyToken, cipher.decrypt(legacyToken))
    }

    @Test
    fun pureKotlinBase64_handlesZeroOneAndTwoPaddingBytes() {
        val inputs = listOf(
            ByteArray(0),
            byteArrayOf(0x01),
            byteArrayOf(0x01, 0x02),
            byteArrayOf(0x01, 0x02, 0x03),
            "cloble-aes-gcm-padding-test-bytes".toByteArray()
        )
        for (input in inputs) {
            val encoded = SessionTokenCipher.encodeBase64(input)
            val decoded = SessionTokenCipher.decodeBase64(encoded)
            assertArrayEquals(input, decoded)
        }
        assertNull(SessionTokenCipher.decodeBase64("abc"))
        assertNull(SessionTokenCipher.decodeBase64("ab=c"))
    }

    @Test(expected = IllegalStateException::class)
    fun encrypt_whenKeyProviderReturnsNull_throwsIllegalStateException() {
        val cipher = SessionTokenCipher { null }
        cipher.encrypt("plain-session-token")
    }

    @Test
    fun encryptAndDecrypt_cachesSecretKeyAcrossMultipleCalls() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val secretKey = keyGen.generateKey()
        var providerCalls = 0
        val cipher = SessionTokenCipher {
            providerCalls += 1
            secretKey
        }

        val enc1 = cipher.encrypt("tok-1")
        val enc2 = cipher.encrypt("tok-2")
        assertEquals("tok-1", cipher.decrypt(enc1))
        assertEquals("tok-2", cipher.decrypt(enc2))
        assertEquals(1, providerCalls)
    }
}
