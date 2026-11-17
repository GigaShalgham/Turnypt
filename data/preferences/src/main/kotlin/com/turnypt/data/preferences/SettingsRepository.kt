package com.turnypt.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.turnypt.security.policies.Argon2Profile
import com.turnypt.security.policies.KeyPolicy

private val Context.turnyptDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "turnypt_settings",
)

/**
 * v0.6.0: the user's cipher preference for NEW encryptions (text and
 * files alike). The algorithm travels in every envelope and file header,
 * so this is purely a SENDING choice — decryption always follows the wire
 * and never depends on it.
 */
enum class CipherPreference {
    /** Measure this device once and use the faster cipher (default). */
    AUTO,

    /** Always AES-256-GCM (hardware-accelerated on modern SoCs). */
    AES_256_GCM,

    /** Always ChaCha20-Poly1305 (constant-time, fast in pure software). */
    CHACHA20_POLY1305,

    ;

    companion object {
        fun fromName(name: String?): CipherPreference? =
            entries.firstOrNull { it.name == name }
    }
}

/**
 * v1.5.2 (user ruling, "once and for all"): the custom file-extension
 * setting is a SINGLE toggle — "پسوند کاستوم". OFF (the default) keeps
 * Turnypt's ecosystem suffixes (.tnpi images / .cki others). ON names
 * EVERY newly encrypted file with the ONE unifying suffix: .TNP —
 * images AND text/other files alike, no exceptions, no per-type
 * branching, no user-entered suffix value.
 *
 * Purely a NAMING preference: the file's content (the CKF1 magic) decides
 * what it is, decryption never depends on the name, and every file ever
 * written under .tnpi/.cki keeps opening exactly as before.
 */
object CustomFileExtension {

    /** v1.5.2: the ONE suffix the toggle applies — ".tnp", every file. */
    const val UNIFYING_EXTENSION: String = "tnp"

    /** Shortest sane suffix ("x"); kept for legacy-value sanitization. */
    const val MIN_LENGTH: Int = 1

    /** Longest sane suffix — a filename, not a sentence. */
    const val MAX_LENGTH: Int = 16

    /** The reserved suffixes that stay Turnypt's own, never user-settable. */
    val RESERVED: Set<String> = setOf("cki", "tnpi")

    /**
     * Sanitizes a user-entered suffix into a safe one, or null when it
     * cannot be salvaged. A leading dot is accepted and stripped ("​.secret"
     * -> "secret"); letters, digits, hyphens and underscores survive;
     * whitespace is trimmed; anything else (dots inside, path separators,
     * control characters, unicode whitespace tricks) rejects the input —
     * a filename suffix must round-trip through every filesystem and SAF
     * provider without surprises. Reserved Turnypt suffixes are refused so
     * a custom value can never masquerade as the defaults.
     *
     * v1.5.2: kept only to re-validate any value persisted by the v1.3.2–
     * v1.5.1 "type your own suffix" UI on read (see [SettingsRepository]);
     * the setting no longer accepts user input.
     */
    fun sanitize(raw: String): String? {
        val trimmed = raw.trim().removePrefix(".").trim()
        if (trimmed.length !in MIN_LENGTH..MAX_LENGTH) return null
        if (trimmed.any { !it.isLetterOrDigit() && it != '-' && it != '_' }) return null
        if (trimmed.lowercase() in RESERVED) return null
        return trimmed.lowercase()
    }
}

/**
 * User-facing security settings.
 *
 * Defaults come from [KeyPolicy]/[Argon2Profile]; persisted values are
 * re-validated on read so that a corrupted or forged store cannot loosen the
 * policy.
 */
