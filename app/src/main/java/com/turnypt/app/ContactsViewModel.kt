package com.turnypt.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.turnypt.app.companion.UiNotice
import com.turnypt.app.security.BiometricDiagnostics
import com.turnypt.app.security.BiometricGate
import com.turnypt.app.security.BiometricKeyStore
import com.turnypt.app.security.BiometricPromptRequest
import com.turnypt.core.common.ContactId
import com.turnypt.core.sessions.wipe
import com.turnypt.data.preferences.SettingsRepository
import com.turnypt.data.repository.Contact
import com.turnypt.data.repository.ContactRepository
import com.turnypt.data.repository.ContactSession
import com.turnypt.data.repository.EraImport
import com.turnypt.data.repository.MessageCipher
import com.turnypt.data.repository.SessionKeyRepository
import com.turnypt.data.repository.UnlockOutcome
import com.turnypt.core.messages.Ck1FormatException
import com.turnypt.core.messages.InviteCodec
import com.turnypt.app.clipboard.ClipboardHistoryRecorder
import com.turnypt.security.clipboard.ClipboardAccess
import com.turnypt.security.policies.Argon2Profile

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactsRepository: ContactRepository,
    private val sessionKeys: SessionKeyRepository,
    private val messageCipher: MessageCipher,
    private val clipboard: ClipboardAccess,
    private val clipboardRecorder: ClipboardHistoryRecorder,
    private val biometricStore: BiometricKeyStore,
    private val settings: SettingsRepository,
) : ViewModel() {

    val contacts: kotlinx.coroutines.flow.StateFlow<List<Contact>> =
        contactsRepository.observeContacts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _unlocked = MutableStateFlow<Set<ContactId>>(emptySet())
    val unlocked = _unlocked.asStateFlow()

    private val _eras = MutableStateFlow<Map<ContactId, List<ContactSession>>>(emptyMap())
    val eras = _eras.asStateFlow()

    /** v0.4.2: typed feedback line (resource + arg) — was hardcoded English. */
    private val _feedback = MutableStateFlow<UiNotice?>(null)
    val feedback = _feedback.asStateFlow()

    /**
     * v1.6.0-beta.8 (user report: "when a contact is created and it says
     * 'contact created', then you leave the page and come back, it should
     * be gone — and it must not stick to the contact"): the CREATED notice
     * is one-shot. It is posted when the contact is actually created and
     * auto-dismisses after 4s; if the user leaves the Contacts tab before
     * that, the banner is dropped on the NEXT re-entry instead of living
     * in the ViewModel's state forever (the notice used to persist for as
     * long as the ViewModel survived — "stuck to the contact").
     *
     * [consumingFeedback] pulls the notice ONCE: the first collection
     * after a re-entry gets it, every later one sees null — so the banner
     * can never reappear on a later visit.
     */
    private val _ephemeralFeedback = MutableStateFlow<UiNotice?>(null)
    val ephemeralFeedback: kotlinx.coroutines.flow.StateFlow<UiNotice?> = _ephemeralFeedback.asStateFlow()

    /** The auto-dismiss job for the one-shot banner. */
    private var feedbackDismiss: kotlinx.coroutines.Job? = null

    /**
     * v1.6.0-beta.8: posts a ONE-SHOT success banner (create/delete):
     * shown to the first collector that arrives, auto-cleared after 4s.
     */
    private fun postEphemeralFeedback(notice: UiNotice) {
        feedbackDismiss?.cancel()
        // A fresh banner starts UNSEEN — the next entry into the tab
        // either displays it (the 4s timer still running) or drops it.
        pendingSeen.set(false)
        _ephemeralFeedback.value = notice
        feedbackDismiss = viewModelScope.launch {
            kotlinx.coroutines.delay(4_000)
            _ephemeralFeedback.value = null
        }
    }

    /** v1.6.0-beta.8: guards the one-shot consume against double drops. */
    private val pendingSeen = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * v1.6.0-beta.8: called on every re-entry into the Contacts tab —
     * consumes a one-shot banner that is still up (the user left before
     * the 4s timer) so a returning user never sees a stale "created"
     * line. A banner already displayed stays until its own timer.
     */
    fun consumeEphemeralFeedback() {
        if (pendingSeen.compareAndSet(false, true)) {
            _ephemeralFeedback.value = null
        }
    }

    private val _fingerprints = MutableStateFlow<Map<ContactId, String>>(emptyMap())
    val fingerprints = _fingerprints.asStateFlow()

    // ------------------------------------------------------------ biometric (v0.4.1)

    /** System-prompt request while the detail dialog is open (null = none). */
    private val _biometricPrompt = MutableStateFlow<BiometricPromptRequest?>(null)
    val biometricPrompt: StateFlow<BiometricPromptRequest?> = _biometricPrompt.asStateFlow()

    /** True when the contact dialog should offer "Use fingerprint". */
    private val _biometricOffered = MutableStateFlow(false)
    val biometricOffered: StateFlow<Boolean> = _biometricOffered.asStateFlow()

    /** Honest in-dialog notices (rendered where the user looks, not behind the scrim). */
    private val _dialogNotice = MutableStateFlow<UiNotice?>(null)
    val dialogNotice: StateFlow<UiNotice?> = _dialogNotice.asStateFlow()

    private var dialogContactId: ContactId? = null
    private var lastUnlockedContactId: ContactId? = null

    /**
     * v0.4.2 (deep-scan N2 guard): the "contactId:eraId" key the outstanding
     * fingerprint prompt unwraps — re-checked when the sensor confirms so a
     * wrap of a ROTATED era can never be activated as the new era's key.
     */
    private var pendingPromptKey: String? = null

    init {
        // v0.4.2 (deep-scan N1): lockAll() from Settings or a session timeout
        // used to leave this screen's `unlocked` snapshot stale until the
        // contacts list itself changed. Re-read on every session/era change.
        viewModelScope.launch {
            messageCipher.sessionChanges.collect { resync() }
        }
    }

    /**
     * v0.4.2: live re-read of the unlock state, plus the fingerprint offer
     * and key fingerprint of the contact whose detail dialog is on screen.
     */
    private fun resync() {
        viewModelScope.launch {
            refreshUnlocked()
            dialogContactId?.let { id ->
                refreshBiometricOffer(id)
                refreshFingerprint(id)
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            refreshUnlocked()
            contacts.value.forEach { loadEras(it.id, notify = false) }
        }
    }

    fun loadEras(contactId: ContactId, notify: Boolean = true) {
        viewModelScope.launch {
            val list = sessionKeys.erasForContact(contactId)
            _eras.update { it + (contactId to list) }
            refreshUnlocked()
            if (notify && list.isEmpty()) {
                _feedback.value = UiNotice(R.string.contact_no_era, null)
            }
        }
    }

    /**
     * Creates a contact and either imports the peer's invite (CKS1 code) or
     * generates a fresh key era whose invite the user then shares.
     *
     * The outcome is delivered to [onResult] as (ok, notice) so the ADD
     * DIALOG can show the DETAILED failure itself (deep-scan N3: the dialog
     * used to discard the reason and print a generic "creation failed"),
     * rendered through string resources instead of hardcoded English.
     */
    fun createContact(
        displayName: String,
        inviteText: String?,
        profile: Argon2Profile,
        onResult: (ok: Boolean, notice: UiNotice?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val contact = contactsRepository.createContact(displayName)
                val era = if (!inviteText.isNullOrBlank()) {
                    val invite = InviteCodec.decode(inviteText.trim())
                    when (val imported = sessionKeys.importEra(contact.id, invite)) {
                        is EraImport.Imported -> imported.era
                        is EraImport.AlreadyPresent -> imported.era
                        EraImport.KeyBelongsToOtherContact -> {
                            contactsRepository.deleteContact(contact.id)
                            val notice = UiNotice(R.string.contact_invite_key_conflict, null)
                            _feedback.value = notice
                            onResult(false, notice)
                            return@launch
                        }
                    }
                } else {
                    sessionKeys.createEra(contact.id, profile.params)
                }
                // v0.4.2 (deep-scan, era counter): seed the map from the era
                // we JUST created — the card used to flash "no key era" for
                // the new contact until the async loadEras query landed.
                _eras.update { it + (contact.id to listOf(era)) }
                loadEras(contact.id, notify = false)
                // v1.6.0-beta.8: one-shot banner — gone after 4s, never
                // again after a re-entry (the sticky _feedback line used
                // to greet the user on every return).
                postEphemeralFeedback(UiNotice(R.string.contact_feedback_created, null))
                _feedback.value = null
                onResult(true, null)
            } catch (e: Ck1FormatException) {
                val notice = UiNotice(R.string.contact_invalid_invite, e.message ?: "")
                _feedback.value = notice
                onResult(false, notice)
            } catch (e: IllegalArgumentException) {
                val notice = e.message?.let { UiNotice(R.string.contact_creation_invalid, it) }
                    ?: UiNotice(R.string.contact_creation_failed, null)
                _feedback.value = notice
                onResult(false, notice)
            }
        }
    }

    /**
     * Copies text (e.g. an invite code) through the ClipboardAccess
     * chokepoint (deep-scan finding 14) and reports whether it worked.
     * v0.5.2: public copies (invite codes) join the clipboard history.
     */
    fun copyText(text: String): Boolean {
        val copied = clipboard.setText(text)
        if (copied) {
            clipboardRecorder.onCopied(text)
        }
        return copied
    }

    /**
     * v0.5.2: copies a SUGGESTED PASSPHRASE — the sensitive clipboard path
     * (hidden preview on Android 13+), NEVER recorded in the clipboard
     * history (secrets must not persist on disk).
     */
    fun copySensitiveText(text: String): Boolean {
        val copied = clipboard.setSensitiveText(text)
        if (copied) {
            clipboardRecorder.onSensitiveCopied(text)
        }
        return copied
    }

    fun unlockContact(contactId: ContactId, passphrase: CharArray, onResult: (UnlockOutcome) -> Unit) {
        viewModelScope.launch {
            val outcome = messageCipher.unlockContact(contactId, passphrase)
            passphrase.wipe()
            refreshUnlocked()
            refreshFingerprint(contactId)
            onResult(outcome)
        }
    }

    /**
     * v0.4.1: opens the detail dialog — recomputes the fingerprint offer
     * for THIS contact/era and remembers whose dialog is on screen.
     */
    fun openDetail(contactId: ContactId) {
        dialogContactId = contactId
        _dialogNotice.value = null
        refreshBiometricOffer(contactId)
    }

    /** v0.4.1: gate + wrap presence for the current dialog contact. */
    fun refreshBiometricOffer(contactId: ContactId) {
        viewModelScope.launch {
            val enabled = settings.settings.first().biometricUnlockEnabled
            val gate = BiometricDiagnostics.gateFor(biometricStore.strongAuthResult())
            if (!enabled || gate != BiometricGate.AVAILABLE) {
                _biometricOffered.value = false
                return@launch
            }
            val key = biometricContactKeyFor(contactId)
                ?: run { _biometricOffered.value = false; return@launch }
            _biometricOffered.value = biometricStore.hasWrapped(key)
        }
    }

    /** v0.4.1: "Use fingerprint" in the dialog — show the system prompt. */
    fun requestBiometricUnlock() {
        val id = dialogContactId ?: return
        viewModelScope.launch {
            val key = biometricContactKeyFor(id) ?: return@launch
            if (!biometricStore.hasWrapped(key)) {
                // v0.4.2 (deep-scan N2): the visible offer was computed for
                // an older era — retract it instead of dead-tapping silently.
                _biometricOffered.value = false
                return@launch
            }
            _dialogNotice.value = null
            pendingPromptKey = key
            _biometricPrompt.value = BiometricPromptRequest.Unlock(key)
        }
    }

    /**
     * v0.4.1: outcome of the system prompt (null = canceled / failed).
     *
     * v0.5.1: [reason] carries the system's localized message for
     * non-cancel failures so the dialog notice explains what happened.
     */
    fun onBiometricUnlockResult(keyBytes: ByteArray?, reason: String? = null) {
        _biometricPrompt.value = null
        if (keyBytes == null) {
            _dialogNotice.value = reason?.let {
                UiNotice(R.string.notice_biometric_failed_reason, it)
            } ?: UiNotice(R.string.notice_biometric_failed, null)
            return
        }
        val id = dialogContactId
        if (id == null) {
            keyBytes.fill(0)
            return
        }
        viewModelScope.launch {
            // v0.4.2 (deep-scan N2 guard): the era may have rotated between
            // showing the prompt and touching the sensor — the unwrapped
            // bytes then belong to the OLD era and must never activate as
            // the new era's session key. Discard the wrap, say so honestly.
            val requestedKey = pendingPromptKey
            pendingPromptKey = null
            val currentKey = biometricContactKeyFor(id)
            if (requestedKey != null && currentKey != null && requestedKey != currentKey) {
                keyBytes.fill(0)
                biometricStore.clear(requestedKey)
                _dialogNotice.value = UiNotice(R.string.notice_biometric_stale, null)
                _biometricOffered.value = false
                return@launch
            }
            val key = biometricContactKeyFor(id)
            when (messageCipher.unlockContactWithKey(id, keyBytes)) {
                UnlockOutcome.Unlocked, UnlockOutcome.AlreadyUnlocked -> {
                    refreshUnlocked()
                    refreshFingerprint(id)
                    _dialogNotice.value = null
                    _biometricOffered.value = true
                }
                UnlockOutcome.NoSession ->
                    _dialogNotice.value = UiNotice(R.string.notice_no_era, null)
                else -> {
                    // Stale wrap (era rotated): discard it honestly.
                    key?.let { biometricStore.clear(it) }
                    _dialogNotice.value = UiNotice(R.string.notice_biometric_stale, null)
                    _biometricOffered.value = false
                }
            }
        }
    }

    /**
     * v0.4.1: after a passphrase unlock, offer to remember the key.
     *
     * v0.5.1 (reported "fingerprint didn't work anywhere"): the Settings
     * toggle no longer hides the offer — the system prompt ("Remember this
     * key" / "Not now") is the consent, and success persists the toggle
     * ON. Turning the toggle OFF still wipes every wrap.
     */
    fun offerRemember(contactId: ContactId) {
        viewModelScope.launch {
            val gate = BiometricDiagnostics.gateFor(biometricStore.strongAuthResult())
            if (gate != BiometricGate.AVAILABLE) return@launch
            val key = biometricContactKeyFor(contactId) ?: return@launch
            lastUnlockedContactId = contactId
            if (!biometricStore.hasWrapped(key)) {
                _biometricPrompt.value = BiometricPromptRequest.Remember(key)
            }
        }
    }

    /** v0.4.1: the key bytes the Remember prompt should wrap. */
    suspend fun consumeRememberKey(): ByteArray? {
        if (_biometricPrompt.value !is BiometricPromptRequest.Remember) return null
        val id = lastUnlockedContactId ?: return null
        return messageCipher.exportSessionKey(id)
    }

    /**
     * v0.4.1: prompt finished — [success] says whether the wrap persisted.
     *
     * v0.5.1: a persisted wrap turns the Settings toggle ON (the
     * authenticated "Remember this key" prompt IS the opt-in); a non-cancel
     * failure surfaces the system's reason instead of silence.
     */
    fun onRememberDone(success: Boolean, reason: String? = null) {
        _biometricPrompt.value = null
        _biometricOffered.value = _biometricOffered.value || success
        if (success) {
            viewModelScope.launch { settings.setBiometricUnlockEnabled(true) }
        } else if (reason != null) {
            _dialogNotice.value = UiNotice(R.string.notice_biometric_failed_reason, reason)
        }
    }

    /** `"contactId:eraId"` — the AAD and storage key for a remembered wrap. */
    private suspend fun biometricContactKeyFor(contactId: ContactId): String? {
        val eraId = messageCipher.currentEraId(contactId)?.value ?: return null
        return "${contactId.value}:$eraId"
    }

    fun lockContact(contactId: ContactId) {
        viewModelScope.launch {
            messageCipher.lockContact(contactId)
            refreshUnlocked()
            _fingerprints.update { it - contactId }
        }
    }

    fun lockAll() {
        viewModelScope.launch {
            messageCipher.lockAll()
            refreshUnlocked()
            _fingerprints.value = emptyMap()
        }
    }

    fun deleteContact(contactId: ContactId) {
        viewModelScope.launch {
            messageCipher.lockContact(contactId)
            contactsRepository.deleteContact(contactId)
            _eras.update { it - contactId }
            // Deep-scan finding 15: the fingerprint cache belonged to the
            // unlocked session that no longer exists.
            _fingerprints.update { it - contactId }
            // v1.6.0-beta.8: the deleted banner is one-shot too.
            postEphemeralFeedback(UiNotice(R.string.contact_feedback_deleted, null))
            _feedback.value = null
        }
    }

    private suspend fun refreshUnlocked() {
        // v1.5.9 (lag audit): the whole unlocked set in ONE manager lock
        // instead of one isUnlocked() mutex acquisition per contact. This
        // runs on every sessionChanges bump (an unlock, a lockAll, an
        // expiry sweep) — with N contacts the old loop was N serialized
        // lock round-trips racing the other collectors' identical loops.
        // The resulting Set is byte-for-byte the one the loop produced.
        val unlockedIds = messageCipher.unlockedContactIds()
        val known = contacts.value.map { it.id }
        _unlocked.value = known.filterTo(mutableSetOf()) { it in unlockedIds }
    }

    private suspend fun refreshFingerprint(contactId: ContactId) {
        val fingerprint = messageCipher.activeSessionFingerprint(contactId)
        if (fingerprint != null) {
            _fingerprints.update { it + (contactId to fingerprint) }
        } else {
            _fingerprints.update { it - contactId }
        }
    }
}
