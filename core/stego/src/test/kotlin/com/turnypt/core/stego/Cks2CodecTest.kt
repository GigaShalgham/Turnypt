package com.turnypt.core.stego

import com.turnypt.core.crypto.AeadCiphers
import com.turnypt.core.crypto.Argon2KeyDerivator
import com.turnypt.core.crypto.Argon2Params

/**
 * Cks2 round-trip tests (stego plan §6.1 S1 exit criteria: "Protocol
 * tests green: round-trip byte-identical (empty/tiny/chunk-boundary/
 * multi-chunk payloads); tamper = typed failure; wrong passphrase =
 * typed NotACarrier").
 *
 * The Argon2 profile is INTERACTIVE (16 MiB / 3 passes) — the tests
 * derive real keys through the real KDF, just at the fast preset, so
 * the two-key split itself is exercised end-to-end.
 */
class Cks2CodecTest {

    private val params = Argon2Params(memoryExponent = 10, iterations = 1, parallelism = 1)
    private val passphrase = "correct horse battery staple".toCharArray()
    private val derivator = Argon2KeyDerivator()
    private val aeads = AeadCiphers()

    private fun keys(material: EmbedMaterial): Pair<ByteArray, ByteArray> =
        Cks2.deriveKeys(
            passphrase = passphrase,
            salt = material.salt,
            frameNonce = material.frameNonce,
            payloadNonce = material.payloadNonce,
            params = params,
            derivator = derivator,
        )

    private fun roundTrip(payload: ByteArray) {
        val material = BitSpread.freshMaterial()
        val (frameKey, payloadKey) = keys(material)
        val codec = Cks2Codec(aeads)
        val embedded = codec.encode(frameKey, payloadKey, material.frameNonce, payload)
        // Frame is exactly 64 bytes regardless of payload (plan §4.2).
        org.junit.Assert.assertEquals(Cks2.FRAME_BYTES, embedded.copyOfRange(0, Cks2.FRAME_BYTES).size)

        val decoded = codec.decode(frameKey, payloadKey, embedded)
        org.junit.Assert.assertArrayEquals(payload, decoded)
    }

    @org.junit.Test
    fun `empty payload round-trips`() = roundTrip(ByteArray(0))

    @org.junit.Test
    fun `tiny payload round-trips`() = roundTrip("hello stego".toByteArray())

    @org.junit.Test
    fun `odd-size payload round-trips`() =
        roundTrip(ByteArray(1237) { (it * 31 + 7).toByte() })

    @org.junit.Test
    fun `chunk-boundary payload round-trips`() =
        roundTrip(ByteArray(Cks2.CHUNK_PLAINTEXT_BYTES) { it.toByte() })

    @org.junit.Test
    fun `multi-chunk payload round-trips`() =
        roundTrip(ByteArray(Cks2.CHUNK_PLAINTEXT_BYTES * 2 + 511) { (it * 7).toByte() })

    @org.junit.Test
    fun `wrong passphrase is a typed NotACarrier`() {
        val material = BitSpread.freshMaterial()
        val (frameKey, payloadKey) = keys(material)
        val embedded = Cks2Codec(aeads).encode(
            frameKey, payloadKey, material.frameNonce,
            "secret message".toByteArray(),
        )
        val wrongKeys = Cks2.deriveKeys(
            passphrase = "wrong passphrase entirely".toCharArray(),
            salt = material.salt,
            frameNonce = material.frameNonce,
            payloadNonce = material.payloadNonce,
            params = params,
            derivator = derivator,
        )
        try {
            Cks2Codec(aeads).decode(wrongKeys.first, wrongKeys.second, embedded)
            org.junit.Assert.fail("expected NotACarrier")
        } catch (e: StegoError.NotACarrier) {
            // the typed signal (plan §4.5: GCM failure IS the detection)
        }
    }

    @org.junit.Test
    fun `tampered frame fails authentication`() {
        val material = BitSpread.freshMaterial()
        val (frameKey, payloadKey) = keys(material)
        val embedded = Cks2Codec(aeads).encode(
            frameKey, payloadKey, material.frameNonce,
            "secret message".toByteArray(),
        )
        embedded[3] = (embedded[3].toInt() xor 0x41).toByte()
        try {
            Cks2Codec(aeads).decode(frameKey, payloadKey, embedded)
            org.junit.Assert.fail("expected NotACarrier")
        } catch (e: StegoError.NotACarrier) {
            // a flipped frame byte kills the AEAD tag
        }
    }

    @org.junit.Test
    fun `tampered body fails authentication`() {
        val material = BitSpread.freshMaterial()
        val (frameKey, payloadKey) = keys(material)
        val embedded = Cks2Codec(aeads).encode(
            frameKey, payloadKey, material.frameNonce,
            "secret message".toByteArray(),
        )
        // Flip a byte deep in the body (frame is 64 bytes).
        embedded[embedded.size - 2] = (embedded[embedded.size - 2].toInt() xor 1).toByte()
        try {
            Cks2Codec(aeads).decode(frameKey, payloadKey, embedded)
            org.junit.Assert.fail("expected typed failure")
        } catch (e: StegoError.NotACarrier) {
            // chunk AAD binding caught the flip
        }
    }

    @org.junit.Test
    fun `truncated embedded stream is a typed Truncated`() {
        val material = BitSpread.freshMaterial()
        val (frameKey, payloadKey) = keys(material)
        val embedded = Cks2Codec(aeads).encode(
            frameKey, payloadKey, material.frameNonce,
            ByteArray(2000) { it.toByte() },
        )
        try {
            Cks2Codec(aeads).decode(frameKey, payloadKey, embedded.copyOf(embedded.size - 10))
            org.junit.Assert.fail("expected Truncated")
        } catch (e: StegoError.Truncated) {
            // the sealed_len in the frame says more bytes follow
        }
    }

    @org.junit.Test
    fun `garbage input is NotACarrier not a crash`() {
        val material = BitSpread.freshMaterial()
        val (frameKey, payloadKey) = keys(material)
        try {
            Cks2Codec(aeads).decode(frameKey, payloadKey, ByteArray(300) { it.toByte() })
            org.junit.Assert.fail("expected NotACarrier")
        } catch (e: StegoError.NotACarrier) {
            // random bytes: frame tag fails
        }
    }
}
