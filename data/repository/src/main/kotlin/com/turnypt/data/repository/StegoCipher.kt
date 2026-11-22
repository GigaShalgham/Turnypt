package com.turnypt.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.turnypt.core.common.DispatcherProvider
import com.turnypt.core.crypto.ProtocolRandom
import com.turnypt.core.crypto.Argon2KeyDerivator
import com.turnypt.core.crypto.Argon2Params
import com.turnypt.core.stego.PngStegoCipher
import com.turnypt.core.stego.StegoError
import com.turnypt.core.stego.WavCarrier
import com.turnypt.core.stego.WavStegoCipher
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.withContext

/** Outcome of embedding a message into a carrier (stego plan §5.1). */
sealed interface StegoEmbedOutcome {
    /** The carrier bytes with the message embedded were written to target. */
    data class Embedded(
        val carrierKind: StegoCarrierKind,
        val messageBytes: Int,
        val carrierBytes: Long,
    ) : StegoEmbedOutcome

    /** The carrier cannot hold the stream (capacity planner fired). */
    data class CarrierTooSmall(
        val carrierKind: StegoCarrierKind,
        val neededBytes: Long,
        val availableBytes: Long,
    ) : StegoEmbedOutcome

    /** The bytes are not a supported carrier format. */
    data object NotACarrier : StegoEmbedOutcome

    data class Failed(val error: Throwable) : StegoEmbedOutcome
}

/** Outcome of extracting a message from a carrier (stego plan §5.1). */
sealed interface StegoExtractOutcome {
    /** The hidden message was recovered and authenticated. */
    data class Extracted(val message: ByteArray) : StegoExtractOutcome

    /** No CKS2 frame verified under this passphrase (or the bytes are not a carrier). */
    data object NotACarrier : StegoExtractOutcome

    data class Failed(val error: Throwable) : StegoExtractOutcome
}

/** The carriers Phase 1 ships (plan §3.2 matrix; MP4 is gated on S0). */
enum class StegoCarrierKind(val label: String) {
    PNG("PNG image"),
    WAV("WAV audio"),
}

/**
 * StegoCipher — the stego twin of [MediaCipher] (stego plan §5.1):
 * same shape, same outcome discipline, and passphrase-only key model
 * per plan §4.4 — stego containers consult NO contact, NO era, NO
 * session. The StegoScreen contact picker is UX sugar for remembering
 * who the carrier is for; the passphrase travels out-of-band (the
 * plan's §4.4 decision and the alpha.3 user note reconciled: the screen
 * keeps the ecosystem look, the container stays self-contained).
 *
 * Android-bearing by necessity: PNG pixels come from BitmapFactory (the
 * plan §3.2 PNG row relies on the platform's lossless decode), and the
 * output re-encodes via Bitmap.compress(PNG, 100) — lossless, so the
 * ±1 nudged pixels survive their own re-encode (that IS the PNG gate:
 * "decoders still read the output").
 */
