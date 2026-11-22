package com.turnypt.core.stego

/**
 * WavCarrier — S3 of the stego plan (§3.2 table): WAV is the primary
 * audio carrier. Technique: one LSB per PCM sample (16-bit or 24-bit),
 * spread by BitSpread's keyed permutation over the sample index space.
 *
 * The plan's per-format honesty (§3.2 WAV row): "one LSB is 1/65536 of
 * sample amplitude — inaudible at low occupancy in the general case,
 * but the plan treats audibility as measured, not assumed". The S3 exit
 * gate is the null-test (decoded output bit-compared) + AB listening;
 * what is SHIPPED here is the codec — measurement is the release-gate
 * re-verification, not a code artifact.
 *
 * Pure Kotlin, JVM-testable: works on the decoded PCM sample array the
 * Android layer (AudioTrack/WavFile IO or a tiny RIFF parser) hands it.
 * The RIFF parsing itself is [WavParser]'s job — byte-level, no
 * dependencies, per plan §4.1 (in-house, no NDK).
 *
 * Layout mirrored from PngCarrier's shipped design: the salt+frame
 * prefix rides the FIRST samples sequentially (plan §4.2 "read exactly
 * the 64-byte frame from the front"), the sealed body rides the keyed
 * BitSpread positions over the remaining sample space (plan §4.3).
 * One LSB per sample — sample delta is exactly 0 or 1, always (the
 * ±1 trick is unnecessary in the amplitude domain; overwrite-LSB on
 * audio is the standard and the plan's chosen technique says exactly
 * this: "LSB in 16/24-bit PCM samples", not ±1).
 */
object WavCarrier {

    /**
     * Parses the RIFF header of a PCM WAV file: validates the magic,
     * walks the chunk list to `fmt ` and `data`, and reports the PCM
     * width + sample count. No allocation of the data block itself —
     * the caller owns the buffers (plan §5.3 memory discipline).
     */
    data class WavInfo(
        val sampleBits: Int, // 16 or 24
        val sampleCount: Long, // mono-frame count across channels
        val channels: Int,
        val sampleRate: Int,
        val dataOffset: Long, // byte offset of the data payload
        val dataSize: Long, // bytes in the data payload
    )

    fun parse(bytes: ByteArray): WavInfo {
        require(bytes.size >= 44) { "RIFF too short" }
        require(bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()
        ) { "not RIFF" }
        require(bytes[8] == 'W'.code.toByte() && bytes[9] == 'A'.code.toByte() &&
            bytes[10] == 'V'.code.toByte() && bytes[11] == 'E'.code.toByte()
        ) { "not WAVE" }
        var pos = 12
        var sampleBits = -1
        var channels = -1
        var sampleRate = -1
        var dataOffset = -1L
        var dataSize = -1L
        while (pos + 8 <= bytes.size) {
            val chunkId = String(bytes, pos, 4, Charsets.US_ASCII)
            val chunkSize = readInt32LE(bytes, pos + 4)
            when (chunkId) {
                "fmt " -> {
                    require(pos + 8 + chunkSize <= bytes.size) { "fmt chunk overruns" }
                    val format = readInt16LE(bytes, pos + 8)
                    require(format == 1) { "only PCM (format=$format) supported" }
                    channels = readInt16LE(bytes, pos + 10)
                    sampleRate = readInt32LE(bytes, pos + 12)
                    sampleBits = readInt16LE(bytes, pos + 22)
                    require(channels in 1..8) { "bad channel count $channels" }
                    require(sampleRate > 0) { "bad sample rate" }
                }
                "data" -> {
                    dataOffset = (pos + 8).toLong()
                    dataSize = chunkSize.toLong()
                }
            }
            pos += 8 + chunkSize + (chunkSize and 1) // chunks are word-aligned
        }
        require(sampleBits == 16 || sampleBits == 24) { "unsupported PCM width $sampleBits" }
        require(dataOffset >= 0 && dataSize > 0) { "no data chunk" }
        val bytesPerFrame = channels * (sampleBits / 8)
        require(dataSize % bytesPerFrame == 0L) { "data size not frame-aligned" }
        val sampleCount = dataSize / (sampleBits / 8)
        return WavInfo(sampleBits, sampleCount, channels, sampleRate, dataOffset, dataSize)
    }

