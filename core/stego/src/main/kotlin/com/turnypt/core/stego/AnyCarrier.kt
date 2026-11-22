package com.turnypt.core.stego

import com.turnypt.core.crypto.Argon2KeyDerivator
import com.turnypt.core.crypto.Argon2Params
import com.turnypt.core.crypto.CipherAlgorithm
import com.turnypt.core.crypto.Hkdf
import com.turnypt.core.crypto.ProtocolRandom
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom

/**
 * AnyCarrier — the "any file, any extension" EOF-append carrier (the
 * 2026-11-22 user decree: a hidden message inside EVERY file, sealed
 * with full security). The third carrier family beside PNG and WAV.
 *
 * Technique: the message is sealed with the SAME CKS2 engine the
 * pixel/sample carriers use (Argon2id → HKDF frame/payload keys →
 * AES-256-GCM chunks — one crypto core for the whole stego family),
 * then the sealed stream is APPENDED after the file's original bytes
 * in a self-locating CKE1 trailer. Nothing inside the carrier is
 * modified, which is why this works for EVERY format and EVERY
 * extension: PDF, ZIP, APK, MP4, JPEG, GIF, TXT, SQLite, no-extension —
 * the file's own parsers stop at their native EOF and never reach the
 * trailer, exactly the property append-steganography exploits.
 *
 * Trailer layout (multi-byte integers big-endian), from EOF backwards:
 *
 * ```
 * ORIGINAL FILE BYTES (untouched, streamed)
 * PAD (0..15 random bytes — hides the trailer's alignment)
 * salt(16) || Cks2 FRAME(64) || sealed body   ← the Cks2 stream
 * keyHint(16)    era keyId (ecosystem path) or all-zero sentinel
 *                (passphrase-only path)
 * payloadLen(4)  bytes from the salt's first byte to EOF
 * magic(4)       "CKE1" (0x43 0x4B 0x45 0x31)
 * ```
 *
 * Detection is O(1): the magic sits at the very end, payloadLen walks
 * back to the salt. A file with no trailer fails the magic check —
 * [StegoError.NotACarrier]. A wrong passphrase fails the Cks2 frame's
 * GCM tag — also NotACarrier, the same no-oracle failure model the
 * PNG/WAV paths use (plan §4.5).
 *
 * Two key models, one container:
 *  - PASSPHRASE-ONLY (plan §4.4, kept): Argon2id over the passphrase +
 *    the trailer's own salt/nonces — self-contained, travels out-of-band.
 *  - ECOSYSTEM (new, the user decree "member of the Turnypt ecosystem"):
 *    the keys derive from the CONTACT'S UNLOCKED ERA SESSION KEY via
 *    HKDF — the hidden message then rides the same unlock/era/timeout
 *    discipline as CK1 text and CKF1 files. The trailer's keyHint
 *    records the era's public keyId so the receiver's app resolves
 *    WHICH contact the file was hidden for and offers the SAME unlock
 *    card every other Turnypt flow uses.
 *
 * Memory discipline (plan §5.3): the ORIGINAL carrier bytes are
 * STREAMED through on embed — never buffered whole; only the trailer
 * (message-sized) is held. On extract the stream is read once whole
 * because SAF streams are not seekable and the trailer sits at the end.
 */
object AnyCarrier {

    /** "CKE1" — the any-carrier trailer magic. */
    val MAGIC: ByteArray = byteArrayOf(0x43, 0x4B, 0x45, 0x31)

    /** keyHint(16) + payloadLen(4) + magic(4) — the fixed trailer tail. */
    const val TAIL_BYTES: Int = ProtocolRandom.KEY_ID_LENGTH_BYTES + 4 + 4

    /** The all-zero keyHint sentinel — passphrase-only embed. */
    val NO_KEY_HINT: ByteArray = ByteArray(ProtocolRandom.KEY_ID_LENGTH_BYTES)

    /**
     * Ecosystem frame/payload keys: HKDF-SHA256 over the era's session
     * key, salted by the trailer's fresh salt, same two-info split as
     * Cks2.deriveKeys so [Cks2Codec] serves both key models unchanged.
     */
    fun deriveEcosystemKeys(
        sessionKey: ByteArray,
        salt: ByteArray,
    ): Pair<ByteArray, ByteArray> {
        val frameKey = Hkdf.sha256(sessionKey, salt, Cks2.FRAME_INFO, 32)
        val payloadKey = Hkdf.sha256(sessionKey, salt, Cks2.PAYLOAD_INFO, 32)
        return frameKey to payloadKey
    }