class StegoCipher(
    private val random: ProtocolRandom,
    private val dispatchers: DispatcherProvider,
    private val kdfParams: Argon2Params,
) {

    private val pngCipher get() = PngStegoCipher(kdfParams)
    private val wavCipher get() = WavStegoCipher(kdfParams)

    /**
     * Embeds [message] into the carrier read from [source] and writes
     * the carrier WITH the hidden stream to [target] — same file shape,
     * PNG re-encoded losslessly, WAV byte-identical except the LSBs.
     */
    suspend fun embed(
        source: InputStream,
        target: OutputStream,
        message: ByteArray,
        passphrase: CharArray,
    ): StegoEmbedOutcome = withContext(dispatchers.io) {
        val bytes = try {
            source.readBytes()
        } catch (e: Exception) {
            return@withContext StegoEmbedOutcome.Failed(e)
        }
        try {
            when (sniffCarrier(bytes)) {
                StegoCarrierKind.PNG -> {
                    val out = embedPng(bytes, message, passphrase)
                        ?: return@withContext passThrough(bytes, target)
                    out.second.compress(Bitmap.CompressFormat.PNG, 100, target)
                    StegoEmbedOutcome.Embedded(
                        StegoCarrierKind.PNG,
                        message.size,
                        bytes.size.toLong(),
                    )
                }
                StegoCarrierKind.WAV -> {
                    val wav = embedWav(bytes, message, passphrase)
                        ?: return@withContext passThrough(bytes, target)
                    target.write(wav)
                    StegoEmbedOutcome.Embedded(
                        StegoCarrierKind.WAV,
                        message.size,
                        bytes.size.toLong(),
                    )
                }
                null -> passThrough(bytes, target)
            }
        } catch (e: StegoError.CarrierTooSmall) {
            passThrough(bytes, target).let {
                StegoEmbedOutcome.CarrierTooSmall(
                    sniffCarrier(bytes) ?: StegoCarrierKind.PNG,
                    e.neededBits / 8,
                    e.availableBits / 8,
                )
            }
        } catch (e: IllegalArgumentException) {
            passThrough(bytes, target)
        } catch (e: Exception) {
            StegoEmbedOutcome.Failed(e)
        }
    }

    /** Writes the unchanged carrier through (honest no-op) — NotACarrier. */
    private suspend fun passThrough(
        bytes: ByteArray,
        target: OutputStream,
    ): StegoEmbedOutcome = withContext(dispatchers.io) {
        target.write(bytes)
        StegoEmbedOutcome.NotACarrier
    }

    /**
     * Extracts the hidden message from a carrier under [passphrase].
     * A wrong passphrase fails at the frame's GCM tag — NotACarrier,
     * no oracle (plan §4.5).
     */
    suspend fun extract(
        source: InputStream,
        passphrase: CharArray,
    ): StegoExtractOutcome = withContext(dispatchers.io) {
        val bytes = try {
            source.readBytes()
        } catch (e: Exception) {
            return@withContext StegoExtractOutcome.Failed(e)
        }
        when (sniffCarrier(bytes)) {
            StegoCarrierKind.PNG -> {
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@withContext StegoExtractOutcome.NotACarrier
                val w = bitmap.width
                val h = bitmap.height
                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                try {
                    StegoExtractOutcome.Extracted(pngCipher.extract(pixels, w, h, passphrase))
                } catch (e: StegoError.NotACarrier) {
                    StegoExtractOutcome.NotACarrier
                } catch (e: StegoError.CarrierTooSmall) {
                    StegoExtractOutcome.NotACarrier
                } catch (e: StegoError.Truncated) {
                    StegoExtractOutcome.NotACarrier
                } catch (e: Exception) {
                    StegoExtractOutcome.Failed(e)
                }
            }
            StegoCarrierKind.WAV -> {
                try {
                    val info = WavCarrier.parse(bytes)
                    StegoExtractOutcome.Extracted(wavCipher.extract(bytes, info, passphrase))
                } catch (e: IllegalArgumentException) {
                    StegoExtractOutcome.NotACarrier
                } catch (e: StegoError.NotACarrier) {
                    StegoExtractOutcome.NotACarrier
                } catch (e: StegoError.CarrierTooSmall) {
                    StegoExtractOutcome.NotACarrier
                } catch (e: StegoError.Truncated) {
                    StegoExtractOutcome.NotACarrier
                } catch (e: Exception) {
                    StegoExtractOutcome.Failed(e)
                }
            }
            null -> StegoExtractOutcome.NotACarrier
        }
    }

    /**
     * Formats sniffed by signature (plan §4.5: no magic bytes are
     * ADDED by us — but the carrier's own native signature is what
     * routes it). PNG: 8-byte signature; WAV: "RIFF....WAVE".
     */
    private fun sniffCarrier(bytes: ByteArray): StegoCarrierKind? = when {
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte() -> StegoCarrierKind.PNG
        bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'A'.code.toByte() &&
            bytes[10] == 'V'.code.toByte() && bytes[11] == 'E'.code.toByte() -> StegoCarrierKind.WAV
        else -> null
    }

    /**
     * PNG path: decode → plan capacity (plan §6.3: CarrierTooSmall is a
     * PRE-FLIGHT state) → ±1 embed → re-encode. Returns null when the
     * pre-flight fails (caller writes the original bytes unchanged and
     * reports the reason via the capacity outcome).
     */
    private fun embedPng(
        bytes: ByteArray,
        message: ByteArray,
        passphrase: CharArray,
    ): Pair<Unit, Bitmap>? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val w = bitmap.width
        val h = bitmap.height
        // Pre-flight with the planner (16B salt + 64B frame ride too).
        val streamBytes = 16 + 64 + message.size + 16L * (message.size / 65536 + 1)
        if (streamBytes * 8 > w.toLong() * h * 3) {
            throw StegoError.CarrierTooSmall(streamBytes * 8, w.toLong() * h * 3)
        }
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        pngCipher.embed(pixels, w, h, passphrase, message)
        val out = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        return Unit to out
    }

    private fun embedWav(
        bytes: ByteArray,
        message: ByteArray,
        passphrase: CharArray,
    ): ByteArray? = try {
        val info = WavCarrier.parse(bytes)
        val streamBytes = 16 + 64 + message.size + 16L * (message.size / 65536 + 1)
        if (streamBytes * 8 > WavCarrier.capacityBits(info)) {
            throw StegoError.CarrierTooSmall(streamBytes * 8, WavCarrier.capacityBits(info))
        }
        val mutated = bytes.copyOf()
        wavCipher.embed(mutated, info, passphrase, message)
        mutated
    } catch (e: IllegalArgumentException) {
        null
    }

    /** Fresh salt material comes from the ciphers' own freshMaterial();
     *  [random] is held for future nonce diversification at the facade tier. */
    @Suppress("unused")
    private val reservedRandom: ProtocolRandom = random
}

/** Tests + future callers: the KDF cost the facade seals with. */
object StegoCipherDefaults {
    /** The BALANCED Argon2 profile (64 MiB / 3 passes) — the app default. */
    val KDF_PARAMS: Argon2Params = Argon2Params(memoryExponent = 16, iterations = 3, parallelism = 1)

    fun derivator(): Argon2KeyDerivator = Argon2KeyDerivator()
}
