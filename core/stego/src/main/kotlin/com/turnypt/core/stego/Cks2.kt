package com.turnypt.core.stego

import com.turnypt.core.crypto.AeadCiphers
import com.turnypt.core.crypto.AeadFailure
import com.turnypt.core.crypto.Argon2KeyDerivator
import com.turnypt.core.crypto.Argon2Params
import com.turnypt.core.crypto.CipherAlgorithm
import com.turnypt.core.crypto.CryptoError
import com.turnypt.core.crypto.Hkdf

/**
 * CKS2 — the Turnypt Stego container, version 2 family (stego plan §4.2,
 * revision 1.1). The stego sibling of CKF1: a payload sealed for a
 * PASSPHRASE-ONLY key model, then spread bit-by-bit into a carrier by
 * [BitSpread] at a higher layer. This codec owns only the byte stream.
 *
 * Key derivation (the plan's two-key split):
 *
 * ```
 * passphrase + salt(16B, random per embed) + frameNonce(12B)
 *   + payloadNonce(12B) --Argon2id--> master (32B)
 * K_frame   = HKDF-SHA256(master, salt, info="CKS2/frame/v1")
 * K_payload = HKDF-SHA256(master, salt, info="CKS2/payload/v1")
 * ```
 *
 * The two nonces ride INSIDE the Argon2 input — a wrong passphrase can
 * never produce the right keys even by luck, because the frame nonce is
 * read from the carrier before any key is verified (see [StegoError]).
 *
 * Embedded stream (what BitSpread stores in the carrier):
 *
 * ```
 * Embedded stream = FRAME(64B) || body
 * FRAME = frameNonce(12, CLEARTEXT) || AES-256-GCM(K_frame, nonce =
 *         frameNonce, no AAD, plaintext = sealed_len(24 BE), tag(16))
 *         -> 12 + (24+16) = 52B + 12B zero pad = 64B
 * body  = payload AEAD-sealed per CKF1 rules, key K_payload:
 *         64 KiB chunks, nonce = payloadNonce[0..4) || counter(8 BE),
 *         AAD = FRAME || chunk position || finality flag
 * ```
 *
 * The frame nonce MUST be cleartext: the extractor reads it from the
 * carrier BEFORE any key is verified (it is the GCM nonce for the frame
 * itself) — a wrong passphrase then simply fails the frame tag (the
 * plan's §4.5 detection model: no public marker, the GCM failure IS
 * the "not a carrier" signal).
 *
 * body plaintext (post-decrypt) — Phase 1:
 * `[inner type(1)=0x01][len(4 BE)][UTF-8 bytes]`; Phase 2 adds the
 * name/MIME/CRC32 record under inner type 0x02.
 *
 * Memory discipline (plan §5.3): at most ONE 64 KiB chunk plus carrier
 * row buffers in memory at any time — this codec is stream-only.
 */
object Cks2 {

    /** HKDF `info` strings — the two-key split (plan §4.2). */
    val FRAME_INFO: ByteArray = "CKS2/frame/v1".toByteArray(Charsets.US_ASCII)
    val PAYLOAD_INFO: ByteArray = "CKS2/payload/v1".toByteArray(Charsets.US_ASCII)
    internal val PAYLOAD_INFO_NONCE: ByteArray = "CKS2/payloadnonce/v1".toByteArray(Charsets.US_ASCII)

    /** The fixed 64-byte authenticated frame (plan revision 1.1). */
    const val FRAME_BYTES: Int = 64
    const val FRAME_NONCE_BYTES: Int = 12
    const val FRAME_PLAINTEXT_BYTES: Int = 24
    private val FRAME_PAD_BYTES: Int =
        FRAME_BYTES - FRAME_NONCE_BYTES - FRAME_PLAINTEXT_BYTES - 16

    /** Chunk rule inherited from CKF1 — same streaming shape. */
    const val CHUNK_PLAINTEXT_BYTES: Int = 64 * 1024

    /** Inner payload types (plan §4.2 body layout). */
    const val INNER_MESSAGE: Int = 0x01
    const val INNER_FILE: Int = 0x02

    /** The frame AEAD is fixed to AES-256-GCM (plan §4.2). */
    private val FRAME_ALGORITHM = CipherAlgorithm.AES_256_GCM