    /**
     * Appends the sealed trailer after the ORIGINAL bytes of [source]
     * (streamed through untouched) onto [target].
     *
     * Key routing: [keyId] non-null selects the ECOSYSTEM model — the
     * caller passes the era's active session key in [sessionKey] and the
     * era's public keyId; the trailer records keyId and the keys derive
     * by HKDF over the session key. [keyId] null selects the
     * PASSPHRASE-ONLY model — Argon2id over [passphrase] and the
     * trailer's own salt/nonces.
     *
     * @return the sealed Cks2 stream (tests verify independence).
     */
    fun append(
        source: InputStream,
        target: OutputStream,
        message: ByteArray,
        passphrase: CharArray?,
        sessionKey: ByteArray?,
        keyId: ByteArray?,
        kdfParams: Argon2Params,
        random: SecureRandom = SecureRandom(),
        codec: Cks2Codec = Cks2Codec(),
        derivator: Argon2KeyDerivator = Argon2KeyDerivator(),
    ): ByteArray {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val frameNonce = ByteArray(Cks2.FRAME_NONCE_BYTES).also { random.nextBytes(it) }

        // Seal with the shared engine. The codec derives its payload
        // nonce from the frame nonce; the Argon2 AD must bind THAT value.
        val codecPayloadNonce = codec.payloadNonceFromNonce(frameNonce)
        val (frameKey, payloadKey) = when {
            keyId != null && sessionKey != null -> deriveEcosystemKeys(sessionKey, salt)
            keyId == null && passphrase != null -> Cks2.deriveKeys(
                passphrase, salt, frameNonce, codecPayloadNonce, kdfParams, derivator,
            )
            else -> throw IllegalArgumentException("exactly one key model is required")
        }
        val sealed = codec.encode(frameKey, payloadKey, frameNonce, message)

        // Stream the original bytes through — the carrier is never held
        // whole in memory.
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = source.read(buffer)
            if (read == -1) break
            target.write(buffer, 0, read)
        }
        // Random pad hides the trailer's alignment from a length probe.
        repeat(random.nextInt(16)) { target.write(random.nextInt(256)) }
        target.write(salt)
        target.write(sealed)
        target.write(keyId ?: NO_KEY_HINT)
        // payloadLen = salt(16) + sealed + keyHint + len + magic.
        writeInt32BE(target, 16 + sealed.size + TAIL_BYTES)
        target.write(MAGIC)
        target.flush()
        return sealed
    }

    /**
     * Extracts the hidden message from a stream ending with a CKE1
     * trailer. [passphrase] serves the passphrase-only model;
     * [eraKeysByKeyId] serves the ecosystem model — it receives the
     * trailer's keyHint AND salt and returns the era's HKDF frame/payload
     * keys, or null when the contact is LOCKED (the caller then routes
     * through the normal unlock card and retries). Synchronous on
     * purpose: the JVM-pure carrier stays non-suspending; the
     * repository layer wraps it on its IO dispatcher.
     *
     * Throws [StegoError.NotACarrier] when the file carries no trailer,
     * the trailer is corrupt, or the passphrase is wrong — the honest
     * no-oracle failure of the sibling carriers.
     */
    fun extract(
        source: InputStream,
        passphrase: CharArray?,
        eraKeysByKeyId: ((keyHint: ByteArray, salt: ByteArray) -> Pair<ByteArray, ByteArray>?)?,
        kdfParams: Argon2Params,
        codec: Cks2Codec = Cks2Codec(),
        derivator: Argon2KeyDerivator = Argon2KeyDerivator(),
    ): ByteArray {
        val bytes = source.readBytes()
        if (bytes.size < 16 + Cks2.FRAME_BYTES + TAIL_BYTES) throw StegoError.NotACarrier

        // Fixed tail: magic at EOF, payloadLen before it, keyHint before that.
        val magic = bytes.copyOfRange(bytes.size - 4, bytes.size)
        if (!magic.contentEquals(MAGIC)) throw StegoError.NotACarrier
        val payloadLen = readInt32BE(bytes, bytes.size - 8)
        // payloadLen covers salt(16) + sealed stream + the fixed tail.
        if (payloadLen <= TAIL_BYTES + Cks2.FRAME_BYTES + 16 ||
            payloadLen > bytes.size
        ) {
            throw StegoError.NotACarrier
        }
        val trailerStart = bytes.size - payloadLen
        if (trailerStart < 0) throw StegoError.NotACarrier
        val keyHint = bytes.copyOfRange(bytes.size - TAIL_BYTES, bytes.size - TAIL_BYTES + 16)
        val salt = bytes.copyOfRange(trailerStart, trailerStart + 16)
        val frame = bytes.copyOfRange(trailerStart + 16, trailerStart + 16 + Cks2.FRAME_BYTES)
        val body = bytes.copyOfRange(trailerStart + 16 + Cks2.FRAME_BYTES, bytes.size - TAIL_BYTES)

        val frameKey: ByteArray
        val payloadKey: ByteArray
        if (keyHint.contentEquals(NO_KEY_HINT)) {
            if (passphrase == null || passphrase.isEmpty()) throw StegoError.NotACarrier
            val frameNonce = frame.copyOfRange(0, Cks2.FRAME_NONCE_BYTES)
            val codecPayloadNonce = codec.payloadNonceFromNonce(frameNonce)
            val keys = Cks2.deriveKeys(
                passphrase, salt, frameNonce, codecPayloadNonce, kdfParams, derivator,
            )
            frameKey = keys.first
            payloadKey = keys.second
        } else {
            if (eraKeysByKeyId == null) throw StegoError.NotACarrier
            val keys = eraKeysByKeyId(keyHint, salt) ?: throw StegoError.NotACarrier
            frameKey = keys.first
            payloadKey = keys.second
        }
        return try {
            codec.decode(frameKey, payloadKey, frame + body)
        } catch (e: StegoError.Truncated) {
            throw StegoError.NotACarrier
        }
    }

    private fun writeInt32BE(target: OutputStream, value: Int) {
        target.write((value ushr 24) and 0xFF)
        target.write((value ushr 16) and 0xFF)
        target.write((value ushr 8) and 0xFF)
        target.write(value and 0xFF)
    }

    private fun readInt32BE(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or
            ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or
            (bytes[at + 3].toInt() and 0xFF)
}

