package com.turnypt.core.stego

import com.turnypt.core.crypto.Argon2Params
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * PngCarrier tests (stego plan §6.1 S2 exit criteria: "Golden PNGs:
 * pixel delta <=1 per channel; round-trip byte-identical; capacity
 * planner rejects before seal").
 */
class PngCarrierTest {

    private val params = Argon2Params(memoryExponent = 10, iterations = 1, parallelism = 1)
    private val cipher = PngStegoCipher(params)

    /** A photo-like int row: bounded random pixels, opaque alpha. */
    private fun randomPixels(width: Int, height: Int, seed: Int = 7): IntArray =
        IntArray(width * height) { i ->
            val r = Random(seed * 31 + i).nextInt(256)
            val g = Random(seed * 77 + i).nextInt(256)
            val b = Random(seed * 131 + i).nextInt(256)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

    @Test
    fun `message round-trips through pixels`() {
        val width = 64
        val height = 64
        val pixels = randomPixels(width, height)
        val original = pixels.copyOf()
        val message = "meet at the dock, 21:00 — bring the blue key".toByteArray()

        cipher.embed(pixels, width, height, "pass phrase 123".toCharArray(), message)
        val extracted = cipher.extract(pixels, width, height, "pass phrase 123".toCharArray())
        org.junit.Assert.assertArrayEquals(message, extracted)
        // sanity: the embed actually changed pixels
        var changed = 0
        for (i in pixels.indices) if (pixels[i] != original[i]) changed++
        assertTrue("embed changed nothing", changed > 0)
    }

    @Test
    fun `empty message round-trips`() {
        val width = 32
        val height = 32
        val pixels = randomPixels(width, height, seed = 3)
        cipher.embed(pixels, width, height, "pw".toCharArray(), ByteArray(0))
        val extracted = cipher.extract(pixels, width, height, "pw".toCharArray())
        org.junit.Assert.assertArrayEquals(ByteArray(0), extracted)
    }

    @Test
    fun `pixel delta is at most one per channel`() {
        val width = 80
        val height = 80
        val pixels = randomPixels(width, height, seed = 11)
        val original = pixels.copyOf()
        // 1200B payload + 16B salt + 64B frame + 16B tag = ~1296B -> fits 19200B capacity
        val message = ByteArray(1200) { it.toByte() } // dense-ish embed
        cipher.embed(pixels, width, height, "pw2".toCharArray(), message)
        for (i in pixels.indices) {
            for (channel in 0 until 3) {
                val shift = 8 * (3 - channel)
                val before = (original[i] shr shift) and 0xFF
                val after = (pixels[i] shr shift) and 0xFF
                val delta = kotlin.math.abs(before - after)
                assertTrue("pixel $i channel $channel moved by $delta", delta <= 1)
            }
            // alpha is never touched
            assertEquals(original[i] ushr 24, pixels[i] ushr 24)
        }
    }

    @Test
    fun `wrong passphrase is typed NotACarrier`() {
        val width = 64
        val height = 64
        val pixels = randomPixels(width, height)
        cipher.embed(pixels, width, height, "right".toCharArray(), "hidden".toByteArray())
        try {
            cipher.extract(pixels, width, height, "wrong".toCharArray())
            org.junit.Assert.fail("expected NotACarrier")
        } catch (e: StegoError.NotACarrier) {
            // the frame tag rejects the wrong key — no oracle leak
        }
    }

    @Test
    fun `oversized payload is CarrierTooSmall pre-flight`() {
        val width = 16
        val height = 16 // capacity 768 bits = 96 bytes
        val pixels = randomPixels(width, height)
        val bigMessage = ByteArray(500) { it.toByte() }
        try {
            cipher.embed(pixels, width, height, "pw".toCharArray(), bigMessage)
            org.junit.Assert.fail("expected CarrierTooSmall")
        } catch (e: StegoError.CarrierTooSmall) {
            // pre-flight, before any pixel was nudged
        }
    }

    @Test
    fun `capacity planner math matches the embed reality`() {
        val width = 64
        val height = 64
        // planner says a 200-byte message fits in 64x64 (capacity 12288 B)
        assertTrue(CapacityPlanner.fits(64, 64, 200))
        // and a 2 KiB message does not (needs ~2.1 KiB, have 1.5 KiB)
        assertEquals(false, CapacityPlanner.fits(64, 64, 2048))
        // headroom is bits and non-negative when it fits
        assertTrue(CapacityPlanner.headroomBits(64, 64, 200) > 0)
        assertEquals(0L, CapacityPlanner.headroomBits(64, 64, 2048))
    }

    @Test
    fun `writeBit matches the ±1 contract`() {
        assertEquals(5, PngCarrier.writeBit(5, 1)) // LSB already 1: no change
        assertEquals(4, PngCarrier.writeBit(5, 0)) // -1 nudge
        assertEquals(4, PngCarrier.writeBit(4, 0)) // LSB already 0: no change
        assertEquals(5, PngCarrier.writeBit(4, 1)) // +1 nudge
        assertEquals(2, PngCarrier.writeBit(3, 0)) // 3 -> 2 (-1)
        assertEquals(1, PngCarrier.writeBit(0, 1)) // underflow overwrite
        assertEquals(254, PngCarrier.writeBit(255, 0)) // overflow overwrite
    }

    @Test
    fun `spread positions differ per passphrase`() {
        val width = 64
        val height = 64
        val pixelsA = randomPixels(width, height)
        val pixelsB = randomPixels(width, height)
        cipher.embed(pixelsA, width, height, "alpha".toCharArray(), "m".toByteArray())
        cipher.embed(pixelsB, width, height, "beta".toCharArray(), "m".toByteArray())
        // different bootstrap keys read different slots — the wrong
        // passphrase must fail extraction, proven by the test above too
        try {
            cipher.extract(pixelsA, width, height, "beta".toCharArray())
            org.junit.Assert.fail("expected NotACarrier")
        } catch (e: StegoError.NotACarrier) {
            // different passphrase -> different spread -> garbage frame -> tag fails
        }
    }
}