    /**
     * Derives the two subkeys from the passphrase material. The [salt],
     * [frameNonce] and [payloadNonce] are all bound into the Argon2 input
     * AND the salt rides the HKDF too — a receiver with the wrong
     * passphrase derives wrong keys before any tag is checked.
     */
    fun deriveKeys(
        passphrase: CharArray,
        salt: ByteArray,
        frameNonce: ByteArray,
        payloadNonce: ByteArray,
        params: Argon2Params,
        derivator: Argon2KeyDerivator,
    ): Pair<ByteArray, ByteArray> {
        // The two nonces ride the Argon2 ASSOCIATED-DATA input (the RFC
        // field BC exposes via withAdditional): the derived master is
        // bound to this exact embed's nonces WITHOUT mangling the
        // passphrase encoding — the char-roundtrip trick previously
        // corrupted every byte >= 0x80 of the nonce material (signed
        // Byte -> Char -> byte loses the low byte cleanly but high bytes
        // collided), which made deriveKeys non-deterministic across the
        // JVM's char handling. Argon2's AD input is length-free and
        // authenticated by the KDF itself.
        val master = derivator.derive(
            passphrase = passphrase,
            salt = salt,
            params = params,
            outputLength = 32,
            associatedData = frameNonce + payloadNonce,
        )
        val frameKey = Hkdf.sha256(master, salt, FRAME_INFO, 32)
        val payloadKey = Hkdf.sha256(master, salt, PAYLOAD_INFO, 32)
        return frameKey to payloadKey
    }

    /**
     * Serialises the 64-byte frame: the CLEARTEXT [frameNonce], then the
     * AEAD seal of `sealed_len(24 BE)` under [frameKey] with that nonce,
     * then [FRAME_PAD_BYTES] zero bytes so BitSpread sees a constant
     * width. The pad is NOT authenticated; the nonce must be cleartext
     * because it is the GCM nonce the extractor needs first.
     */
    fun sealFrame(
        frameKey: ByteArray,
        frameNonce: ByteArray,
        sealedLengthBytes: Long,
        aeads: AeadCiphers = AeadCiphers(),
    ): ByteArray {
        val plaintext = ByteArray(FRAME_PLAINTEXT_BYTES)
        writeLong24(plaintext, 0, sealedLengthBytes)
        val sealed = aeads.of(FRAME_ALGORITHM).seal(
            key = frameKey,
            nonce = frameNonce,
            plaintext = plaintext,
            associatedData = ByteArray(0),
        )
        check(sealed.size == FRAME_PLAINTEXT_BYTES + FRAME_ALGORITHM.tagLengthBytes)
        val frame = ByteArray(FRAME_BYTES)
        frameNonce.copyInto(frame, 0)
        sealed.copyInto(frame, FRAME_NONCE_BYTES)
        return frame
    }

    /**
     * Reads and authenticates the frame from the first
     * [FRAME_BYTES] bytes of [embedded]. Returns the frame nonce and the
     * total sealed body length. Throws [AeadFailure] (crypto taxonomy)
     * when the tag does not verify — the extractor's "not a stego
     * carrier (or wrong passphrase)" signal (plan §4.5: no public
     * marker exists, so the GCM failure IS the detection model).
     */
    fun openFrame(
        embedded: ByteArray,
        frameKey: ByteArray,
        aeads: AeadCiphers = AeadCiphers(),
    ): Pair<ByteArray, Long> {
        if (embedded.size < FRAME_BYTES) throw StegoError.NotACarrier
        val frameNonce = embedded.copyOfRange(0, FRAME_NONCE_BYTES)
        val sealed = embedded.copyOfRange(
            FRAME_NONCE_BYTES,
            FRAME_NONCE_BYTES + FRAME_PLAINTEXT_BYTES + FRAME_ALGORITHM.tagLengthBytes,
        )
        try {
            val plaintext = aeads.of(FRAME_ALGORITHM).open(
                key = frameKey,
                nonce = frameNonce,
                sealed = sealed,
                associatedData = ByteArray(0),
            )
            return frameNonce to readLong24(plaintext, 0)
        } catch (e: AeadFailure) {
            // Distinguish "tag failed" from a genuine key/nonce misuse.
            if (e.error == CryptoError.AuthenticationFailed ||
                e.error == CryptoError.MalformedMessage
            ) {
                throw StegoError.NotACarrier
            }
            throw e
        }
    }

    /** chunk nonce = payloadNonce[0..4) ‖ counter (8 bytes BE) — CKF1 rule. */
    internal fun chunkNonce(payloadNonce: ByteArray, counter: Long): ByteArray {
        val nonce = ByteArray(12)
        payloadNonce.copyInto(nonce, 0, 0, 4)
        nonce[4] = (counter ushr 56).toByte()
        nonce[5] = (counter ushr 48).toByte()
        nonce[6] = (counter ushr 40).toByte()
        nonce[7] = (counter ushr 32).toByte()
        nonce[8] = (counter ushr 24).toByte()
        nonce[9] = (counter ushr 16).toByte()
        nonce[10] = (counter ushr 8).toByte()
        nonce[11] = counter.toByte()
        return nonce
    }

