package com.turnypt.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.turnypt.core.common.ContactId
import com.turnypt.core.common.DispatcherProvider
import com.turnypt.core.crypto.ProtocolRandom
import com.turnypt.core.crypto.Argon2KeyDerivator
import com.turnypt.core.crypto.Argon2Params
import com.turnypt.core.stego.AnyCarrier
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

    /**
     * The trailer names a contact era that is not unlocked on this device
     * (ecosystem path): the UI shows the SAME unlock card every other
     * Turnypt flow uses and retries after a successful unlock.
     */
    data class NeedsContactUnlock(val contactId: ContactId, val contactName: String) :
        StegoExtractOutcome

    /** No CKS2 frame verified (or the bytes are not a carrier). */
    data object NotACarrier : StegoExtractOutcome

    data class Failed(val error: Throwable) : StegoExtractOutcome
}

/**
 * The carriers v1.6.0 ships: the PNG/WAV pixel-sample carriers of the
 * Phase 1 plan, plus ANY — the 2026-11-22 user decree carrier that
 * appends the sealed Cks2 trailer after the original bytes of EVERY
 * file, every format, every extension.
 */
enum class StegoCarrierKind(val label: String) {
    PNG("PNG image"),
    WAV("WAV audio"),
    ANY("Any file"),
}

/**
 * StegoCipher — the stego facade (stego plan §5.1), v1.6.0-beta.7:
 *
 *  - ANY (default for every file): EOF-append through [AnyCarrier] —
 *    the sealed message rides a CKE1 trailer after the untouched
 *    original bytes, so every format and extension works. Two key
 *    models: PASSPHRASE-ONLY (plan §4.4, self-contained) or the
 *    ECOSYSTEM model (the user decree "member of the Turnypt
 *    ecosystem"): keys derive by HKDF over the contact's ACTIVE era
 *    session key, the trailer records the era's public keyId, and the
 *    receiver resolves WHICH CONTACT the file was hidden for — the
 *    same unlock card, timeout and era-rotation discipline as CK1
 *    text and CKF1 files.
 *  - PNG / WAV stay on their pixel/sample carriers when the SNIFFED
 *    signature matches AND the "deep" pixel path is selected — the
 *    plan's two-zone BitSpread machinery, unchanged.
 *
 * The screen routes per file: anything that is not a sniffable PNG or
 * WAV goes to ANY, so NO file is ever rejected by format.
 */
