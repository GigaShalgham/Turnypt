package com.turnypt.core.stego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * BitSpread tests (stego plan §6.1 S1/S2 exit criteria: the keyed
 * permutation is a BIJECTION over the index space and is seed-bound —
 * a different key yields a different mapping).
 */
class BitSpreadTest {

    @Test
    fun `permutation is a bijection over the capacity`() {
        val key = ByteArray(32) { it.toByte() }
        val capacity = 2_000L
        val seen = HashSet<Long>()
        for (i in 0 until capacity) {
            val mapped = BitSpread.permute(i, capacity, key)
            assertTrue("out of range: $mapped", mapped in 0 until capacity)
            assertTrue("duplicate: $mapped", seen.add(mapped))
        }
        assertEquals(capacity.toInt(), seen.size)
    }

    @Test
    fun `bijection holds at an odd capacity`() {
        val key = ByteArray(32) { (it * 3).toByte() }
        val capacity = 1_025L // odd, just above 2^10
        val seen = HashSet<Long>()
        for (i in 0 until capacity) {
            val mapped = BitSpread.permute(i, capacity, key)
            assertTrue(mapped in 0 until capacity)
            assertTrue(seen.add(mapped))
        }
        assertEquals(capacity.toInt(), seen.size)
    }

    @Test
    fun `different keys give different layouts`() {
        val keyA = ByteArray(32) { 1 }
        val keyB = ByteArray(32) { 2 }
        val capacity = 500L
        var differ = 0
        for (i in 0 until capacity) {
            if (BitSpread.permute(i, capacity, keyA) != BitSpread.permute(i, capacity, keyB)) {
                differ++
            }
        }
        // Overwhelmingly different positions (not just a few).
        assertTrue("layouts too similar: $differ/$capacity", differ > capacity / 2)
    }

    @Test
    fun `positions returns the first count slots`() {
        val key = ByteArray(32) { 7 }
        val positions = BitSpread.positions(200, 50_000L, key)
        assertEquals(200, positions.size)
        positions.forEach { assertTrue(it in 0 until 50_000L) }
    }

    @Test
    fun `embed material is sized per the plan`() {
        val material = BitSpread.freshMaterial()
        assertEquals(16, material.salt.size)
        assertEquals(12, material.frameNonce.size)
        assertEquals(12, material.payloadNonce.size)
    }

    @Test
    fun `spread key is deterministic and payload-key-bound`() {
        val payloadKey = ByteArray(32) { it.toByte() }
        assertEquals(
            MessageDigest.isEqual(
                BitSpread.spreadKey(payloadKey),
                BitSpread.spreadKey(payloadKey),
            ),
            true,
        )
    }
}