    /** chunk AAD = FRAME(64) ‖ counter (8 BE) ‖ last-flag — CKF1 shape. */
    internal fun chunkAad(frame: ByteArray, counter: Long, last: Boolean): ByteArray {
        val aad = ByteArray(FRAME_BYTES + 9)
        frame.copyInto(aad, 0)
        aad[FRAME_BYTES] = (counter ushr 56).toByte()
        aad[FRAME_BYTES + 1] = (counter ushr 48).toByte()
        aad[FRAME_BYTES + 2] = (counter ushr 40).toByte()
        aad[FRAME_BYTES + 3] = (counter ushr 32).toByte()
        aad[FRAME_BYTES + 4] = (counter ushr 24).toByte()
        aad[FRAME_BYTES + 5] = (counter ushr 16).toByte()
        aad[FRAME_BYTES + 6] = (counter ushr 8).toByte()
        aad[FRAME_BYTES + 7] = counter.toByte()
        aad[FRAME_BYTES + 8] = if (last) 1 else 0
        return aad
    }

    /** Writes a 24-bit big-endian long (the sealed_len field). */
    internal fun writeLong24(target: ByteArray, at: Int, value: Long) {
        target[at] = (value ushr 16).toByte()
        target[at + 1] = (value ushr 8).toByte()
        target[at + 2] = value.toByte()
    }

    /** Reads a 24-bit big-endian long (the sealed_len field). */
    internal fun readLong24(from: ByteArray, at: Int): Long {
        return ((from[at].toLong() and 0xFF) shl 16) or
            ((from[at + 1].toLong() and 0xFF) shl 8) or
            (from[at + 2].toLong() and 0xFF)
    }
}

/** The stego error taxonomy (plan §4.5 + §6.1 risk table). */
sealed class StegoError(description: String) : Exception(description) {

    /** No authenticated CKS2 frame at this position — not a carrier
     * (with this passphrase), or the bits were destroyed. */
    object NotACarrier : StegoError("not a stego carrier (or wrong passphrase)")

    /** The carrier is too small for the payload (pre-flight, §6.3). */
    class CarrierTooSmall(val neededBits: Long, val availableBits: Long) :
        StegoError("carrier too small: need $neededBits bits, have $availableBits")

    /** The embedded stream is short/cut — extraction failed mid-body. */
    object Truncated : StegoError("embedded stream truncated")
}

/**
 * The sealed CKS2 byte stream: FRAME(64) || sealed body chunks. Pure
 * stream IO, mirrors [com.turnypt.core.crypto.CkfChunkedCipher]'s shape
 * so the repository layer reads both the same way.
 */