data class AppSettings(
    val sessionTimeoutMillis: Long = KeyPolicy.DEFAULT_SESSION_TIMEOUT_MILLIS,
    val argon2Profile: Argon2Profile = Argon2Profile.DEFAULT,
    val onboardingCompleted: Boolean = false,
    /** v0.3.0: remember unlocked keys behind a fingerprint-gated wrap. */
    val biometricUnlockEnabled: Boolean = false,
    /** v0.3.0: floating assistant overlay (opt-in, off by default). */
    val assistantEnabled: Boolean = false,
    /** v0.5.2: clipboard history capture (on by default; sensitive copies are never stored). */
    val clipboardHistoryEnabled: Boolean = true,
    /** v0.6.0: cipher used for NEW encryptions (default Automatic). */
    val preferredCipher: CipherPreference = CipherPreference.AUTO,
    /**
     * v1.3.2: name NEW encrypted files with the user's custom suffix
     * instead of Turnypt's (.tnpi images / .cki others). OFF by default —
     * the ecosystem suffixes stay the default experience.
     *
     * v1.5.2 (user ruling): the suffix is no longer user-entered — ON
     * names EVERY new encrypted file with the unifying .tnp suffix
     * ([CustomFileExtension.UNIFYING_EXTENSION]), images and others
     * alike. The stored-value field is kept only so a value persisted by
     * the v1.3.2–v1.5.1 UI migrates cleanly: any old value is simply
     * superseded by tnp wherever the toggle is read.
     */
    val customFileExtensionEnabled: Boolean = false,
    /**
     * v1.3.2 legacy: the sanitized suffix applied when the toggle is ON.
     * v1.5.2: informational only — consumers resolve the effective suffix
     * through [effectiveCustomSuffix], which is always
     * [CustomFileExtension.UNIFYING_EXTENSION] when enabled.
     */
    val customFileExtension: String = "",
) {
    /**
     * v1.5.2: the ONE effective custom suffix when the toggle is ON —
     * always "tnp", whatever legacy value may be stored. When OFF there
     * is no custom suffix at all (null), and the ecosystem defaults
     * (.tnpi / .cki) apply.
     */
    val effectiveCustomSuffix: String?
        get() = if (customFileExtensionEnabled) CustomFileExtension.UNIFYING_EXTENSION else null
}

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            // nearestTimeoutChoice: a persisted value that is not one of the
            // picker choices (e.g. v0.2's 1 h cap) still maps to a row.
            sessionTimeoutMillis = KeyPolicy.nearestTimeoutChoice(
                prefs[KEY_SESSION_TIMEOUT] ?: KeyPolicy.DEFAULT_SESSION_TIMEOUT_MILLIS,
            ),
            argon2Profile = Argon2Profile.fromName(prefs[KEY_ARGON2_PROFILE])
                ?: Argon2Profile.DEFAULT,
            onboardingCompleted = prefs[KEY_ONBOARDING_COMPLETED] ?: false,
            biometricUnlockEnabled = prefs[KEY_BIOMETRIC_UNLOCK] ?: false,
            assistantEnabled = prefs[KEY_ASSISTANT_ENABLED] ?: false,
            clipboardHistoryEnabled = prefs[KEY_CLIPBOARD_HISTORY] ?: true,
            preferredCipher = CipherPreference.fromName(prefs[KEY_PREFERRED_CIPHER])
                ?: CipherPreference.AUTO,
            // v1.3.2: re-validate on read — a corrupted or forged store
            // value falls back to the blank default (the toggle's OFF
            // state then simply keeps the ecosystem suffixes).
            customFileExtensionEnabled = prefs[KEY_CUSTOM_EXT_ENABLED] ?: false,
            customFileExtension = prefs[KEY_CUSTOM_EXT]?.let(CustomFileExtension::sanitize)
                ?: "",
        )
    }

    suspend fun setSessionTimeoutMillis(requestedMillis: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_SESSION_TIMEOUT] = KeyPolicy.clampSessionTimeout(requestedMillis)
        }
    }

    suspend fun setArgon2Profile(profile: Argon2Profile) {
        dataStore.edit { prefs ->
            prefs[KEY_ARGON2_PROFILE] = profile.name
        }
    }

    suspend fun setOnboardingCompleted() {
        dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = true
        }
    }

    /** v0.3.0: toggles fingerprint-gated key wrapping (see app/security). */
    suspend fun setBiometricUnlockEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_BIOMETRIC_UNLOCK] = enabled
        }
    }

    /** v0.3.0: toggles the floating assistant service. */
    suspend fun setAssistantEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ASSISTANT_ENABLED] = enabled
        }
    }

    /** v0.5.2: toggles clipboard-history capture (never stores sensitive copies). */
    suspend fun setClipboardHistoryEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_CLIPBOARD_HISTORY] = enabled
        }
    }

    /** v0.6.0: the cipher preference for new encryptions. */
    suspend fun setPreferredCipher(preference: CipherPreference) {
        dataStore.edit { prefs ->
            prefs[KEY_PREFERRED_CIPHER] = preference.name
        }
    }

    /**
     * v1.3.2: toggles the custom file-extension naming.
     *
     * v1.5.2 (user ruling): a SINGLE toggle with no companion value —
     * ON means every new encrypted file is named with the unifying .tnp
     * suffix. Any legacy stored suffix (v1.3.2–v1.5.1 UIs) is wiped on
     * every flip so "off" means off and "on" means tnp, never a
     * resurrected old value.
     */
    suspend fun setCustomFileExtensionEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_EXT_ENABLED] = enabled
            prefs.remove(KEY_CUSTOM_EXT)
        }
    }

    /**
     * v1.3.2: stores the custom suffix (sanitized; rejected input throws
     * [IllegalArgumentException] so the Settings row can show the honest
     * error instead of silently keeping the old value).
     *
     * v1.5.2: no longer called by any UI — the setting has no
     * user-entered value any more. Kept (deprecated) so external callers
     * compile and behave honestly: a stored value is superseded by tnp
     * at read time (see [AppSettings.effectiveCustomSuffix]).
     *
     * @deprecated the v1.5.2 setting is a pure toggle.
     */
    @Deprecated("The setting is a single toggle; .tnp is applied automatically.")
    suspend fun setCustomFileExtension(raw: String) {
        val sanitized = CustomFileExtension.sanitize(raw)
            ?: throw IllegalArgumentException("invalid file extension: $raw")
        dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_EXT] = sanitized
        }
    }

    /**
     * Sticky companion contact: the last contact the user encrypted for,
     * so the PROCESS_TEXT encrypt flow usually needs zero extra taps
     * (spec 003, FR-005).
     *
     * Pure storage — no existence validation here (this module cannot see
     * Room). Callers must clamp: if the id no longer matches a contact,
     * treat it as null.
     */
    val lastContactId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_CONTACT_ID]
    }

    suspend fun setLastContactId(contactId: String?) {
        dataStore.edit { prefs ->
            if (contactId == null) {
                prefs.remove(KEY_LAST_CONTACT_ID)
            } else {
                prefs[KEY_LAST_CONTACT_ID] = contactId
            }
        }
    }

    companion object {
        private val KEY_SESSION_TIMEOUT = longPreferencesKey("session_timeout_millis")
        private val KEY_ARGON2_PROFILE = stringPreferencesKey("argon2_profile")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_LAST_CONTACT_ID = stringPreferencesKey("last_contact_id")
        private val KEY_BIOMETRIC_UNLOCK = booleanPreferencesKey("biometric_unlock_enabled")
        private val KEY_ASSISTANT_ENABLED = booleanPreferencesKey("assistant_enabled")
        private val KEY_CLIPBOARD_HISTORY = booleanPreferencesKey("clipboard_history_enabled")
        private val KEY_PREFERRED_CIPHER = stringPreferencesKey("preferred_cipher")
        private val KEY_CUSTOM_EXT_ENABLED = booleanPreferencesKey("custom_file_extension_enabled")
        private val KEY_CUSTOM_EXT = stringPreferencesKey("custom_file_extension")

        /** Factory for the process-wide DataStore instance. */
        fun defaultDataStore(context: Context): DataStore<Preferences> = context.turnyptDataStore
    }
}
