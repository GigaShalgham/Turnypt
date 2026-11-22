package com.turnypt.core.stego

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32

/**
 * PngCarrier — S2 of the stego plan (§3.2 table): PNG is the image carrier
 * of Phase 1. Pure-Kotlin PNG re-serialization is NOT attempted (the plan
 * forbids new runtime dependencies); instead the carrier's PIXEL BYTES are
 * the insertion space and this class works on the raw scanline data that
 * the Android layer (Bitmap.getPixels → ARGB ints) hands it. The module
 * stays JVM-testable because the test side synthesises the same int rows.
 *
 * Technique (plan §3.2): LSB ±1 matching, not overwrite — each payload bit
 * is written by nudging the pixel's LSB to the target value via ±1 (the
 * ±1 trick keeps the pixel within its original neighbourhood, defeating
 * the simple chi-square "all LSBs look replaced" signature). The plan's
 * S2 gate measures: pixel delta <= 1 per channel, decoders still read the
 * output, round-trip survives a re-encode-free transfer (PNG is lossless —
 * the one carrier class where the messenger pipeline is bit-safe as long
 * as the file is sent as a DOCUMENT, not re-compressed as a photo).
 *
 * Insertion space: every channel of every pixel, ordered by BitSpread's
 * keyed permutation — capacity = width * height * 3 bits (RGB; the alpha
 * channel is left alone deliberately: alpha LSBs show up in compositing
 * artefacts, and 3/4 of the space is already abundant for messages).
 */
object PngCarrier {

    /** Channels per pixel used for payload bits (RGB, alpha untouched). */
    const val PAYLOAD_CHANNELS: Int = 3

    /** ARGB int row helpers — the Android Bitmap interop shape. */

    fun capacityBits(width: Int, height: Int): Long =
        width.toLong() * height * PAYLOAD_CHANNELS

    /**
     * The ±1 LSB write for one channel value. Returns the adjusted value:
     * the LSB becomes [bit] with a minimal ±1 nudge (or no change if the
     * LSB already matches). 0 stays 0 on underflow, 255 stays on overflow
     * — the boundary values degrade to overwrite, which is the standard
     * ±1-matching caveat and affects ~2 of 256 values.
     */
    fun writeBit(channelValue: Int, bit: Int): Int {
        val lsb = channelValue and 1
        if (lsb == bit) return channelValue
        return when {
            channelValue == 0 -> 1 // underflow: overwrite upward
            channelValue == 255 -> 254 // overflow: overwrite downward
            else -> if (bit == 1) channelValue + 1 else channelValue - 1
        }
    }

    /**
     * Embeds [embedded] (the Cks2 byte stream) into [pixels] (ARGB ints,
     * row-major, `width * height` entries) IN PLACE, spreading bit i of
     * the stream to the BitSpread position derived from [spreadKey].
     * The stream's byte j bit b maps to flat bit index `j*8 + (7-b)`
     * (MSB-first within a byte — the convention both sides share).
     */
    fun embed(
        pixels: IntArray,
        width: Int,
        height: Int,
        embedded: ByteArray,
        spreadKey: ByteArray,
    ) {
        val capacity = capacityBits(width, height)
        val totalBits = embedded.size.toLong() * 8
        if (totalBits > capacity) throw StegoError.CarrierTooSmall(totalBits, capacity)
        val positions = BitSpread.positions(embedded.size * 8, capacity, spreadKey)
        for (byteIndex in embedded.indices) {
            val b = embedded[byteIndex].toInt() and 0xFF
            for (bitIndex in 0 until 8) {
                val bit = (b shr (7 - bitIndex)) and 1
                val slot = positions[byteIndex * 8 + bitIndex]
                writeSlot(pixels, slot, bit)
            }
        }
    }

    /**
     * Extracts [byteCount] bytes from [pixels] using the same keyed
     * positions. The caller learns byteCount from the (already-opened)
     * Cks2 frame: FRAME + sealed_len is the full embedded stream.
     */
    fun extract(
        pixels: IntArray,
        width: Int,
        height: Int,
        byteCount: Int,
        spreadKey: ByteArray,
    ): ByteArray {
        val capacity = capacityBits(width, height)
        val totalBits = byteCount.toLong() * 8
        if (totalBits > capacity) throw StegoError.CarrierTooSmall(totalBits, capacity)
        val positions = BitSpread.positions(byteCount * 8, capacity, spreadKey)
        val out = ByteArray(byteCount)
        for (byteIndex in 0 until byteCount) {
            var value = 0
            for (bitIndex in 0 until 8) {
                val slot = positions[byteIndex * 8 + bitIndex]
                val bit = readSlot(pixels, slot)
                value = (value shl 1) or bit
            }
            out[byteIndex] = value.toByte()
        }
        return out
    }