/**
 * The any-file facade the repository layer wires to: seal with the
 * shared Cks2 engine, append after the original bytes, extract with
 * the two key models. Kept as a thin class (not more objects) so the
 * app layer injects ONE stego twin per carrier family with matching
 * outcome discipline.
 */
class AnyStegoCipher(
    private val kdfParams: Argon2Params,
    private val derivator: Argon2KeyDerivator = Argon2KeyDerivator(),
    private val codec: Cks2Codec = Cks2Codec(),
) {

    /** Passphrase-only embed — the plan §4.4 self-contained container. */
    fun appendPassphrase(
        source: InputStream,
        target: OutputStream,
        message: ByteArray,
        passphrase: CharArray,
    ): ByteArray = AnyCarrier.append(
        source, target, message, passphrase, null, null,
        kdfParams, SecureRandom(), codec, derivator,
    )

    /**
     * Ecosystem embed — the hidden message rides the contact's era:
     * keys derive by HKDF over the ACTIVE session key, and the era's
     * public keyId lands in the trailer so the receiver resolves the
     * contact and unlocks through the standard Turnypt card.
     */
    fun appendEcosystem(
        source: InputStream,
        target: OutputStream,
        message: ByteArray,
        sessionKey: ByteArray,
        keyId: ByteArray,
    ): ByteArray = AnyCarrier.append(
        source, target, message, null, sessionKey, keyId,
        kdfParams, SecureRandom(), codec, derivator,
    )

    /** Passphrase-only extract (NotACarrier when the file carries no trailer). */
    fun extractPassphrase(
        source: InputStream,
        passphrase: CharArray,
    ): ByteArray = AnyCarrier.extract(
        source, passphrase, null, kdfParams, codec, derivator,
    )

    /**
     * Ecosystem extract: [eraKeysByKeyId] resolves the era's keys from
     * the app's session machinery (it receives the trailer's keyHint and
     * salt); null means "locked — ask for the passphrase", which the
     * caller surfaces as a NeedsUnlock-style outcome rather than an
     * error.
     */
    fun extractEcosystem(
        source: InputStream,
        eraKeysByKeyId: (keyHint: ByteArray, salt: ByteArray) -> Pair<ByteArray, ByteArray>?,
    ): ByteArray = AnyCarrier.extract(
        source, null, eraKeysByKeyId, kdfParams, codec, derivator,
    )
}
