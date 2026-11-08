package com.example.cloble.domain

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cloble.domain.util.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PasswordHasherAndroidTest {

    @Test
    fun testVector1_rfc7914_section11_onAndroidRuntime() {

        val p = "passwd".toByteArray(Charsets.UTF_8)
        val s = "salt".toByteArray(Charsets.UTF_8)
        val expected = "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
                "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783"

        val actualBytes = PasswordHasher.deriveKey(p, s, iterations = 1, dkLenBytes = 64)
        val actualHex = PasswordHasher.toHex(actualBytes)
        assertEquals(expected, actualHex)
    }

    @Test
    fun testVector2_rfc7914_section11_onAndroidRuntime() {

        val p = "Password".toByteArray(Charsets.UTF_8)
        val s = "NaCl".toByteArray(Charsets.UTF_8)
        val expected = "4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56" +
                "a1d425a1225833549adb841b51c9b3176a272bdebba1d078478f62b397f33c8d"

        val actualBytes = PasswordHasher.deriveKey(p, s, iterations = 80000, dkLenBytes = 64)
        val actualHex = PasswordHasher.toHex(actualBytes)
        assertEquals(expected, actualHex)
    }

    @Test
    fun standardVectors_32bytes_onAndroidRuntime() {
        val p = "password".toByteArray(Charsets.UTF_8)
        val s = "salt".toByteArray(Charsets.UTF_8)

        val v1 = PasswordHasher.toHex(PasswordHasher.deriveKey(p, s, 1, 32))
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b", v1)

        val v2 = PasswordHasher.toHex(PasswordHasher.deriveKey(p, s, 2, 32))
        assertEquals("ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43", v2)

        val v4096 = PasswordHasher.toHex(PasswordHasher.deriveKey(p, s, 4096, 32))
        assertEquals("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a", v4096)
    }

    @Test
    fun emptyPassword_handledCorrectly_onAndroidRuntime() {
        val hash = PasswordHasher.hash("")
        assertNotNull(hash)
        assertTrue(PasswordHasher.verify("", hash))
        assertFalse(PasswordHasher.verify("wrong", hash))
    }

    @Test
    fun utf8NonAscii_handledCorrectly_onAndroidRuntime() {
        val unicodePw = "Kemeja👕CloBle123!_Bandung"
        val hash = PasswordHasher.hash(unicodePw, iterations = 10_000)
        assertTrue(PasswordHasher.verify(unicodePw, hash))
        assertFalse(PasswordHasher.verify("Kemeja CloBle123!_Bandung", hash))
    }

    @Test
    fun verify_acceptsCorrectAndRejectsWrong() {
        val pw = "SecretPassword123!"
        val hash = PasswordHasher.hash(pw, iterations = 15_000)
        assertTrue(PasswordHasher.verify(pw, hash))
        assertFalse(PasswordHasher.verify("SecretPassword123", hash))
        assertFalse(PasswordHasher.verify("secretpassword123!", hash))
        assertFalse(PasswordHasher.verify("", hash))
    }

    @Test
    fun reproducibleBenchmark_onAndroidRuntime() = runBlocking {
        withContext(Dispatchers.Default) {
            val iterations = PasswordHasher.DEFAULT_ITERATIONS
            val testPassword = "BenchmarkTestPassword2026!"

            repeat(2) {
                PasswordHasher.hash(testPassword, iterations = 10_000)
            }

            val sampleCount = 5
            val durationsMs = mutableListOf<Double>()

            repeat(sampleCount) {
                val startNanos = SystemClock.elapsedRealtimeNanos()
                val hash = PasswordHasher.hash(testPassword, iterations = iterations)
                val endNanos = SystemClock.elapsedRealtimeNanos()
                assertTrue(PasswordHasher.verify(testPassword, hash))

                val durationMs = (endNanos - startNanos) / 1_000_000.0
                durationsMs.add(durationMs)
            }

            durationsMs.sort()
            val minMs = durationsMs.first()
            val maxMs = durationsMs.last()
            val medianMs = durationsMs[sampleCount / 2]

            println(
                """
                ============================================================
                [BENCHMARK] PasswordHasher PBKDF2-HMAC-SHA256 (Android ART)
                - Iterations: $iterations
                - Samples: $sampleCount
                - Min: ${"%.2f".format(minMs)} ms
                - Median: ${"%.2f".format(medianMs)} ms
                - Max: ${"%.2f".format(maxMs)} ms
                ============================================================
                """.trimIndent()
            )

            assertTrue("Benchmark median ($medianMs ms) exceeds 500 ms limit", medianMs <= 500.0)
        }
    }
}