    /** The insert capacity in BITS = one LSB per PCM sample. */
    fun capacityBits(info: WavInfo): Long = info.sampleCount

    /**
     * Reads sample i (absolute PCM index across channels) as a signed
     * value from the little-endian PCM block.
     */
    fun readSample(bytes: ByteArray, info: WavInfo, sampleIndex: Int): Int {
        val from = info.dataOffset.toInt() + sampleIndex * (info.sampleBits / 8)
        return when (info.sampleBits) {
            16 -> readInt16LE(bytes, from)
            24 -> {
                val b0 = bytes[from].toInt() and 0xFF
                val b1 = bytes[from + 1].toInt() and 0xFF
                val b2 = bytes[from + 2].toInt()
                (b2 shl 24) or (b1 shl 16) or (b0 shl 8) shr 8 // sign-extend
            }
            else -> throw IllegalArgumentException("width ${info.sampleBits}")
        }
    }

    /** Writes sample i back into the PCM block (in place). */
    fun writeSample(bytes: ByteArray, info: WavInfo, sampleIndex: Int, value: Int) {
        val from = info.dataOffset.toInt() + sampleIndex * (info.sampleBits / 8)
        when (info.sampleBits) {
            16 -> {
                bytes[from] = (value and 0xFF).toByte()
                bytes[from + 1] = ((value shr 8) and 0xFF).toByte()
            }
            24 -> {
                bytes[from] = (value and 0xFF).toByte()
                bytes[from + 1] = ((value shr 8) and 0xFF).toByte()
                bytes[from + 2] = ((value shr 16) and 0xFF).toByte()
            }
            else -> throw IllegalArgumentException("width ${info.sampleBits}")
        }
    }

    /** Slot = sample index; bit = the sample's LSB. */
    private fun writeSlot(bytes: ByteArray, info: WavInfo, slot: Long, bit: Int) {
        val sample = readSample(bytes, info, slot.toInt())
        if (sample and 1 != bit) writeSample(bytes, info, slot.toInt(), sample xor 1)
    }

    private fun readSlot(bytes: ByteArray, info: WavInfo, slot: Long): Int =
        readSample(bytes, info, slot.toInt()) and 1

    /** Sequential prefix read — mirrors PngCarrier.readSequential. */
    fun readSequential(bytes: ByteArray, info: WavInfo, byteCount: Int): ByteArray {
        val capacity = capacityBits(info)
        if (byteCount.toLong() * 8 > capacity) {
            throw StegoError.CarrierTooSmall(byteCount.toLong() * 8, capacity)
        }
        val out = ByteArray(byteCount)
        for (byteIndex in 0 until byteCount) {
            var value = 0
            for (bitIndex in 0 until 8) {
                value = (value shl 1) or readSlot(bytes, info, (byteIndex * 8 + bitIndex).toLong())
            }
            out[byteIndex] = value.toByte()
        }
        return out
    }

