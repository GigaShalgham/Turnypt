package com.turnypt.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.turnypt.data.preferences.AppSettings
import com.turnypt.data.preferences.CipherPreference
import com.turnypt.data.preferences.SettingsRepository
import com.turnypt.data.repository.ClipboardHistoryRepository
import com.turnypt.data.repository.MessageCipher
import com.turnypt.app.assistant.AssistantReadiness
import com.turnypt.app.assistant.FloatingAssistantService
import com.turnypt.app.security.BiometricDiagnostics
import com.turnypt.app.security.BiometricGate
import com.turnypt.core.crypto.AutomaticCipherSelector
import com.turnypt.core.crypto.CipherAlgorithm
import com.turnypt.security.policies.Argon2Profile

/**
 * One-shot UI events raised by Settings interactions (v0.3.0) — consumed
 * by the screen so navigation/side effects survive recomposition.
 */
sealed interface SettingsEvent {
    /** Open the system "Display over other apps" screen for Turnypt. */
    data object RequestOverlayPermission : SettingsEvent

    /**
     * v1.3.2: the custom-file-extension toggle was turned ON with no valid
     * suffix stored yet — the row asks for one (the field is the same row).
     */
    data object CustomFileExtensionNeedsValue : SettingsEvent

    /** v1.3.2: the entered suffix is invalid (the row shows the honest error). */
    data object CustomFileExtensionInvalid : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val biometrics: com.turnypt.app.security.BiometricKeyStore,
    private val messageCipher: MessageCipher,
    private val clipboardHistory: ClipboardHistoryRepository,
    private val automaticCipher: AutomaticCipherSelector,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /**
     * v0.4.1: the honest biometric gate (null = not probed yet). Replaces the
     * bare boolean: every "can't do it" case now carries its own reason.
     */
    private val _biometricGate = MutableStateFlow<BiometricGate?>(null)
    val biometricGate = _biometricGate.asStateFlow()

    /** True while the overlay permission is granted (re-checked on resume). */
    private val _overlayGranted = MutableStateFlow(false)
    val overlayGranted = _overlayGranted.asStateFlow()

    /**
     * v0.6.0: what the cipher preference currently resolves to on THIS
     * device (the Automatic benchmark runs at most once per process).
     */
    private val _resolvedCipher = MutableStateFlow<CipherAlgorithm?>(null)
    val resolvedCipher = _resolvedCipher.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    init {
        refresh()
    }

    /** Re-reads the hardware/permission state and re-evaluates the
     * assistant lifecycle (called on init and every screen resume).
     *
     * Deep-scan F2: when the user enables the assistant without the overlay
     * permission and grants it in system settings, the service must start
     * as soon as they return — not on the next app launch. Re-running this
     * on resume makes the "enabled + granted" state converge to
     * "service running" idempotently (startForegroundService on an
     * already-running service is a no-op update). */
    fun refresh() {
        viewModelScope.launch {
            // v0.4.1: STRONG-class probe with a honest reason per failure —
            // v0.4.0 accepted WEAK|STRONG here while the wrap key demanded
            // STRONG, so weak sensors passed the gate and then failed.
            _biometricGate.value =
                BiometricDiagnostics.gateFor(biometrics.strongAuthResult())
            val overlay = android.provider.Settings.canDrawOverlays(context)
            _overlayGranted.value = overlay
            val enabled = settingsRepository.settings.first().assistantEnabled
            if (AssistantReadiness.shouldAutoStart(enabled, overlay)) {
                runCatching { FloatingAssistantService.start(context) }
            }
            refreshResolvedCipher()
        }
    }

