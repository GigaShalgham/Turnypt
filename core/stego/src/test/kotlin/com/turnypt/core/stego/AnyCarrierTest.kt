package com.turnypt.core.stego

import com.turnypt.core.crypto.Argon2Params
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * AnyCarrier — the any-file EOF-append carrier (2026-11-22 user decree:
 * a hidden message inside EVERY file, sealed with full security).
 * Round-trips the two key models over carrier families whose parsers
 * tolerate trailing bytes, and refuses honestly when nothing rides.
 */
class AnyCarrierTest {

    private val kdf = Argon2Params(memoryExponent = 11, iterations = 1, parallelism = 1)

    private fun embed(
        carrier: ByteArray,
        message: String,
        passphrase: CharArray? = "shared-pass".toCharArray(),
        sessionKey: ByteArray? = null,
        keyId: ByteArray? = null,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        AnyCarrier.append(
            ByteArrayInputStream(carrier), out,
            message.toByteArray(Charsets.UTF_8),
            passphrase, sessionKey, keyId,
            kdf,
        )
        return out.toByteArray()
    }

    private inline fun expectNotACarrier(block: () -> Unit) {
        try {
            block()
            fail("expected NotACarrier")
        } catch (e: StegoError.NotACarrier) {
            // the typed signal (plan §4.5: GCM failure IS the detection)
        }
    }

    @Test
    fun `passphrase round-trip over arbitrary bytes`() {
        val carrier = ByteArray(4096) { (it * 31).toByte() }
        val message = "hidden for the decree: any file, any extension"
        val combined = embed(carrier, message)

        // The ORIGINAL bytes survive untouched at the front.
        assertTrue(combined.copyOfRange(0, carrier.size).contentEquals(carrier))

        val revealed = AnyCarrier.extract(
            ByteArrayInputStream(combined),
            "shared-pass".toCharArray(), null, kdf,
        )
        assertEquals(message, String(revealed, Charsets.UTF_8))
    }

    @Test
    fun `works for every extension family`() {
        // One representative per format class — the trailer survives
        // formats whose parsers tolerate trailing bytes (all of these do).
        val families = mapOf(
            "pdf" to byteArrayOf(0x25, 0x50, 0x44, 0x46) + ByteArray(512) { 0x0A },
            "zip" to byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(512) { 0 },
            "jpeg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(512) { 0x11 },
            "mp4" to byteArrayOf(0x00, 0x00, 0x00, 0x18) + "ftypmp42".toByteArray() + ByteArray(512) { 0 },
            "txt" to "just a plain text file\nnothing special\n".toByteArray(),
            "noext" to ByteArray(300) { 0x5A },
        )
        families.forEach { (name, carrier) ->
            val message = "hello inside $name"
            val combined = embed(carrier, message)
            val revealed = AnyCarrier.extract(
                ByteArrayInputStream(combined),
                "shared-pass".toCharArray(), null, kdf,
            )
            assertEquals(
                "carrier family: $name",
                message,
                String(revealed, Charsets.UTF_8),
            )
        }
    }

    @Test
    fun `wrong passphrase fails closed`() {
        val combined = embed(ByteArray(2048) { 7 }, "secret")
        expectNotACarrier {
            AnyCarrier.extract(
                ByteArrayInputStream(combined),
                "wrong-pass".toCharArray(), null, kdf,
            )
        }
    }

    @Test
    fun `a file without a trailer is not a carrier`() {
        expectNotACarrier {
            AnyCarrier.extract(
                ByteArrayInputStream(ByteArray(2048) { 9 }),
                "shared-pass".toCharArray(), null, kdf,
            )
        }
    }

    @Test
    fun `ecosystem round-trip through the era session key`() {
        val carrier = ByteArray(2048) { 3 }
        val sessionKey = ByteArray(32) { 0x42 }
        val keyId = ByteArray(16) { 0x77 }
        val message = "for the contact, salted by the trailer"

        val out = ByteArrayOutputStream()
        AnyCarrier.append(
            ByteArrayInputStream(carrier), out,
            message.toByteArray(Charsets.UTF_8),
            null, sessionKey, keyId, kdf,
        )
        val combined = out.toByteArray()

        // The passphrase path cannot open an ecosystem trailer.
        expectNotACarrier {
            AnyCarrier.extract(
                ByteArrayInputStream(combined),
                "anything".toCharArray(), null, kdf,
            )
        }

        // The resolver receives keyHint + salt and derives the same keys
        // the append side derived (HKDF over the session key).
        val revealed = AnyCarrier.extract(
            ByteArrayInputStream(combined), null,
            eraKeysByKeyId = { hint, salt ->
                if (hint.contentEquals(keyId)) {
                    AnyCarrier.deriveEcosystemKeys(sessionKey, salt)
                } else null
            },
            kdf,
        )
        assertEquals(message, String(revealed, Charsets.UTF_8))
    }

    @Test
    fun `a wrong keyHint resolves to no carrier`() {
        val carrier = ByteArray(2048) { 5 }
        val sessionKey = ByteArray(32) { 0x42 }
        val keyId = ByteArray(16) { 0x77 }
        val out = ByteArrayOutputStream()
        AnyCarrier.append(
            ByteArrayInputStream(carrier), out,
            "gone".toByteArray(Charsets.UTF_8),
            null, sessionKey, keyId, kdf,
        )
        expectNotACarrier {
            AnyCarrier.extract(
                ByteArrayInputStream(out.toByteArray()), null,
                eraKeysByKeyId = { hint, _ ->
                    if (hint.contentEquals(ByteArray(16) { 0x01 })) {
                        AnyCarrier.deriveEcosystemKeys(sessionKey, ByteArray(16))
                    } else null
                },
                kdf,
            )
        }
    }

    @Test
    fun `original carrier bytes survive untouched`() {
        val carrier = ByteArray(777) { (it * 7 and 0xFF).toByte() }
        val combined = embed(carrier, "intact?")
        assertEquals(
            carrier.size + combined.size - carrier.size,
            combined.size,
        )
        // Full prefix equality, not just length.
        assertTrue(
            combined.copyOfRange(0, carrier.size).contentEquals(carrier),
        )
    }
}
