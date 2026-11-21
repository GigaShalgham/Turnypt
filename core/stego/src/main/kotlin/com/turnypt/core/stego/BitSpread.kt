package com.turnypt.core.stego

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * BitSpread — WHERE the payload bits land inside a carrier (stego plan
 * §4.3): a keyed PRNG permutation over the carrier's insertion-index
 * space, so the same [K_payload]-derived seed places and finds the bits
 * while a bare-eye/statistical scan sees an even LSB pattern.
 *
 * Construction: a 4-round Feistel network over the index space, wrapped
 * in standard CYCLE-WALKING over [0, capacity). The Feistel half-width
 * is ceil(bits(capacity-1) / 2), so the padded space 2^(2*halfBits) ≥
 * capacity; any out-of-range image is fed back through the SAME
 * Feistel until it lands in range. Cycle-walking preserves bijectivity
 * on the restricted domain (the classic PGP/Black-Rogaway argument) —
 * the first version of this file tried a modular "fold" instead, which
 * could bounce forever and hung the bijection test.
 */
object BitSpread {

    /** Feistel rounds — 4 is the classic minimal "enough diffusion" count. */
    private const val ROUNDS = 4

    /**
     * Derives the permutation key from the payload key: HKDF-stretched,
     * so the spread is bound to the same secret material as the AEAD body.
     */
    fun spreadKey(payloadKey: ByteArray): ByteArray {
        val info = "CKS2/spread/v1".toByteArray(Charsets.US_ASCII)
        return com.turnypt.core.crypto.Hkdf.sha256(payloadKey, ByteArray(0), info, 32)
    }

    /**
     * The capacity-domain permutation: maps insertion index [index] in
     * `0 until capacity` to its scrambled position, bijectively.
     */
    fun permute(index: Long, capacity: Long, key: ByteArray): Long {
        require(capacity > 0) { "capacity must be positive" }
        require(index in 0 until capacity) { "index out of domain" }
        var value = feistel(index, key, capacity)
        // Cycle-walk: the padded Feistel space is >= capacity; re-apply
        // until the image lands inside the domain. Expected iterations
        // are 2^(2*halfBits)/capacity — small by construction.
        var guard = 0
        while (value >= capacity) {
            value = feistel(value, key, capacity)
            check(++guard < 1_000) { "cycle-walk failed to converge" }
        }
        return value
    }

    /** One Feistel pass over the padded index space. */
    private fun feistel(value: Long, key: ByteArray, capacity: Long): Long {
        // HALF width: ceil(bits(capacity-1)/2). The earlier version used the
        // full bit width as the half — left then escaped its mask on every
        // odd input, the rejoin grew beyond the padded space, and the
        // cycle-walk diverged (the 1h hang and the 1000-iteration guard).
        val bits = bitsFor(capacity - 1)
        val halfBits = (bits + 1) / 2
        val halfMask = (1L shl halfBits) - 1
        var left = (value ushr halfBits) and halfMask
        var right = value and halfMask
        val digest = MessageDigest.getInstance("SHA-256")
        for (round in 0 until ROUNDS) {
            val f = roundFunction(digest, key, right, round, halfBits) and halfMask
            val newLeft = right
            right = left xor f
            left = newLeft
        }
        return (left shl halfBits) or right
    }

    private fun roundFunction(
        digest: MessageDigest,
        key: ByteArray,
        right: Long,
        round: Int,
        halfBits: Int,
    ): Long {
        digest.reset()
        digest.update(key)
        digest.update(longToBytes(right))
        digest.update(round.toByte())
        val hash = digest.digest()
        var acc = 0L
        val need = (halfBits + 7) / 8
        for (i in 0 until need) {
            acc = (acc shl 8) or (hash[i].toLong() and 0xFF)
        }
        return acc
    }

    private fun bitsFor(maxValue: Long): Int {
        var bits = 0
        var v = maxValue
        while (v > 0) {
            bits++
            v = v shr 1
        }
        return bits.coerceAtLeast(1)
    }

    private fun longToBytes(v: Long): ByteArray {
        val out = ByteArray(8)
        for (i in 0 until 8) {
            out[i] = (v ushr ((7 - i) * 8)).toByte()
        }
        return out
    }

    /**
     * Convenience: the ordered embed positions for the FIRST [count]
     * payload bits — `permutation(i)` for i in 0 until count.
     */
    fun positions(count: Int, capacity: Long, key: ByteArray): LongArray {
        val out = LongArray(count)
        for (i in 0 until count) {
            out[i] = permute(i.toLong(), capacity, key)
        }
        return out
    }

    /** Fresh nonces/salt for an embed (plan: 16B salt, 12B nonces). */
    fun freshMaterial(random: SecureRandom = SecureRandom()): EmbedMaterial {
        return EmbedMaterial(
            salt = ByteArray(16).also { random.nextBytes(it) },
            frameNonce = ByteArray(12).also { random.nextBytes(it) },
            payloadNonce = ByteArray(12).also { random.nextBytes(it) },
        )
    }
}

/** The per-embed random material (plan §4.2: 16B salt + two 12B nonces). */
data class EmbedMaterial(
    val salt: ByteArray,
    val frameNonce: ByteArray,
    val payloadNonce: ByteArray,
)