    /** v0.6.0: resolve the cipher preference (benchmark off the main thread). */
    private fun refreshResolvedCipher() {
        viewModelScope.launch {
            val preference = settingsRepository.settings.first().preferredCipher
            _resolvedCipher.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                when (preference) {
                    CipherPreference.AES_256_GCM -> CipherAlgorithm.AES_256_GCM
                    CipherPreference.CHACHA20_POLY1305 -> CipherAlgorithm.CHACHA20_POLY1305
                    CipherPreference.AUTO -> automaticCipher.select()
                }
            }
        }
    }

    /** v0.6.0: change the cipher preference for new encryptions. */
    fun setPreferredCipher(preference: CipherPreference) {
        viewModelScope.launch {
            settingsRepository.setPreferredCipher(preference)
            refreshResolvedCipher()
        }
    }

    /**
     * v1.3.2: the "custom file extension" toggle. Invalid input is refused
     * with an honest error string (resolvable) instead of a silent no-op —
     * and a REFUSED value never disables what already works, so the row
     * keeps its previous state on error.
     */
    fun setCustomFileExtensionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // Enabling requires a valid stored suffix: with none saved yet,
                // the caller must save one first (the Settings row's flow).
                val current = settingsRepository.settings.first().customFileExtension
                if (current.isBlank()) {
                    _events.emit(SettingsEvent.CustomFileExtensionNeedsValue)
                    return@launch
                }
            }
            settingsRepository.setCustomFileExtensionEnabled(enabled)
        }
    }

    /** v1.3.2: store the custom suffix; null on invalid input (row shows the error). */
    fun setCustomFileExtension(raw: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setCustomFileExtension(raw)
            } catch (e: IllegalArgumentException) {
                _events.emit(SettingsEvent.CustomFileExtensionInvalid)
            }
        }
    }

    fun setSessionTimeoutMinutes(minutes: Int) {
        setSessionTimeoutMillis(minutes * 60_000L)
    }

    /**
     * v1.3.1 (lock-time fix): the change must reach LIVE sessions too, not
     * just the next unlock. Before this, the timeout was read exactly once
     * (at unlock) and frozen into an absolute deadline — tightening from
     * 24 h to 1 min left already-unlocked sessions live for hours.
     *
     * Persist first, then re-arm: every live session's countdown restarts
     * from NOW under the new value; sessions already past the new (tighter)
     * deadline are wiped immediately. The sessionChanges counter bumps, so
     * every open screen re-reads who is unlocked.
     */
    fun setSessionTimeoutMillis(millis: Long) {
        viewModelScope.launch {
            settingsRepository.setSessionTimeoutMillis(millis)
            messageCipher.rearmSessionTimeout(millis)
        }
    }

    fun setArgon2Profile(profile: Argon2Profile) {
        viewModelScope.launch {
            settingsRepository.setArgon2Profile(profile)
        }
    }

    /**
     * v0.3.0: fingerprint unlock toggle. Turning it OFF wipes every
     * remembered wrap and the Keystore wrap key — "off" must mean the
     * data is gone, not merely hidden.
     */
    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && _biometricGate.value != BiometricGate.AVAILABLE) {
                // Refuse honestly — the row renders the exact reason below
                // the toggle instead of a silent no-op.
                return@launch
            }
            if (!enabled) {
                biometrics.clearAll()
            }
            settingsRepository.setBiometricUnlockEnabled(enabled)
        }
    }

    /**
     * v0.3.0: floating assistant toggle. Also starts/stops the service so
     * the change is immediately visible, not just after the next launch.
     */
    fun setAssistantEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAssistantEnabled(enabled)
            val action = AssistantReadiness
                .actionFor(enabled, android.provider.Settings.canDrawOverlays(context))
            when (action) {
                AssistantReadiness.Action.StartService ->
                    runCatching { FloatingAssistantService.start(context) }
                AssistantReadiness.Action.StopService ->
                    FloatingAssistantService.stop(context)
                AssistantReadiness.Action.None ->
                    _events.emit(SettingsEvent.RequestOverlayPermission)
            }
        }
    }

    /** Locks every unlocked session right now (panic affordance). */
    fun lockAllNow() {
        viewModelScope.launch {
            messageCipher.lockAll()
        }
    }

    /** v0.5.2: clipboard-history capture toggle. */
    fun setClipboardHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setClipboardHistoryEnabled(enabled)
        }
    }

    /** v0.5.2: wipes the clipboard history from Settings. */
    fun clearClipboardHistory() {
        viewModelScope.launch {
            clipboardHistory.clear()
        }
    }

    fun consumeEvent() {
        // SharedFlow consumes implicitly; kept for call-site symmetry.
    }

    fun markOnboardingComplete() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted()
        }
    }
}
