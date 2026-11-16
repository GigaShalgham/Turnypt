package com.turnypt.core.sessions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.turnypt.core.common.ContactId
import com.turnypt.core.common.SessionId
import com.turnypt.core.crypto.Argon2KeyDerivator
import com.turnypt.core.crypto.Argon2Params
import com.turnypt.security.policies.KeyPolicy
import java.util.Arrays
import kotlin.coroutines.CoroutineContext

/**
 * In-memory registry of unlocked per-contact session keys.
 *
 * Responsibilities:
 *  - derive keys (Argon2id) off the caller's thread;
 *  - hold at most ONE active session per contact (the latest era), used
 *    for BOTH directions;
 *  - hold a bounded LRU cache of HISTORICAL sessions (superseded key
 *    eras, v0.4.0) used ONLY to decrypt old-era envelopes — never for
 *    encryption (see [activateHistorical]);
 *  - enforce a fixed lifetime per session (no sliding renewal — the countdown
 *    starts at unlock; an explicit timeout CHANGE re-arms every live
 *    session, v1.3.1);
 *  - wipe key material on lock, replacement and expiry;
 *  - guarantee isolation: a key derived for contact A is never returned for
 *    contact B (see KeySessionManagerTest).
 *
 * This class performs NO persistence and NO Keystore access; those live in
 * the data and security modules.
 */