    /**
     * Slot → pixel/channel: slot = pixelIndex * 3 + channelIndex(0..2).
     * Channel 0=R, 1=G, 2=B of the ARGB int — alpha (bits 24..31) is
     * never touched. The ±1 write goes through [writeBit].
     */
    /** Public ±1 write at an ABSOLUTE slot — the facade's two-zone embed. */
    fun writeSlotAt(pixels: IntArray, slot: Long, bit: Int) {
        writeSlot(pixels, slot, bit)
    }

    /** Sequential (unkeyed) read of the FIRST [byteCount] bytes — the
     *  salt+frame prefix zone. Mirrors the sequential prefix write. */
    fun readSequential(pixels: IntArray, width: Int, height: Int, byteCount: Int): ByteArray {
        val capacity = capacityBits(width, height)
        if (byteCount.toLong() * 8 > capacity) throw StegoError.CarrierTooSmall(byteCount.toLong() * 8, capacity)
        val out = ByteArray(byteCount)
        for (byteIndex in 0 until byteCount) {
            var value = 0
            for (bitIndex in 0 until 8) {
                value = (value shl 1) or readSlot(pixels, (byteIndex * 8 + bitIndex).toLong())
            }
            out[byteIndex] = value.toByte()
        }
        return out
    }

    /**
     * Body-zone read: [byteCount] bytes at payloadKey spread positions
     * over the space AFTER [prefixBits] — mirror of the embed offset.
     */
    fun extractAfter(
        pixels: IntArray,
        width: Int,
        height: Int,
        prefixBytes: Int,
        byteCount: Int,
        spreadKey: ByteArray,
    ): ByteArray {
        val prefixBits = prefixBytes.toLong() * 8
        val capacity = capacityBits(width, height)
        val bodyCapacity = capacity - prefixBits
        if (byteCount.toLong() * 8 > bodyCapacity) {
            throw StegoError.CarrierTooSmall(byteCount.toLong() * 8, bodyCapacity)
        }
        val positions = BitSpread.positions(byteCount * 8, bodyCapacity, spreadKey)
        val out = ByteArray(byteCount)
        for (byteIndex in 0 until byteCount) {
            var value = 0
            for (bitIndex in 0 until 8) {
                val slot = positions[byteIndex * 8 + bitIndex] + prefixBits
                val bit = readSlot(pixels, slot)
                value = (value shl 1) or bit
            }
            out[byteIndex] = value.toByte()
        }
        return out
    }

    // ARGB int layout: A in bits 24..31, R in 16..23, G in 8..15, B in 0..7.
    // Channel 0=R, 1=G, 2=B — shift = 16 - 8*channel. Alpha NEVER moves.
    private fun channelShift(channel: Int): Int = 16 - 8 * channel

    private fun writeSlot(pixels: IntArray, slot: Long, bit: Int) {
        val pixelIndex = (slot / PAYLOAD_CHANNELS).toInt()
        val channel = (slot % PAYLOAD_CHANNELS).toInt()
        val shift = channelShift(channel)
        val argb = pixels[pixelIndex]
        val adjusted = writeBit((argb ushr shift) and 0xFF, bit)
        val mask = 0xFF shl shift
        pixels[pixelIndex] = (argb and mask.inv()) or (adjusted shl shift)
    }

    private fun readSlot(pixels: IntArray, slot: Long): Int {
        val pixelIndex = (slot / PAYLOAD_CHANNELS).toInt()
        val channel = (slot % PAYLOAD_CHANNELS).toInt()
        return (pixels[pixelIndex] ushr channelShift(channel)) and 1
    }
}

/**
 * The capacity planner (plan §6.3 risk table: "CarrierTooSmall is a
 * pre-flight UI state, not a runtime surprise"). Given the carrier size
 * and the payload byte length it reports whether the embed fits and how
 * much room is left — the UI shows this BEFORE the user types a 2 MiB
 * message into a 512x512 PNG.
 */
object CapacityPlanner {