class Cks2Codec(
    private val aeads: AeadCiphers = AeadCiphers(),
) {

    /**
     * Buffer-based encode (Phase 1 messages are small by definition of
     * the plan: "typed message" vs Phase 2's file picker). Seals once.
     *
     * The payload nonce is DERIVED from the frame nonce (HKDF over the
     * sealed frame) — the same derivation [decode] applies, so the
     * two-key split needs no extra frame field for it.
     */
    fun encode(
        frameKey: ByteArray,
        payloadKey: ByteArray,
        frameNonce: ByteArray,
        payload: ByteArray,
    ): ByteArray {
        // The payload nonce derives from the frame nonce via HKDF — NOT
        // from the sealed frame bytes (those depend on sealed_len, which
        // depends on the nonce... circular). Binding to the frame NONCE
        // alone is deterministic on both sides and still unique per embed
        // (the frame nonce is fresh random per embed).
        val payloadNonce = payloadNonceFromNonce(frameNonce)
        val frame = Cks2.sealFrame(frameKey, frameNonce, sealedLength(payload))
        val body = sealBodyToBuffer(payloadKey, payloadNonce, frame, payload)
        return frame + body
    }

    private fun sealedLength(payload: ByteArray): Long {
        // Sealed body size is deterministic: every sealed chunk is
        // plaintext + 16B tag. The final chunk is the strictly-short one
        // (the CKF1 sentinel rule): an exact multiple gets a zero-length
        // final chunk, so the body is chunks*(CHUNK+16) + 16.
        val chunks = payload.size / Cks2.CHUNK_PLAINTEXT_BYTES
        val remainder = payload.size % Cks2.CHUNK_PLAINTEXT_BYTES
        val fullChunks = chunks + (if (remainder == 0) 0 else 0)
        return chunks.toLong() * (Cks2.CHUNK_PLAINTEXT_BYTES + 16) +
            (remainder + 16).toLong()
    }

    private fun sealBodyToBuffer(
        payloadKey: ByteArray,
        payloadNonce: ByteArray,
        frame: ByteArray,
        payload: ByteArray,
    ): ByteArray {
        val aead = aeads.of(CipherAlgorithm.AES_256_GCM)
        val out = java.io.ByteArrayOutputStream()
        var offset = 0
        var counter = 0L
        while (true) {
            val take = minOf(Cks2.CHUNK_PLAINTEXT_BYTES, payload.size - offset)
            // CKF1 finality rule: the final chunk is strictly SHORTER than
            // the chunk size; an exact multiple gets a zero-length final
            // chunk as the truncation sentinel.
            val last = take < Cks2.CHUNK_PLAINTEXT_BYTES
            val sealed = aead.seal(
                key = payloadKey,
                nonce = Cks2.chunkNonce(payloadNonce, counter),
                plaintext = payload.copyOfRange(offset, offset + take),
                associatedData = Cks2.chunkAad(frame, counter, last),
            )
            out.write(sealed)
            offset += take
            counter++
            if (last) break
            if (offset >= payload.size) {
                // Exact multiple: the empty sentinel chunk closes the body.
                val sentinel = aead.seal(
                    key = payloadKey,
                    nonce = Cks2.chunkNonce(payloadNonce, counter),
                    plaintext = ByteArray(0),
                    associatedData = Cks2.chunkAad(frame, counter, true),
                )
                out.write(sentinel)
                break
            }
        }
        return out.toByteArray()
    }

    /**
     * Decodes a full embedded stream ([embedded]: frame + sealed body,
     * already gathered from the carrier by BitSpread). Returns the
     * authenticated plaintext. Every chunk is verified against its AAD
     * (frame ‖ position ‖ finality) before any byte is returned.
     */
    fun decode(
        frameKey: ByteArray,
        payloadKey: ByteArray,
        embedded: ByteArray,
    ): ByteArray {
        if (embedded.size < Cks2.FRAME_BYTES) throw StegoError.NotACarrier
        val (frameNonce, sealedLength) = Cks2.openFrame(embedded, frameKey, aeads)
        if (sealedLength == 0L || embedded.size < Cks2.FRAME_BYTES + sealedLength) {
            throw StegoError.Truncated
        }
        val frame = embedded.copyOfRange(0, Cks2.FRAME_BYTES)
        val body = embedded.copyOfRange(Cks2.FRAME_BYTES, Cks2.FRAME_BYTES + sealedLength.toInt())
        val aead = aeads.of(CipherAlgorithm.AES_256_GCM)
        val out = java.io.ByteArrayOutputStream()
        var offset = 0
        var counter = 0L
        while (true) {
            val sealedChunk = Cks2.CHUNK_PLAINTEXT_BYTES + 16
            if (offset >= body.size) throw StegoError.Truncated
            val take = minOf(sealedChunk, body.size - offset)
            val plaintextLength = take - 16
            val last = plaintextLength < Cks2.CHUNK_PLAINTEXT_BYTES
            val plaintext = try {
                aead.open(
                    key = payloadKey,
                    nonce = Cks2.chunkNonce(payloadNonceFromNonce(frameNonce), counter),
                    sealed = body.copyOfRange(offset, offset + take),
                    associatedData = Cks2.chunkAad(frame, counter, last),
                )
            } catch (e: AeadFailure) {
                if (e.error == CryptoError.AuthenticationFailed) throw StegoError.NotACarrier
                throw e
            }
            out.write(plaintext)
            offset += take
            counter++
            if (last) break
        }
        if (offset != body.size) throw StegoError.Truncated
        return out.toByteArray()
    }

    /**
     * The payload base nonce: HKDF-SHA256 of the frame nonce, salted by
     * the Cks2 payload info — deterministic on both sides, unique per
     * embed (the frame nonce is fresh random), and NOT circular (it
     * must not depend on the sealed frame bytes, whose plaintext holds
     * the sealed_len that itself depends on this derivation's output
     * length... which is fixed at 12B, but the circularity was real
     * enough to break every round-trip).
     */
    internal fun payloadNonceFromNonce(frameNonce: ByteArray): ByteArray {
        return Hkdf.sha256(frameNonce, ByteArray(0), Cks2.PAYLOAD_INFO_NONCE, 12)
    }

}