class KeySessionManager(
    private val derivator: Argon2KeyDerivator = Argon2KeyDerivator(),
    private val timeSource: TimeSource = TimeSource.SYSTEM,
    private val deriveContext: CoroutineContext = Dispatchers.Default,
) {

    private val mutex = Mutex()
    private val active = HashMap<ContactId, Entry>()

    // accessOrder = true: iteration starts at the least recently used entry,
    // which is the one evicted when the bound is exceeded.
    private val historical = LinkedHashMap<SessionId, HistEntry>(16, 0.75f, true)

    private class Entry(
        val sessionKey: SessionKey,
        val unlockedAtMillis: Long,
        val timeoutMillis: Long,
    ) {
        /** v1.3.1: evaluated against the LIVE fields, not a frozen deadline —
         * the countdown starts at unlock and an explicit timeout change
         * re-arms it (see [rearmTimeout]). */
        fun expiresAtMillis(): Long = KeyPolicy.sessionDeadline(unlockedAtMillis, timeoutMillis)
    }

    /** Historical variant: carries its contact so lock(contact) can wipe it. */
    private class HistEntry(
        val contactId: ContactId,
        val sessionKey: SessionKey,
        val unlockedAtMillis: Long,
        val timeoutMillis: Long,
    ) {
        fun expiresAtMillis(): Long = KeyPolicy.sessionDeadline(unlockedAtMillis, timeoutMillis)
    }

    /**
     * v0.4.2 (deep-scan N1/N2): monotonic change counter for the manager's
     * state — bumped on every activate, lock, lockAll and expiry (lazy or
     * swept). Screens used to snapshot "who is unlocked" once and then miss
     * a lockAll() or a timeout that happened in another surface: the Encrypt
     * tab kept its button up with no unlock card, and an open companion
     * sheet kept offering a fingerprint wrap of a rotated era. Collectors of
     * this flow re-read the live state instead.
     */
    private val _sessionChanges = MutableStateFlow(0L)
    val sessionChanges: StateFlow<Long> = _sessionChanges.asStateFlow()

    /** Must be called while holding [mutex]. */
    private fun bumpSessionChangesLocked() {
        _sessionChanges.value += 1L
    }

    companion object {
        /**
         * Upper bound of simultaneously cached historical (old-era)
         * sessions. Beyond it the least recently used entry is wiped and
         * evicted — the user simply re-enters the passphrase next time an
         * old-era envelope arrives.
         */
        const val MAX_HISTORICAL_SESSIONS = 8
    }

    /**
     * Derives a 256-bit key from the passphrase and the session's salt.
     * CPU/memory heavy: always called from [deriveContext] (Default
     * dispatcher unless overridden).
     */
    suspend fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        params: Argon2Params,
    ): ByteArray = withContext(deriveContext) {
        derivator.derive(passphrase, salt, params)
    }

    /**
     * Activates (or replaces) the unlocked session for a contact. The
     * previous session's key material, if any, is wiped first.
     *
     * The keyId, salt AND keyBytes buffers are COPIED: a SessionKey owns its
     * buffers so that wiping on lock/expiry can never mutate storage-layer
     * objects that may still be aliased by the caller (defensive against
     * DAOs or repositories that hand out shared byte arrays, and against the
     * repository zeroing the same array it is about to persist — deep-scan
     * finding 1: a concurrent lock() between activate() and the wrapped-
     * verifier persistence used to zero the key mid-wrap and could persist
     * an all-zeros verifier that permanently rejected the correct
     * passphrase).
     */
    suspend fun activate(
        contactId: ContactId,
        sessionId: SessionId,
        keyId: ByteArray,
        salt: ByteArray,
        params: Argon2Params,
        keyBytes: ByteArray,
        timeoutMillis: Long,
    ): SessionKey = mutex.withLock {
        val existing = active.remove(contactId)
        if (existing != null) wipe(existing.sessionKey)

        val sessionKey = SessionKey(
            sessionId = sessionId,
            contactId = contactId,
            keyId = keyId.copyOf(),
            salt = salt.copyOf(),
            params = params,
            keyBytes = keyBytes.copyOf(),
        )
        active[contactId] = Entry(
            sessionKey,
            timeSource.nowMillis(),
            KeyPolicy.clampSessionTimeout(timeoutMillis),
        )
        bumpSessionChangesLocked()
        sessionKey
    }

    /**
     * Returns the unlocked session for the contact, or null when no session
     * is active or it has expired (expired sessions are wiped and removed as
     * a side effect).
     */
    suspend fun activeSession(contactId: ContactId): SessionKey? = mutex.withLock {
        val entry = active[contactId] ?: return@withLock null
        if (entry.expiresAtMillis() <= timeSource.nowMillis()) {
            active.remove(contactId)
            wipe(entry.sessionKey)
            bumpSessionChangesLocked()
            return@withLock null
        }
        entry.sessionKey
    }

    /**
     * v0.4.0 (old-key-era decryption): activates a HISTORICAL session — the
     * key of a superseded key era, re-derived with that era's salt so old
     * envelopes can still be read.
     *
     * Deliberate differences from [activate]:
     *  - the per-contact ACTIVE session is left untouched (an old-era key
     *    must never silently become the sending key);
     *  - several historical sessions may coexist, keyed by session id, up to
     *    [MAX_HISTORICAL_SESSIONS] — the least recently used entry is wiped
     *    and evicted beyond that;
     *  - historical sessions exist ONLY to serve decryption lookups via
     *    [historicalSession]; nothing in the encrypt path consults them.
     *
     * The keyId, salt AND keyBytes buffers are copied, exactly like
     * [activate] — a SessionKey owns its buffers.
     */
    suspend fun activateHistorical(
        contactId: ContactId,
        sessionId: SessionId,
        keyId: ByteArray,
        salt: ByteArray,
        params: Argon2Params,
        keyBytes: ByteArray,
        timeoutMillis: Long,
    ): SessionKey = mutex.withLock {
        historical.remove(sessionId)?.let { wipe(it.sessionKey) }
        while (historical.size >= MAX_HISTORICAL_SESSIONS) {
            // accessOrder map: the iterator starts at the least recently
            // used entry — evict exactly that one.
            val iterator = historical.entries.iterator()
            val eldest = iterator.next()
            iterator.remove()
            wipe(eldest.value.sessionKey)
        }
        val sessionKey = SessionKey(
            sessionId = sessionId,
            contactId = contactId,
            keyId = keyId.copyOf(),
            salt = salt.copyOf(),
            params = params,
            keyBytes = keyBytes.copyOf(),
        )
        historical[sessionId] = HistEntry(
            contactId,
            sessionKey,
            timeSource.nowMillis(),
            KeyPolicy.clampSessionTimeout(timeoutMillis),
        )
        bumpSessionChangesLocked()
        sessionKey
    }

    /**
     * v0.4.0 (old-key-era decryption): the cached historical session of a
     * superseded era, or null when it was never unlocked here, or it has
     * expired (expired entries are wiped and removed as a side effect).
     * Access refreshes the LRU order.
     */
    suspend fun historicalSession(sessionId: SessionId): SessionKey? = mutex.withLock {
        val entry = historical[sessionId] ?: return@withLock null
        if (entry.expiresAtMillis() <= timeSource.nowMillis()) {
            historical.remove(sessionId)
            wipe(entry.sessionKey)
            bumpSessionChangesLocked()
            return@withLock null
        }
        entry.sessionKey
    }

    /**
     * Locks (wipes) the contact's session. Returns true if one existed.
     *
     * v0.4.0: also wipes every HISTORICAL session of the contact — "lock"
     * means no key material of this contact stays in memory, regardless of
     * which era it belongs to.
     */
    suspend fun lock(contactId: ContactId): Boolean = mutex.withLock {
        var existed = false
        active.remove(contactId)?.let {
            wipe(it.sessionKey)
            existed = true
        }
        val histIterator = historical.entries.iterator()
        while (histIterator.hasNext()) {
            val entry = histIterator.next().value
            if (entry.contactId == contactId) {
                histIterator.remove()
                wipe(entry.sessionKey)
                existed = true
            }
        }
        if (existed) bumpSessionChangesLocked()
        existed
    }

    /**
     * Locks every session (active + historical). Returns the number of
     * sessions wiped.
     */
    suspend fun lockAll(): Int = mutex.withLock {
        var count = active.size
        active.values.forEach { wipe(it.sessionKey) }
        active.clear()
        count += historical.size
        historical.values.forEach { wipe(it.sessionKey) }
        historical.clear()
        if (count > 0) bumpSessionChangesLocked()
        count
    }

    /** Snapshot of contacts with a live (non-expired) session. */
    suspend fun unlockedContactIds(): Set<ContactId> = mutex.withLock {
        sweepExpiredLocked()
        active.keys.toSet()
    }

    /** Absolute expiry timestamp of the contact's session, or null. */
    suspend fun expiryOf(contactId: ContactId): Long? = mutex.withLock {
        sweepExpiredLocked()
        active[contactId]?.expiresAtMillis()
    }

    /**
     * v1.3.1 (lock-time fix): applies a NEW timeout to every live session —
     * active AND historical.
     *
     * Before this, the timeout was read exactly once (at unlock) and frozen
     * into an absolute deadline, so changing it in Settings did nothing to
     * already-unlocked sessions: tightening 24 h → 1 min left them live for
     * hours, and the 60 s sweeper kept honouring the stale deadline too.
     *
     * Semantics — the new value applies as if it had been the setting AT
     * UNLOCK: the anchor (unlockedAt) is kept and the deadline re-evaluated
     * as `unlockedAt + newTimeout`, so the module's doctrine "no sliding
     * renewal, the countdown starts at unlock" survives the change and a
     * session can never gain lifetime beyond the new setting's intent:
     *  - tightening: a session whose elapsed lifetime already exceeds the
     *    new timeout is wiped IMMEDIATELY (a tightening must be able to
     *    LOCK, not merely shorten future lifetimes);
     *  - extending: live sessions get the fuller budget from their original
     *    anchor — but a session already expired under its OLD deadline is
     *    wiped first and never resurrected (the user re-enters the
     *    passphrase).
     *
     * Returns the number of sessions the new value was applied to. Wipes
     * bump the sessionChanges counter (the unlocked set changed); a change
     * with nothing live is a no-op and bumps nothing.
     */
    suspend fun rearmTimeout(newTimeoutMillis: Long): Int = mutex.withLock {
        // 1. Wipe sessions already past their OLD deadlines FIRST — an
        //    extension must never reach back and revive an expired session.
        sweepExpiredLocked()

        // 2. Swap the duration on every survivor; the anchor is kept. The
        //    iterator's setValue keeps the maps structurally intact.
        val clamped = KeyPolicy.clampSessionTimeout(newTimeoutMillis)
        var applied = 0
        val activeIterator = active.entries.iterator()
        while (activeIterator.hasNext()) {
            val entry = activeIterator.next()
            entry.setValue(Entry(entry.value.sessionKey, entry.value.unlockedAtMillis, clamped))
            applied++
        }
        val historicalIterator = historical.entries.iterator()
        while (historicalIterator.hasNext()) {
            val entry = historicalIterator.next()
            entry.setValue(
                HistEntry(entry.value.contactId, entry.value.sessionKey, entry.value.unlockedAtMillis, clamped),
            )
            applied++
        }

        // 3. Sweep again under the NEW deadlines: a tightening may condemn
        //    sessions whose elapsed lifetime already exceeds the new
        //    budget — they must wipe NOW, not at the next query/sweep.
        //    (bumps the counter only when it actually wipes something).
        sweepExpiredLocked()
        applied
    }

    /**
     * Wipes every expired session immediately instead of waiting for the
     * next per-contact query, and returns how many were swept.
     *
     * Expiry is otherwise lazy (deep-scan finding 8): an expired-but-never-
     * queried key would stay wiped-in-waiting in memory until process death.
     * The app starts a low-frequency sweeper calling this (see
     * TurnyptApplication), bounding the residual window to roughly one
     * sweep interval.
     */
    suspend fun sweepExpired(): Int = mutex.withLock { sweepExpiredLocked() }

    private fun wipe(sessionKey: SessionKey) {
        Arrays.fill(sessionKey.keyBytes, 0.toByte())
        sessionKey.keyId.fill(0) // public identifier, wiped for symmetry
    }

    private fun sweepExpiredLocked(): Int {
        val now = timeSource.nowMillis()
        var swept = 0
        val expired = active.filterValues { it.expiresAtMillis() <= now }.keys
        expired.forEach { id ->
            active.remove(id)?.let {
                wipe(it.sessionKey)
                swept++
            }
        }
        val expiredHist = historical.filterValues { it.expiresAtMillis() <= now }.keys
        expiredHist.forEach { id ->
            historical.remove(id)?.let {
                wipe(it.sessionKey)
                swept++
            }
        }
        if (swept > 0) bumpSessionChangesLocked()
        return swept
    }
}
