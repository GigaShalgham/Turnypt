package com.turnypt.app.companion

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import java.io.File
import javax.crypto.Cipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.turnypt.app.R
import com.turnypt.app.clipboard.ClipboardHistoryRecorder
import com.turnypt.app.security.BiometricKeyStore
import com.turnypt.core.common.DispatcherProvider
import com.turnypt.core.crypto.Argon2KeyDerivator
import com.turnypt.core.crypto.Argon2Params
import com.turnypt.core.crypto.PassphraseCryptoProvider
import com.turnypt.core.crypto.ProtocolRandom
import com.turnypt.core.crypto.RandomSource
import com.turnypt.core.keys.FixedKeyProtector
import com.turnypt.core.sessions.KeySessionManager
import com.turnypt.core.sessions.TimeSource
import com.turnypt.data.database.ClipboardHistoryDao
import com.turnypt.data.database.ClipboardHistoryEntity
import com.turnypt.data.database.ContactDao
import com.turnypt.data.database.ContactEntity
import com.turnypt.data.database.SessionKeyDao
import com.turnypt.data.database.SessionKeyEntity
import com.turnypt.data.preferences.SettingsRepository
import com.turnypt.data.repository.Contact
import com.turnypt.data.repository.ContactRepository
import com.turnypt.data.repository.ContactSession
import com.turnypt.data.repository.MessageCipher
import com.turnypt.data.repository.SessionKeyRepository
import com.turnypt.security.clipboard.ClipboardAccess
import com.turnypt.security.clipboard.ClipboardParser
import com.turnypt.security.clipboard.ClipboardRead