class StegoCipher(
    private val random: ProtocolRandom,
    private val dispatchers: DispatcherProvider,
    private val kdfParams: Argon2Params,
    private val contacts: ContactRepository,
    private val sessionKeys: SessionKeyRepository,
    private val sessions: com.turnypt.core.sessions.KeySessionManager,
) {

    private val pngCipher get() = PngStegoCipher(kdfParams)
    private val wavCipher get() = WavStegoCipher(kdfParams)

    /**
     * Embeds [message] into the carrier read from [source], writing the
     * carrier WITH the hidden stream to [target].
     *
     * Key routing: [contactId] non-null selects the ECOSYSTEM model (the
     * era must be unlocked on this device — the caller checked). Null
     * selects PASSPHRASE-ONLY. The PNG/WAV pixel paths always use the
     * passphrase model (the trailer has no room in pixel space; the
     * ecosystem route works through the ANY path).
     */
    suspend fun embed(
        source: InputStream,
        target: OutputStream,
        message: ByteArray,
        passphrase: CharArray,
        contactId: ContactId? = null,
        deepPixelPath: Boolean = true,
    ): StegoEmbedOutcome = withContext(dispatchers.io) {
        val bytes = try {
            source.readBytes()
        } catch (e: Exception) {
            return@withContext StegoEmbedOutcome.Failed(e)
        }
        val sniffed = sniffCarrier(bytes)
        try {
            if (deepPixelPath && sniffed == StegoCarrierKind.PNG) {
                val out = embedPng(bytes, message, passphrase)
                    ?: return@withContext passThrough(bytes, target)
                out.second.compress(Bitmap.CompressFormat.PNG, 100, target)
                StegoEmbedOutcome.Embedded(
                    StegoCarrierKind.PNG,
                    message.size,
                    bytes.size.toLong(),
                )
            } else if (deepPixelPath && sniffed == StegoCarrierKind.WAV) {
                val wav = embedWav(bytes, message, passphrase)
                    ?: return@withContext passThrough(bytes, target)
                target.write(wav)
                StegoEmbedOutcome.Embedded(
                    StegoCarrierKind.WAV,
                    message.size,
                    bytes.size.toLong(),
                )
            } else {
                // ANY — every other file (and the ecosystem route):
                // EOF-append, original bytes streamed through untouched.
                if (contactId != null) {
                    embedAnyEcosystem(bytes, target, message, contactId)
                } else {
                    // The stream source is re-wrapped from the buffer.
                    AnyCarrier.append(
                        java.io.ByteArrayInputStream(bytes), target,
                        message, passphrase, null, null, kdfParams,
                    )
                    StegoEmbedOutcome.Embedded(
                        StegoCarrierKind.ANY,
                        message.size,
                        bytes.size.toLong(),
                    )
                }
            }
        } catch (e: StegoError.CarrierTooSmall) {
            passThrough(bytes, target).let {
                StegoEmbedOutcome.CarrierTooSmall(
                    sniffed ?: StegoCarrierKind.PNG,
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

    /**
     * Extracts the hidden message from the carrier under [passphrase]
     * (passphrase-only model) or the contact ecosystem (null passphrase
     * with a CKE1 trailer whose keyHint names an era). A wrong
     * passphrase fails at the frame's GCM tag — NotACarrier, no oracle
     * (plan §4.5).
     */
    suspend fun extract(
        source: InputStream,
        passphrase: CharArray?,
    ): StegoExtractOutcome = withContext(dispatchers.io) {
        val bytes = try {
            source.readBytes()
        } catch (e: Exception) {
            return@withContext StegoExtractOutcome.Failed(e)
        }
        // ANY first: a CKE1 trailer is detectable on EVERY file, and a
        // PNG/WAV carrying a trailer (not pixel-embedded) must route here.
        try {
            val resolver: ((ByteArray, ByteArray) -> Pair<ByteArray, ByteArray>?)? =
                if (passphrase == null) {
                    // The JVM-pure carrier wants a sync resolver; the era
                    // lookup is Room/DAO work — bridge it on THIS IO
                    // dispatcher, which is already where we run.
                    { keyHint, salt ->
                        kotlinx.coroutines.runBlocking { resolveEraKeys(keyHint, salt) }
                    }
                } else {
                    null
                }
            val message = AnyCarrier.extract(
                java.io.ByteArrayInputStream(bytes),
                passphrase,
                eraKeysByKeyId = resolver,
                kdfParams = kdfParams,
            )
            return@withContext StegoExtractOutcome.Extracted(message)
        } catch (e: StegoError.NotACarrier) {
            // No trailer — try the pixel/sample paths below.
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
                    if (passphrase == null) {
                        return@withContext StegoExtractOutcome.NotACarrier
                    }
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
                    if (passphrase == null) {
                        return@withContext StegoExtractOutcome.NotACarrier
                    }
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
            else -> StegoExtractOutcome.NotACarrier
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
     * The ecosystem key resolver: the trailer's keyHint names an era;
     * resolve the CONTACT through the repository, require an ACTIVE
     * session (the caller's UI unlocks through the normal card and
     * retries when this returns null), and derive the frame/payload
     * keys by HKDF over the session key with the trailer's salt.
     */
    private suspend fun resolveEraKeys(
        keyHint: ByteArray,
        salt: ByteArray,
    ): Pair<ByteArray, ByteArray>? {
        val era = sessionKeys.eraByKeyId(keyHint) ?: return null
        val latest = sessionKeys.latestEra(era.contactId)
        if (latest?.sessionId != era.sessionId) {
            // Superseded era: only its HISTORICAL session can open it —
            // kept when the era rotated (the same rule MediaCipher uses).
            val historical = sessions.historicalSession(era.sessionId) ?: return null
            return AnyCarrier.deriveEcosystemKeys(historical.keyBytes, salt)
        }
        val active = sessions.activeSession(era.contactId) ?: return null
        if (active.sessionId != era.sessionId) return null
        return AnyCarrier.deriveEcosystemKeys(active.keyBytes, salt)
    }

    /**
     * Ecosystem ANY-embed: the era must be ACTIVE (the UI checked) —
     * keys derive by HKDF over the session key; the trailer records the
     * era's public keyId for the receiver's contact resolution.
     */
    private suspend fun embedAnyEcosystem(
        bytes: ByteArray,
        target: OutputStream,
        message: ByteArray,
        contactId: ContactId,
    ): StegoEmbedOutcome {
        val era = sessionKeys.latestEra(contactId)
            ?: return StegoEmbedOutcome.NotACarrier
        val active = sessions.activeSession(contactId)
            ?: return StegoEmbedOutcome.NotACarrier
        if (active.sessionId != era.sessionId) return StegoEmbedOutcome.NotACarrier
        return try {
            AnyCarrier.append(
                java.io.ByteArrayInputStream(bytes), target,
                message, null, active.keyBytes, era.keyId, kdfParams,
            )
            StegoEmbedOutcome.Embedded(
                StegoCarrierKind.ANY,
                message.size,
                bytes.size.toLong(),
            )
        } catch (e: Exception) {
            StegoEmbedOutcome.Failed(e)
        }
    }

    /**
     * Formats sniffed by signature (plan §4.5: no magic bytes are
     * ADDED by us — the carrier's own native signature routes it).
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
     * PNG path: decode → plan capacity → ±1 embed → re-encode. Returns
     * null when the pre-flight fails (caller passes the bytes through).
     */
    private fun embedPng(
        bytes: ByteArray,
        message: ByteArray,
        passphrase: CharArray,
    ): Pair<Unit, Bitmap>? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val w = bitmap.width
        val h = bitmap.height
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
