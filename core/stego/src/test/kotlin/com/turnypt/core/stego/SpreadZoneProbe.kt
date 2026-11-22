package com.turnypt.core.stego

import com.turnypt.core.crypto.Argon2Params
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * Zone probe: the two-zone layout (sequential salt+frame prefix, keyed
 * body) round-trips when driven manually step by step.
 */
class SpreadZoneProbe {

    private val params = Argon2Params(memoryExponent = 10, iterations = 1, parallelism = 1)
    private val cipher = PngStegoCipher(params)

    @Test
    fun `facade round-trip`() {
        val width = 64
        val height = 64
        val pixels = IntArray(width * height) { 0xFF888888.toInt() }
        val message = "facade probe".toByteArray()
        cipher.embed(pixels, width, height, "probe".toCharArray(), message)
        val back = cipher.extract(pixels, width, height, "probe".toCharArray())
        assertArrayEquals(message, back)
    }

    @Test
    fun `collision diagnostic for positions at capacity 12288`() {
        val key = ByteArray(32) { 5 }
        val positions = BitSpread.positions(640, 12_288, key)
        val seen = HashMap<Long, Int>()
        for (v in positions) seen[v] = (seen[v] ?: 0) + 1
        val dupes = seen.entries.filter { it.value > 1 }
        org.junit.Assert.assertTrue("collisions: ${dupes.size} e.g. ${dupes.take(3)}", dupes.isEmpty())
        org.junit.Assert.assertTrue(positions.all { it in 0 until 12_288 })
    }
}