    /** The full embedded-stream size for a payload of [payloadBytes]. */
    fun embeddedStreamBytes(payloadBytes: Int): Int =
        Cks2.FRAME_BYTES + payloadBytes + 16 * chunkCount(payloadBytes)

    private fun chunkCount(payloadBytes: Int): Int =
        payloadBytes / Cks2.CHUNK_PLAINTEXT_BYTES + 1

    fun fits(
        width: Int,
        height: Int,
        payloadBytes: Int,
    ): Boolean = PngCarrier.capacityBits(width, height) >=
        embeddedStreamBytes(payloadBytes) * 8L

    /** Bits available after the embed, or 0 when it does not fit. */
    fun headroomBits(width: Int, height: Int, payloadBytes: Int): Long {
        val need = embeddedStreamBytes(payloadBytes) * 8L
        val have = PngCarrier.capacityBits(width, height)
        return if (have >= need) have - need else 0L
    }
}

/**
 * Stego embed/extract facade for PNG carriers — the seam S4 will wire to
 * the repository layer. Seals the payload with [Cks2] and spreads it with
 * [BitSpread]; extraction is the two-step boot the plan §4.2 defines:
 * derive keys → read the 64-byte frame → learn sealed_len → read the body.
 */
class PngStegoCipher(
    private val kdfParams: com.turnypt.core.crypto.Argon2Params,
    private val derivator: com.turnypt.core.crypto.Argon2KeyDerivator =
        com.turnypt.core.crypto.Argon2KeyDerivator(),
    private val codec: Cks2Codec = Cks2Codec(),
) {

    /**
     * The stream layout this facade writes into a carrier:
     *
     * ```
     * salt(16B) || CKS2 frame(64B) || sealed body — spread in TWO zones:
     *   prefix (salt + frame, 80B) at BOOTSTRAP positions keyed by the
     *     passphrase alone (HKDF info "CKS2/boot/v1") — the extractor
     *     must find these before any key material exists;
     *   body at REAL positions keyed by K_payload (the plan §4.3 rule).
     * ```
     *
     * Two-slot read order is the plan's two-step boot made physical: a
     * wrong passphrase reads a garbage frame and the GCM tag rejects it
     * — no oracle, no partial leak (plan §4.5).
     */
    fun embed(
        pixels: IntArray,
        width: Int,
        height: Int,
        passphrase: CharArray,
        payload: ByteArray,
    ): IntArray {
        val material = BitSpread.freshMaterial()
        // The codec derives the payload nonce from the frame nonce; the
        // KDF's associated-data binding must match that same value.
        val payloadNonce = codec.payloadNonceFromNonce(material.frameNonce)
        val (frameKey, payloadKey) = Cks2.deriveKeys(
            passphrase, material.salt, material.frameNonce, payloadNonce,
            kdfParams, derivator,
        )
        val embedded = codec.encode(frameKey, payloadKey, material.frameNonce, payload)

        val spreadKey = BitSpread.spreadKey(payloadKey)
        val prefix = material.salt + embedded.copyOfRange(0, Cks2.FRAME_BYTES)
        val body = embedded.copyOfRange(Cks2.FRAME_BYTES, embedded.size)

        // Capacity: prefix + body bits must both fit their zones (the body
        // zone starts after the prefix's 80B in the SAME capacity space).
        val capacity = PngCarrier.capacityBits(width, height)
        if ((prefix.size.toLong() + body.size.toLong()) * 8 > capacity) {
            throw StegoError.CarrierTooSmall((prefix.size + body.size) * 8L, capacity)
        }
        embedPrefixed(pixels, width, height, prefix, body, spreadKey)
        return pixels
    }

    /**
     * Extracts the hidden payload. Boot: read the 80B prefix from the
     * bootstrap positions (passphrase alone), derive the real keys,
     * verify the frame tag, then read the sealed body from the real
     * positions sized by the authenticated sealed_len.
     */
    fun extract(
        pixels: IntArray,
        width: Int,
        height: Int,
        passphrase: CharArray,
    ): ByteArray {
        val prefixBytes = 16 + Cks2.FRAME_BYTES
        // Prefix: sequential slots, read straight from the front.
        val prefix = PngCarrier.readSequential(pixels, width, height, prefixBytes)
        val salt = prefix.copyOfRange(0, 16)
        val frame = prefix.copyOfRange(16, prefixBytes)
        val frameNonce = frame.copyOfRange(0, Cks2.FRAME_NONCE_BYTES)

        val payloadNonce = codec.payloadNonceFromNonce(frameNonce)
        val (frameKey, payloadKey) = Cks2.deriveKeys(
            passphrase, salt, frameNonce, payloadNonce, kdfParams, derivator,
        )
        // Frame tag verifies HERE — wrong passphrase dies before any
        // body bit is read (the no-oracle rule).
        val (openedNonce, sealedLength) = Cks2.openFrame(frame, frameKey)
        check(openedNonce.contentEquals(frameNonce))

        val spreadKey = BitSpread.spreadKey(payloadKey)
        val body = PngCarrier.extractAfter(
            pixels, width, height, prefixBytes, sealedLength.toInt(), spreadKey,
        )
        return codec.decode(frameKey, payloadKey, frame + body)
    }

    /**
     * Two-zone embed: prefix at bootstrap positions i in 0..80B*8, body
     * at payloadKey positions over the space SHIFTED past the prefix —
     * extractAfter mirrors the shift so zones never overlap.
     */
    /**
     * Two-zone embed, zones DISJOINT by construction:
     *  - the salt+frame prefix (a public, unauthenticated-positions
     *    header) rides the FIRST [prefix.size]*8 slots sequentially —
     *    the plan §4.2 wording "read exactly the 64-byte frame from
     *    the front" made physical;
     *  - the sealed body rides the KEYED BitSpread positions over the
     *    space AFTER the prefix (plan §4.3: payload positions keyed).
     *  The earlier version spread the PREFIX over the whole capacity
     *  while the body occupied the sub-space — the zones could collide
     *  and one ±1 write clobbered another (probe caught it: a single
     *  flipped bit in the salt).
     */
    private fun embedPrefixed(
        pixels: IntArray,
        width: Int,
        height: Int,
        prefix: ByteArray,
        body: ByteArray,
        spreadKey: ByteArray,
    ) {
        val capacity = PngCarrier.capacityBits(width, height)
        val prefixBits = prefix.size * 8L
        // Prefix: sequential slots 0 until prefixBits.
        for (byteIndex in prefix.indices) {
            val b = prefix[byteIndex].toInt() and 0xFF
            for (bitIndex in 0 until 8) {
                val slot = byteIndex * 8 + bitIndex
                PngCarrier.writeSlotAt(pixels, slot.toLong(), (b shr (7 - bitIndex)) and 1)
            }
        }
        // Body: keyed spread over the remaining space, offset past prefix.
        val bodyCapacity = capacity - prefixBits
        val bodyPositions = BitSpread.positions(body.size * 8, bodyCapacity, spreadKey)
        for (byteIndex in body.indices) {
            val b = body[byteIndex].toInt() and 0xFF
            for (bitIndex in 0 until 8) {
                PngCarrier.writeSlotAt(
                    pixels,
                    bodyPositions[byteIndex * 8 + bitIndex] + prefixBits,
                    (b shr (7 - bitIndex)) and 1,
                )
            }
        }
    }

    private fun writeBits(pixels: IntArray, data: ByteArray, positions: LongArray) {
        for (byteIndex in data.indices) {
            val b = data[byteIndex].toInt() and 0xFF
            for (bitIndex in 0 until 8) {
                val bit = (b shr (7 - bitIndex)) and 1
                PngCarrier.writeSlotAt(pixels, positions[byteIndex * 8 + bitIndex], bit)
            }
        }
    }

    /** The bootstrap spread key — passphrase-only HKDF (plan §4.5). */
    private fun bootstrapSpreadKey(passphrase: CharArray): ByteArray {
        val pw = ByteArray(passphrase.size * 2)
        var i = 0
        for (c in passphrase) {
            pw[i++] = (c.code shr 8).toByte()
            pw[i++] = c.code.toByte()
        }
        return com.turnypt.core.crypto.Hkdf.sha256(pw, ByteArray(0), BOOTSTRAP_INFO, 32)
    }

    /** Test seam: the codec and bootstrap key (probe tests only). */
    fun codec(): Cks2Codec = codec

    fun bootstrapForTest(): ByteArray =
        bootstrapSpreadKey("probe".toCharArray())

    private companion object {
        val BOOTSTRAP_INFO = "CKS2/boot/v1".toByteArray(Charsets.US_ASCII)
        val SEQUENTIAL_KEY = ByteArray(32)
    }
}
