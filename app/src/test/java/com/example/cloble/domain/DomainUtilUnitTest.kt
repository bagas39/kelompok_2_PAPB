package com.example.cloble.domain

import com.example.cloble.domain.util.DistanceCalculator
import com.example.cloble.domain.util.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainUtilUnitTest {

    @Test
    fun distanceCalculator_computesAccurateDistances() {

        val distBandungCimahi = DistanceCalculator.calculateKm(-6.9175, 107.6191, -6.8722, 107.5425)
        assertEquals(10, distBandungCimahi)

        val distBandungDago = DistanceCalculator.calculateKm(-6.9175, 107.6191, -6.8915, 107.6107)
        assertEquals(3, distBandungDago)

        val distBandungJakarta = DistanceCalculator.calculateKm(-6.9175, 107.6191, -6.2088, 106.8456)
        assertTrue(distBandungJakarta in 115..125)

        assertEquals(0, DistanceCalculator.calculateKm(-6.9175, 107.6191, -6.9175, 107.6191))
    }

    @Test
    fun distanceCalculator_resolvesFallbackCoordinatesAndFormatsLabels() {
        val (malangLat, malangLon) = DistanceCalculator.resolveEffectiveCoordinates(
            city = "Arjosari, Kota Malang",
            latitude = -6.9175,
            longitude = 107.6191
        )
        assertTrue(malangLat < -7.5 && malangLon > 112.0)

        val (bandungLat, bandungLon) = DistanceCalculator.resolveEffectiveCoordinates(
            city = "Dago, Bandung",
            latitude = -6.9175,
            longitude = 107.6191
        )
        assertEquals(-6.9175, bandungLat, 0.0001)
        assertEquals(107.6191, bandungLon, 0.0001)

        val (exactLat, exactLon) = DistanceCalculator.resolveEffectiveCoordinates(
            city = "Arjosari, Kota Malang",
            latitude = -7.9318,
            longitude = 112.6560
        )
        assertEquals(-7.9318, exactLat, 0.0001)
        assertEquals(112.6560, exactLon, 0.0001)

        assertEquals("< 1 km", DistanceCalculator.formatDistanceLabel(0))
        assertEquals("1 km", DistanceCalculator.formatDistanceLabel(1))
        assertEquals("12 km", DistanceCalculator.formatDistanceLabel(12))
    }

    @Test
    fun passwordHasher_rfc7914_section11_vector1() {

        val p = "passwd".toByteArray(Charsets.UTF_8)
        val s = "salt".toByteArray(Charsets.UTF_8)
        val expected = "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
                "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783"

        val actualBytes = PasswordHasher.deriveKey(p, s, iterations = 1, dkLenBytes = 64)
        val actualHex = PasswordHasher.toHex(actualBytes)
        assertEquals(expected, actualHex)
    }

    @Test
    fun passwordHasher_rfc7914_section11_vector2() {

        val p = "Password".toByteArray(Charsets.UTF_8)
        val s = "NaCl".toByteArray(Charsets.UTF_8)
        val expected = "4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56" +
                "a1d425a1225833549adb841b51c9b3176a272bdebba1d078478f62b397f33c8d"

        val actualBytes = PasswordHasher.deriveKey(p, s, iterations = 80000, dkLenBytes = 64)
        val actualHex = PasswordHasher.toHex(actualBytes)
        assertEquals(expected, actualHex)
    }

    @Test
    fun passwordHasher_standardVectors_32bytes() {
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
    fun passwordHasher_emptyPasswordHandling() {

        val hash = PasswordHasher.hash("")
        assertNotNull(hash)
        assertTrue(PasswordHasher.verify("", hash))
        assertFalse(PasswordHasher.verify("wrong", hash))
    }

    @Test
    fun passwordHasher_utf8NonAsciiCharacters() {
        val unicodePw = "Kemeja👕CloBle123!_Bandung"
        val hash = PasswordHasher.hash(unicodePw, iterations = 10_000)
        assertTrue(PasswordHasher.verify(unicodePw, hash))
        assertFalse(PasswordHasher.verify("Kemeja CloBle123!_Bandung", hash))
    }

    @Test
    fun passwordHasher_uniqueSaltsAndVerify() {
        val pw = "CloBle123!"
        val hash1 = PasswordHasher.hash(pw, iterations = 10_000)
        val hash2 = PasswordHasher.hash(pw, iterations = 10_000)

        assertNotEquals(hash1, hash2)

        assertTrue(PasswordHasher.verify(pw, hash1))
        assertTrue(PasswordHasher.verify(pw, hash2))
        assertFalse(PasswordHasher.verify("WrongPass!", hash1))
    }

    @Test
    fun passwordHasher_needsUpgrade() {
        val lowerIterHash = PasswordHasher.hash("demo", iterations = 65_536)
        assertTrue(PasswordHasher.needsUpgrade(lowerIterHash, targetIterations = 120_000))

        val currentHash = PasswordHasher.hash("demo", iterations = 120_000)
        assertFalse(PasswordHasher.needsUpgrade(currentHash, targetIterations = 120_000))
    }
}