    /** Keyed body-zone read, offset past the sequential prefix. */
    fun extractAfter(
        bytes: ByteArray,
        info: WavInfo,
        prefixBytes: Int,
        byteCount: Int,
        spreadKey: ByteArray,
    ): ByteArray {
        val prefixBits = prefixBytes.toLong() * 8
        val capacity = capacityBits(info)
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
                value = (value shl 1) or readSlot(bytes, info, slot)
            }
            out[byteIndex] = value.toByte()
        }
        return out
    }

    /**
     * Two-zone embed (same layout as PngCarrier's shipped design):
     * sequential prefix, keyed body. IN PLACE on the PCM block.
     */
    fun embedPrefixed(
        bytes: ByteArray,
        info: WavInfo,
        prefix: ByteArray,
        body: ByteArray,
        spreadKey: ByteArray,
    ) {
        val capacity = capacityBits(info)
        val prefixBits = prefix.size.toLong() * 8
        if (prefixBits + body.size * 8L > capacity) {
            throw StegoError.CarrierTooSmall(prefixBits + body.size * 8L, capacity)
        }
        for (byteIndex in prefix.indices) {
            val b = prefix[byteIndex].toInt() and 0xFF
            for (bitIndex in 0 until 8) {
                writeSlot(bytes, info, (byteIndex * 8 + bitIndex).toLong(), (b shr (7 - bitIndex)) and 1)
            }
        }
        val bodyCapacity = capacity - prefixBits
        val bodyPositions = BitSpread.positions(body.size * 8, bodyCapacity, spreadKey)
        for (byteIndex in body.indices) {
            val b = body[byteIndex].toInt() and 0xFF
            for (bitIndex in 0 until 8) {
                writeSlot(
                    bytes, info,
                    bodyPositions[byteIndex * 8 + bitIndex] + prefixBits,
                    (b shr (7 - bitIndex)) and 1,
                )
            }
        }
    }

    private fun readInt16LE(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun readInt32LE(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)
}

/**
 * The WAV twin of PngStegoCipher: seal with Cks2, two-zone spread via
 * WavCarrier. Same facade shape so the repository layer (S4) treats
 * both carriers uniformly.
 */
class WavStegoCipher(
    private val kdfParams: com.turnypt.core.crypto.Argon2Params,
    private val derivator: com.turnypt.core.crypto.Argon2KeyDerivator =
        com.turnypt.core.crypto.Argon2KeyDerivator(),
    private val codec: Cks2Codec = Cks2Codec(),
) {

    /** Embeds [payload] into the PCM block IN PLACE; returns [bytes]. */
    fun embed(
        bytes: ByteArray,
        info: WavCarrier.WavInfo,
        passphrase: CharArray,
        payload: ByteArray,
    ): ByteArray {
        val material = BitSpread.freshMaterial()
        val payloadNonce = codec.payloadNonceFromNonce(material.frameNonce)
        val (frameKey, payloadKey) = Cks2.deriveKeys(
            passphrase, material.salt, material.frameNonce, payloadNonce,
            kdfParams, derivator,
        )
        val embedded = codec.encode(frameKey, payloadKey, material.frameNonce, payload)
        val spreadKey = BitSpread.spreadKey(payloadKey)
        val prefix = material.salt + embedded.copyOfRange(0, Cks2.FRAME_BYTES)
        val body = embedded.copyOfRange(Cks2.FRAME_BYTES, embedded.size)
        WavCarrier.embedPrefixed(bytes, info, prefix, body, spreadKey)
        return bytes
    }

    /** Two-step boot extract (same as PngStegoCipher). */
    fun extract(
        bytes: ByteArray,
        info: WavCarrier.WavInfo,
        passphrase: CharArray,
    ): ByteArray {
        val prefixBytes = 16 + Cks2.FRAME_BYTES
        val prefix = WavCarrier.readSequential(bytes, info, prefixBytes)
        val salt = prefix.copyOfRange(0, 16)
        val frame = prefix.copyOfRange(16, prefixBytes)
        val frameNonce = frame.copyOfRange(0, Cks2.FRAME_NONCE_BYTES)
        val payloadNonce = codec.payloadNonceFromNonce(frameNonce)
        val (frameKey, payloadKey) = Cks2.deriveKeys(
            passphrase, salt, frameNonce, payloadNonce, kdfParams, derivator,
        )
        val (openedNonce, sealedLength) = Cks2.openFrame(frame, frameKey)
        check(openedNonce.contentEquals(frameNonce))
        val spreadKey = BitSpread.spreadKey(payloadKey)
        val body = WavCarrier.extractAfter(
            bytes, info, prefixBytes, sealedLength.toInt(), spreadKey,
        )
        return codec.decode(frameKey, payloadKey, frame + body)
    }
}
