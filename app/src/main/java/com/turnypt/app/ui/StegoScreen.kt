package com.turnypt.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turnypt.app.R
import com.turnypt.app.ToolsViewModel

/**
 * Stego frame, UI-only (stego plan §6.1 / S4): the skeleton the plan ships
 * first — every surface of the Phase 1 embed flow (carrier step, message
 * step, preview card) laid out in the app's v1.4 "Liquid Glass" component
 * set, with NO crypto, NO BitSpread, NO container behind it. Wiring to a
 * future :core:stego module comes only after the S0/S1 gates pass.
 *
 * v1.6.0-alpha.3 (user request: "no shared passphrase — this option must
 * follow the Turnypt ecosystem completely, using the same contacts
 * system"): the standalone passphrase section is GONE. The screen now
 * takes its recipient exactly like Encrypt/Decrypt do — the shared
 * ContactPicker plus, when the selected contact is locked, the SAME
 * unlock card (passphrase + fingerprint + suggestion gate) the rest of
 * the app uses, driven by the shared ToolsViewModel. Nothing here keeps
 * its own key material: when embedding eventually lands, the hidden
 * message will be sealed with the selected contact's session key — the
 * ecosystem's existing envelope — exactly like every other flow.
 */
@Composable
fun StegoScreen(viewModel: ToolsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var carrierPicked by rememberSaveable { mutableStateOf(false) }
    var carrierName by rememberSaveable { mutableStateOf("") }
    var carrierFormat by rememberSaveable { mutableStateOf(StegoCarrierFormat.PNG) }
    var message by rememberSaveable { mutableStateOf("") }
    var showPreview by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.stego_title),
            subtitle = stringResource(R.string.stego_subtitle),
        )

        if (state.contacts.isEmpty()) {
            EmptyState(
                icon = TurnyptIcons.People,
                title = stringResource(R.string.contacts_empty_title),
                body = stringResource(R.string.contacts_empty_body),
            )
        }

        // ── Step 1: the carrier ────────────────────────────────────────
        SectionCard(
            title = stringResource(R.string.stego_step_carrier),
            icon = TurnyptIcons.Image,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.stego_carrier_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (carrierPicked) {
                    CarrierPickedRow(
                        name = carrierName,
                        format = carrierFormat,
                        onClear = {
                            carrierPicked = false
                            carrierName = ""
                        },
                    )
                } else {
                    OutlinedButton(
                        onClick = {
                            // UI frame only: no SAF picker is wired yet —
                            // the real carrier adapters land with S4 of
                            // the stego plan.
                            carrierPicked = true
                            carrierName = "carrier.png"
                            carrierFormat = StegoCarrierFormat.PNG
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.stego_pick_carrier))
                    }
                }
            }
        }

        // ── Step 2: the recipient — the Turnypt ecosystem, not a
        // standalone passphrase. Same ContactPicker, same lock state
        // per contact, same unlock flow as Encrypt/Decrypt. ──────────
        SectionCard(
            title = stringResource(R.string.encrypt_contact_label),
            icon = TurnyptIcons.People,
        ) {
            ContactPicker(
                contacts = state.contacts,
                selectedContactId = state.selectedContactId,
                unlocked = state.unlocked,
                onContactSelected = viewModel::selectContact,
            )
        }

        // The shared unlock card: shown exactly when the selected contact
        // is locked — the same PassphraseInput (+ fingerprint offer, +
        // honest suggestion gate) the Encrypt tab renders, reading the
        // same ToolsViewModel state. Mirrors the private UnlockCard in
        // EncryptScreen.kt (kept private there deliberately; this screen
        // follows its shape rather than widening its visibility).
        if (state.selectedContact != null && state.selectedContactId !in state.unlocked) {
            SectionCard(
                title = stringResource(
                    R.string.unlock_for_title,
                    state.unlockContactName ?: state.selectedContact!!.displayName,
                ),
                icon = TurnyptIcons.Lock,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.locked_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PassphraseInput(
                        label = stringResource(R.string.passphrase_label),
                        buttonLabel = stringResource(R.string.unlock_encrypt_action),
                        busy = state.busy,
                        onSubmit = viewModel::unlockAndContinue,
                        // v0.5.2: the suggestion appears at the password-entry
                        // moment, but only when this side is CHOOSING the
                        // passphrase (a locally created era, never unlocked)
                        // — an imported era means the peer already picked it.
                        allowSuggestion = state.suggestPassphrase,
                        onCopySensitive = viewModel::copyPassphrase,
                    )
                    if (state.biometricOffered) {
                        OutlinedButton(
                            onClick = viewModel::requestBiometricUnlock,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.use_fingerprint)) }
                    }
                }
            }
        }

        // ── Step 3: the hidden message ────────────────────────────────
        SectionCard(
            title = stringResource(R.string.stego_step_message),
            icon = TurnyptIcons.Shield,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringResource(R.string.stego_message_label)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ── The action ────────────────────────────────────────────────
        Button(
            onClick = { showPreview = true },
            enabled = carrierPicked &&
                state.selectedContact != null &&
                state.selectedContactId in state.unlocked &&
                message.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.stego_embed_action))
        }
        Text(
            stringResource(R.string.stego_coming_soon),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ── Preview (what the completion card will become) ────────────
        if (showPreview) {
            SectionCard(
                title = stringResource(R.string.stego_preview_title),
                icon = TurnyptIcons.CheckCircle,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(
                        text = stringResource(
                            R.string.stego_preview_ready,
                            carrierFormat.label,
                        ),
                        positive = true,
                        icon = TurnyptIcons.CheckCircle,
                    )
                    Text(
                        stringResource(R.string.stego_preview_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { /* real share intent arrives with S4 */ },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.stego_preview_share))
                        }
                        OutlinedButton(
                            onClick = { showPreview = false },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.stego_preview_reset))
                        }
                    }
                }
            }
        }

        // The screen's own clearance under the floating glass — the same
        // 96dp clearance every scrolling screen carries.
        Spacer(modifier = Modifier.height(96.dp))
    }
}

/** Carrier formats the stego plan §3.2 matrix covers. */
enum class StegoCarrierFormat(val label: String) {
    PNG("PNG image"),
    JPEG("JPEG image"),
    WAV("WAV audio"),
    MP4("MP4 video"),
}

/** Picked-carrier row: format chip, name, ready hint. */
@Composable
private fun CarrierPickedRow(
    name: String,
    format: StegoCarrierFormat,
    onClear: () -> Unit,
) {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.material3.FilterChip(
            selected = true,
            onClick = onClear,
            label = { Text(format.label) },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.stego_carrier_ready),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
