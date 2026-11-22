package com.turnypt.core.stego

import com.turnypt.core.crypto.Argon2Params
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * WavCarrier tests (stego plan §6.1 S3 exit criteria: "WAV null-test +
 * AB listening pass"). The null-test is the round-trip + sample-delta
 * gates here; the AB listening pass is a release-gate human check, not
 * a code artifact — noted in STEGO.md.
 */
class WavCarrierTest {

    private val params = Argon2Params(memoryExponent = 10, iterations = 1, parallelism = 1)
    private val cipher = WavStegoCipher(params)

    /** Builds a minimal valid 16-bit stereo PCM WAV around random samples. */
    private fun synthWav(sampleCount: Int, bits: Int = 16, channels: Int = 2): Pair<ByteArray, WavCarrier.WavInfo> {
        val dataBytes = sampleCount * (bits / 8)
        val total = 44 + dataBytes
        val bytes = ByteArray(total)
        // RIFF header
        "RIFF".toByteArray(Charsets.US_ASCII).copyInto(bytes, 0)
        writeInt32LE(bytes, 4, total - 8)
        "WAVE".toByteArray(Charsets.US_ASCII).copyInto(bytes, 8)
        "fmt ".toByteArray(Charsets.US_ASCII).copyInto(bytes, 12)
        writeInt32LE(bytes, 16, 16)
        bytes[20] = 1; bytes[21] = 0 // PCM
        writeInt16LE(bytes, 22, channels)
        writeInt32LE(bytes, 24, 44100)
        writeInt32LE(bytes, 28, 44100 * channels * (bits / 8))
        writeInt16LE(bytes, 32, channels * (bits / 8))
        writeInt16LE(bytes, 34, bits)
        "data".toByteArray(Charsets.US_ASCII).copyInto(bytes, 36)
        writeInt32LE(bytes, 40, dataBytes)
        // random PCM samples (bounded so 24-bit sign-extend is exercised)
        val rng = java.util.Random(42)
        for (i in 0 until sampleCount) {
            val v = when (bits) {
                16 -> rng.nextInt(65536) - 32768
                24 -> rng.nextInt(16777216) - 8388608
                else -> error("bits")
            }
            WavCarrier.writeSample(bytes, WavInfoLazy(bits, channels, dataBytes).info(), i, v)
        }
        val info = WavCarrier.parse(bytes)
        return bytes to info
    }

    /** Helper so writeSample during synthesis has a WavInfo before parse. */
    private class WavInfoLazy(private val bits: Int, private val channels: Int, private val dataBytes: Int) {
        fun info() = WavCarrier.WavInfo(bits, (dataBytes / (bits / 8)).toLong(), channels, 44100, 44, dataBytes.toLong())
    }

    private fun writeInt32LE(b: ByteArray, at: Int, v: Int) {
        b[at] = (v and 0xFF).toByte()
        b[at + 1] = ((v shr 8) and 0xFF).toByte()
        b[at + 2] = ((v shr 16) and 0xFF).toByte()
        b[at + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun writeInt16LE(b: ByteArray, at: Int, v: Int) {
        b[at] = (v and 0xFF).toByte()
        b[at + 1] = ((v shr 8) and 0xFF).toByte()
    }

    @Test
    fun `message round-trips through 16-bit wav`() {
        val (bytes, info) = synthWav(4096)
        val message = "audio channel check".toByteArray()
        cipher.embed(bytes, info, "pass".toCharArray(), message)
        val back = cipher.extract(bytes, info, "pass".toCharArray())
        assertArrayEquals(message, back)
    }

    @Test
    fun `message round-trips through 24-bit wav`() {
        val (bytes, info) = synthWav(2048, bits = 24)
        val message = "24-bit carrier".toByteArray()
        cipher.embed(bytes, info, "pw24".toCharArray(), message)
        val back = cipher.extract(bytes, info, "pw24".toCharArray())
        assertArrayEquals(message, back)
    }

    @Test
    fun `empty message round-trips`() {
        val (bytes, info) = synthWav(1024)
        cipher.embed(bytes, info, "pw".toCharArray(), ByteArray(0))
        assertArrayEquals(ByteArray(0), cipher.extract(bytes, info, "pw".toCharArray()))
    }

    @Test
    fun `sample delta is at most one (null-test)`() {
        val (bytes, info) = synthWav(6000)
        val before = ByteArray(bytes.size) { bytes[it] }
        cipher.embed(bytes, info, "pw".toCharArray(), "dense audio probe".toByteArray())
        var deltas = 0
        for (i in 0 until info.sampleCount.toInt()) {
            val a = WavCarrier.readSample(before, info, i)
            val b = WavCarrier.readSample(bytes, info, i)
            assertTrue("sample $i moved by ${abs(a - b)}", abs(a - b) <= 1)
            if (a != b) deltas++
        }
        assertTrue("embed touched nothing", deltas > 0)
        // header (non-PCM bytes) never changes
        for (i in 0 until info.dataOffset.toInt()) {
            assertEquals(before[i], bytes[i])
        }
        // frame alignment/size fields intact
        assertEquals(info, WavCarrier.parse(bytes))
    }

    @Test
    fun `wrong passphrase is typed NotACarrier`() {
        val (bytes, info) = synthWav(2048)
        cipher.embed(bytes, info, "right".toCharArray(), "hidden".toByteArray())
        try {
            cipher.extract(bytes, info, "wrong".toCharArray())
            org.junit.Assert.fail("expected NotACarrier")
        } catch (e: StegoError.NotACarrier) {
            // expected
        }
    }

    @Test
    fun `oversized payload is CarrierTooSmall`() {
        val (bytes, info) = synthWav(128) // 128 bits capacity < 80B prefix
        try {
            cipher.embed(bytes, info, "pw".toCharArray(), ByteArray(64))
            org.junit.Assert.fail("expected CarrierTooSmall")
        } catch (e: StegoError.CarrierTooSmall) {
            // pre-flight
        }
    }

    @Test
    fun `parser rejects non-pcm and corrupt input`() {
        try {
            WavCarrier.parse(ByteArray(64))
            org.junit.Assert.fail("expected rejection")
        } catch (e: IllegalArgumentException) {
            // not RIFF
        }
        val (bytes, _) = synthWav(64)
        bytes[20] = 3 // format = IEEE float -> reject
        try {
            WavCarrier.parse(bytes)
            org.junit.Assert.fail("expected rejection")
        } catch (e: IllegalArgumentException) {
            // only PCM supported
        }
    }

    @Test
    fun `parser handles odd-sized chunk padding`() {
        // build a wav with a LIST chunk of ODD size before fmt/data
        val (orig, _) = synthWav(128)
        val listChunk = "LIST".toByteArray(Charsets.US_ASCII) + byteArrayOf(3, 0, 0, 0) + "abc".toByteArray(Charsets.US_ASCII) // size 3 -> pad byte
        val patched = ByteArray(orig.size + 12) // +12 = 8 header + 3 data + 1 pad
        "RIFF".toByteArray().copyInto(patched, 0)
        // RIFF size + the inserted chunk's total footprint
        writeInt32LE(patched, 4, orig.size - 8 + 12)
        "WAVE".toByteArray().copyInto(patched, 8)
        listChunk.copyInto(patched, 12) // 8 header + 3 data + 1 pad = 12 bytes
        orig.copyInto(patched, 12 + 12, 12, orig.size)
        val info = WavCarrier.parse(patched)
        assertEquals(128, info.sampleCount)
    }
}