/**
 * v1.5.8 regression tests for the user-reported bug: "decrypting from the
 * text-selection menu — neither the passphrase nor the fingerprint unlocks
 * the contact's lock; encryption works fine."
 *
 * The companion decrypt sheet (PROCESS_TEXT) is driven through the REAL
 * CompanionViewModel on the real crypto stack (same faking style as
 * ToolsViewModelUnlockTest / CompanionWorkflowRoundTripTest):
 *
 *  1. FINGERPRINT path: requestBiometricUnlock never armed `afterUnlock`,
 *     so a successful prompt activated the session key and… nothing re-ran.
 *     The sheet stayed on the unlock card — the exact report.
 *  2. RESYNC clobber: the unlock's own sessionChanges bump fired
 *     resyncSessionState, which recomputed `unlocked` over the DECRYPT
 *     flow's EMPTY contacts list and wiped the just-written entry.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CompanionDecryptUnlockTest {

    private val passphrase = "correct horse battery staple"

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ------------------------------------------------------ scenarios

    @Test
    fun `passphrase unlock continues the interrupted decrypt`() = runTest {
        val env = Env(StandardTestDispatcher(testScheduler))
        val envelope = env.envelopeFromPeer()
        val viewModel = env.buildViewModel()
        advanceUntilIdle()

        // The sheet landed on the unlock card, envelope pending.
        viewModel.beginDecrypt(envelope, TextSource.SELECTION, readOnly = true)
        advanceUntilIdle()
        assertEquals(CompanionUiState.Phase.NeedsUnlock, viewModel.state.value.phase)
        assertNull(viewModel.state.value.resultText)

        viewModel.unlockAndDecrypt(passphrase.toCharArray())
        advanceUntilIdle()

        // THE regression: the decrypt must actually run after the unlock.
        assertEquals(CompanionUiState.Phase.Result, viewModel.state.value.phase)
        assertEquals("secret message", viewModel.state.value.resultText)
        assertFalse(viewModel.state.value.busy)
        env.cleanup()
    }

    @Test
    fun `fingerprint unlock continues the interrupted decrypt`() = runTest {
        val env = Env(StandardTestDispatcher(testScheduler))
        // A remembered wrap exists (hasWrapped=true) and the store hands
        // back the real session key bytes — what the prompt's finishUnwrap
        // would deliver on-device.
        val envelope = env.envelopeFromPeer()
        // The Settings toggle must be ON for the offer to be computed.
        env.settings.setBiometricUnlockEnabled(true)
        val viewModel = env.buildViewModel(
            biometrics = ScriptedBiometrics(wrapped = true),
        )
        advanceUntilIdle()

        viewModel.beginDecrypt(envelope, TextSource.SELECTION, readOnly = true)
        advanceUntilIdle()
        assertEquals(CompanionUiState.Phase.NeedsUnlock, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.biometricOffered)

        // "Use fingerprint": the prompt shows, the sensor authenticates.
        viewModel.requestBiometricUnlock()
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.biometricPrompt)

        // Deliver the unwrapped key bytes (the system prompt's success
        // callback into the ViewModel).
        viewModel.onBiometricUnlockResult(env.rememberedKeyBytes)
        advanceUntilIdle()

        // THE regression: with afterUnlock never armed, the sheet sat on
        // the unlock card forever — session unlocked, nothing decrypted.
        assertEquals(CompanionUiState.Phase.Result, viewModel.state.value.phase)
        assertEquals("secret message", viewModel.state.value.resultText)
        assertFalse(viewModel.state.value.busy)
        assertNull(viewModel.state.value.biometricPrompt)
        env.cleanup()
    }

    @Test
    fun `a lockAll elsewhere retracts the pending contact during decrypt`() = runTest {
        val env = Env(StandardTestDispatcher(testScheduler))
        val envelope = env.envelopeFromPeer()
        val viewModel = env.buildViewModel()
        advanceUntilIdle()

        viewModel.beginDecrypt(envelope, TextSource.SELECTION, readOnly = true)
        advanceUntilIdle()
        viewModel.unlockAndDecrypt(passphrase.toCharArray())
        advanceUntilIdle()
        assertEquals(CompanionUiState.Phase.Result, viewModel.state.value.phase)

        // The floating assistant's "Lock all sessions" while the sheet is
        // open: the pending contact must fall out of the unlocked set.
        env.cipher.lockAll()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.unlocked.contains(env.peerContactId()))
        env.cleanup()
    }

    @Test
    fun `wrong passphrase on the decrypt unlock card shows a notice`() = runTest {
        val env = Env(StandardTestDispatcher(testScheduler))
        val envelope = env.envelopeFromPeer()
        val viewModel = env.buildViewModel()
        advanceUntilIdle()

        viewModel.beginDecrypt(envelope, TextSource.SELECTION, readOnly = true)
        advanceUntilIdle()
        // First wrong attempt on an era with no stored verifier yet: the
        // unlock ACTIVATES the bad key and the AEAD auth fails at decrypt
        // time — a Notice phase (the sheet's honest failure surface).
        viewModel.unlockAndDecrypt("definitely wrong".toCharArray())
        advanceUntilIdle()

        assertEquals(CompanionUiState.Phase.Notice, viewModel.state.value.phase)
        assertEquals(R.string.notice_decrypt_failed, viewModel.state.value.notice?.res)
        assertFalse(viewModel.state.value.busy)
        env.cleanup()
    }

    // ------------------------------------------------------ harness

    /** Scriptable store: wrap present, bytes delivered on demand. */
    private class ScriptedBiometrics(
        private val wrapped: Boolean,
    ) : BiometricKeyStore {
        var keyBytesToDeliver: ByteArray? = null
        override fun isDeviceCapable(): Boolean = true
        override fun strongAuthResult(): Int = 0
        override fun hasWrapped(contactKey: String): Boolean = wrapped
        override fun readWrapped(contactKey: String): ByteArray? =
            if (wrapped) ByteArray(64) else null
        override fun persistWrapped(contactKey: String, blob: ByteArray) {}
        override fun clear(contactKey: String) {}
        override fun clearAll() {}
        override fun newWrapCipher(): Cipher? = null
        override fun newUnwrapCipher(blob: ByteArray): Cipher? = null
        override fun finishWrap(cipher: Cipher, plain: ByteArray, aad: ByteArray): ByteArray = plain
        override fun finishUnwrap(cipher: Cipher, blob: ByteArray, aad: ByteArray): ByteArray = blob
    }

    private class FakeContactDao : ContactDao {
        val state = MutableStateFlow<List<ContactEntity>>(emptyList())
        override suspend fun upsert(contact: ContactEntity) {
            state.value = state.value.filterNot { it.id == contact.id } + contact
        }
        override fun observeAll() = flowOf(state.value.sortedBy { it.displayName })
        override suspend fun byId(id: String): ContactEntity? = state.value.firstOrNull { it.id == id }
        override suspend fun count(): Int = state.value.size
        override suspend fun delete(id: String) {
            state.value = state.value.filterNot { it.id == id }
        }
    }

    private class FakeSessionKeyDao : SessionKeyDao {
        val state = MutableStateFlow<List<SessionKeyEntity>>(emptyList())
        override suspend fun upsert(session: SessionKeyEntity) {
            state.value = state.value.filterNot { it.sessionId == session.sessionId } + session
        }
        override fun observeByContact(contactId: String) = flowOf(
            state.value
                .filter { it.contactId == contactId }
                .sortedWith(
                    compareByDescending<SessionKeyEntity> { it.createdAt }.thenByDescending { it.sessionId },
                ),
        )
        override suspend fun latestByContact(contactId: String): SessionKeyEntity? =
            state.value
                .filter { it.contactId == contactId }
                .sortedWith(
                    compareByDescending<SessionKeyEntity> { it.createdAt }.thenByDescending { it.sessionId },
                )
                .firstOrNull()
        override suspend fun byKeyId(keyId: ByteArray): SessionKeyEntity? =
            state.value.firstOrNull { it.keyId.contentEquals(keyId) }
        override suspend fun bySessionId(sessionId: String): SessionKeyEntity? =
            state.value.firstOrNull { it.sessionId == sessionId }
        override suspend fun updateWrappedKey(sessionId: String, wrapped: ByteArray?, now: Long) {
            state.value = state.value.map {
                if (it.sessionId == sessionId) it.copy(wrappedKey = wrapped, lastUsedAt = now) else it
            }
        }
        override suspend fun touch(sessionId: String, now: Long) {
            state.value = state.value.map {
                if (it.sessionId == sessionId) it.copy(lastUsedAt = now) else it
            }
        }
        override suspend fun deleteByContact(contactId: String) {
            state.value = state.value.filterNot { it.contactId == contactId }
        }
        override suspend fun countByContact(contactId: String): Int =
            state.value.count { it.contactId == contactId }
    }

    private class FakeHistoryDao : ClipboardHistoryDao {
        val entries = MutableStateFlow<List<ClipboardHistoryEntity>>(emptyList())
        override suspend fun upsert(entry: ClipboardHistoryEntity): Long {
            entries.value = entries.value + entry
            return entries.value.size.toLong()
        }
        override fun observeAll() = flowOf(entries.value)
        override suspend fun all(): List<ClipboardHistoryEntity> = entries.value
        override suspend fun deleteById(id: Long) {
            entries.value = entries.value.filterNot { it.id == id }
        }
        override suspend fun deleteByText(text: String) {
            entries.value = entries.value.filterNot { it.text == text }
        }
        override suspend fun clearAll() {
            entries.value = emptyList()
        }
        override suspend fun pruneOlderThan(cutoff: Long) {}
        override suspend fun keepNewest(keep: Int) {}
    }

    private class FakeClipboard : ClipboardAccess {
        override fun read(): ClipboardRead = ClipboardParser.parse(null)
        override fun currentText(): String? = null
        override fun setText(text: String): Boolean = false
    }

    /**
     * One fully wired environment: a RECEIVER device (the decrypting side)
     * plus a sender that produced one locked envelope for it.
     */
    private inner class Env(private val testDispatcher: TestDispatcher) {
        val contactDao = FakeContactDao()
        val sessionKeyDao = FakeSessionKeyDao()
        val time = TimeSource { 1_000_000L }
        val dispatchers = DispatcherProvider(
            io = Dispatchers.Unconfined,
            default = Dispatchers.Unconfined,
            main = Dispatchers.Unconfined,
        )
        val contacts = ContactRepository(contactDao, time, dispatchers)
        val sessionKeys = SessionKeyRepository(
            sessionKeyDao,
            ProtocolRandom(RandomSource.SECURE),
            time,
            dispatchers,
        )
        val sessions = KeySessionManager(
            derivator = Argon2KeyDerivator(),
            timeSource = time,
            deriveContext = Dispatchers.Unconfined,
        )
        val cipher = MessageCipher(
            contacts = contacts,
            sessionKeys = sessionKeys,
            sessions = sessions,
            protector = FixedKeyProtector(),
            provider = PassphraseCryptoProvider(),
            sessionTimeout = { 300_000L },
            dispatchers = dispatchers,
        )
        private val tmpDir = File.createTempFile("turnypt_env", "").let { dir ->
            dir.delete()
            dir.mkdirs()
            dir
        }
        private val dataStoreScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val settings = SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { File(tmpDir, "settings.preferences_pb") },
            ),
        )
        private val history = com.turnypt.data.repository.ClipboardHistoryRepository(
            FakeHistoryDao(),
            time,
            dispatchers,
        )

        // The receiver's contact ("Alice") whose era is locked; the sender
        // ("Bob") produced the envelope the sheet will be asked to decrypt.
        private val senderDao = FakeSessionKeyDao()
        private val senderSessions = KeySessionManager(
            derivator = Argon2KeyDerivator(),
            timeSource = time,
            deriveContext = Dispatchers.Unconfined,
        )
        private val senderContacts = ContactRepository(FakeContactDao(), time, dispatchers)
        private val senderKeys = SessionKeyRepository(
            senderDao,
            ProtocolRandom(RandomSource.SECURE),
            time,
            dispatchers,
        )
        private val senderCipher = MessageCipher(
            contacts = senderContacts,
            sessionKeys = senderKeys,
            sessions = senderSessions,
            protector = FixedKeyProtector(),
            provider = PassphraseCryptoProvider(),
            sessionTimeout = { 300_000L },
            dispatchers = dispatchers,
        )

        private var peerId: Contact? = null
        private var cachedEnvelope: String? = null
        private var cachedKey: ByteArray? = null

        suspend fun envelopeFromPeer(): String {
            cachedEnvelope?.let { return it }
            val bob = senderContacts.createContact("Bob")
            val era = senderKeys.createEra(bob.id, FAST_PARAMS)
            assertTrue(
                senderCipher.unlockContact(bob.id, passphrase.toCharArray())
                    is com.turnypt.data.repository.UnlockOutcome.Unlocked,
            )
            val out = senderCipher.encryptDraft(bob.id, "secret message")
            assertTrue(out is com.turnypt.data.repository.EncryptOutcome.Encrypted)
            cachedEnvelope = (out as com.turnypt.data.repository.EncryptOutcome.Encrypted).envelope

            // Import the SAME era into the receiver as Alice.
            val alice = contacts.createContact("Alice")
            sessionKeys.importEra(
                alice.id,
                com.turnypt.core.messages.InviteCodec.decode(era.inviteCode),
            )
            peerId = alice

            // The key a remembered wrap would hand back: both sides
            // derive the SAME key bytes for this era (shared passphrase).
            cachedKey = senderCipher.exportSessionKey(bob.id)
            return cachedEnvelope!!
        }

        /** The session key bytes a remembered wrap would hand back. */
        val rememberedKeyBytes: ByteArray
            get() = cachedKey!!.copyOf()

        fun peerContactId() = peerId!!.id

        var viewModel: CompanionViewModel? = null
            private set

        fun buildViewModel(
            biometrics: BiometricKeyStore = ScriptedBiometrics(wrapped = false),
        ): CompanionViewModel {
            // Seed: wrap offered + device capable, matching the report's
            // device where both affordances existed.
            val vm = CompanionViewModel(
                messageCipher = cipher,
                contactsRepository = contacts,
                sessionKeys = sessionKeys,
                settings = settings,
                clipboard = FakeClipboard(),
                clipboardRecorder = ClipboardHistoryRecorder(settings, FakeClipboard(), history),
                biometrics = biometrics,
            )
            viewModel = vm
            return vm
        }

        fun cleanup() {
            viewModel?.viewModelScope?.cancel()
            dataStoreScope.cancel()
        }
    }

    private companion object {
        val FAST_PARAMS = Argon2Params(memoryExponent = 3, iterations = 1, parallelism = 1)
    }
}
