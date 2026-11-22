package com.turnypt.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turnypt.app.R
import com.turnypt.app.ToolsViewModel
import com.turnypt.app.companion.ImageFiles

/**
 * Stego screen (stego plan §6.1 / S4), in the app's own v1.5.10 design
 * language since v1.6.0-beta.5: the SAME SectionCard sections, the SAME
 * DualSegmentRow pill the Encrypt/Decrypt tabs use (Hide | Reveal
 * instead of Text | Image), the SAME PickedImageCard for the chosen
 * carrier, the SAME PassphraseInput the unlock cards use, NoticeBanner
 * for failures, and an EncryptedFileCard-shaped result card with
 * Share-as-document / Clear.
 *
 * v1.6.0-beta.7 (the 2026-11-22 user decree, two parts):
 *
 *  1. NO FILE-TYPE RESTRICTION. The SAF picker launches with NO MIME
 *     filter — every file, every extension, grey nothing. The ANY
 *     carrier (EOF-append) rides everything: PNG/WAV keep their
 *     pixel-sample paths, every other file gets the sealed CKE1 trailer
 *     after its untouched bytes.
 *  2. A MEMBER OF THE TURNYPT ECOSYSTEM. The new second segment
 *     (Contact key | Own passphrase) routes the SAME contact picker,
 *     the SAME unlock card and the SAME era/timeout discipline the
 *     Encrypt tab uses: hide-for-contact seals for the contact's era,
 *     reveal resolves the contact from the trailer and unlocks through
 *     the standard card. The passphrase-only model (plan §4.4) stays
 *     as the second choice.
 */
@Composable
fun StegoScreen(viewModel: ToolsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var message by rememberSaveable { mutableStateOf("") }
    // The pending stego passphrase — held only for the interaction that typed
    // it (deliberately NOT saveable: view-state never outlives the field),
    // because PassphraseInput clears itself on submit.
    var pendingStegoPassphrase by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<CharArray?>(null)
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // SAF: pick the carrier — NO MIME FILTER (the beta.7 decree: every
    // file, every extension; the ANY carrier handles them all).
    val pickCarrier = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.pickStegoCarrier(
                uri,
                ImageFiles.queryDisplayName(context, uri),
                ImageFiles.querySize(context, uri),
                ImageFiles.queryMimeType(context, uri),
            )
        }
    }

    // SAF: where the embedded carrier lands (CreateDocument, user-picked).
    val createStego = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            viewModel.embedStegoTo(uri, message, pendingStegoPassphrase ?: CharArray(0))
            pendingStegoPassphrase = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.stego_title),
            subtitle = stringResource(R.string.stego_subtitle_live),
        )

        // ── Hide | Reveal — the same pill as Text | Image ────────────
        DualSegmentRow(
            firstSelected = !state.stegoExtractMode,
            firstLabel = stringResource(R.string.stego_mode_hide),
            firstIcon = TurnyptIcons.Shield,
            onFirstSelected = { viewModel.setStegoExtractMode(false) },
            secondLabel = stringResource(R.string.stego_mode_reveal),
            secondIcon = TurnyptIcons.CheckCircle,
            onSecondSelected = { viewModel.setStegoExtractMode(true) },
        )

        BusyLine(visible = state.stegoBusy)

        // ── The carrier — every file, every extension ──────────────
        SectionCard(
            title = stringResource(R.string.stego_step_carrier),
            icon = TurnyptIcons.Image,
        ) {
            Text(
                stringResource(R.string.stego_carrier_hint_live),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val carrier = state.stegoCarrier
            if (carrier != null) {
                PickedImageCard(
                    displayName = carrier.displayName,
                    sizeBytes = carrier.sizeBytes,
                    busy = state.stegoBusy,
                    onReplace = {
                        pickCarrier.launch(arrayOf("*/*"))
                    },
                )
            } else {
                Button(
                    onClick = {
                        pickCarrier.launch(arrayOf("*/*"))
                    },
                    enabled = !state.stegoBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.stego_pick_carrier)) }
            }
        }

        if (!state.stegoExtractMode) {
            // ── The hidden message — the input section's shape ────────
            SectionCard(
                title = stringResource(R.string.stego_step_message),
                icon = TurnyptIcons.Shield,
            ) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    placeholder = { Text(stringResource(R.string.stego_message_label)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── The key model — Contact key | Own passphrase ────────
            SectionCard(
                title = stringResource(R.string.stego_key_model_title),
                icon = TurnyptIcons.People,
            ) {
                DualSegmentRow(
                    firstSelected = state.stegoEcosystemMode,
                    firstLabel = stringResource(R.string.stego_key_contact),
                    firstIcon = TurnyptIcons.People,
                    onFirstSelected = { viewModel.setStegoEcosystemMode(true) },
                    secondLabel = stringResource(R.string.stego_key_passphrase),
                    secondIcon = TurnyptIcons.Lock,
                    onSecondSelected = { viewModel.setStegoEcosystemMode(false) },
                )
                if (state.stegoEcosystemMode) {
                    // The SAME contact picker the Encrypt tab uses — the
                    // hidden message rides the contact's era (unlock,
                    // timeout, rotation discipline all included).
                    ContactPicker(
                        contacts = state.contacts,
                        selectedContactId = state.selectedContactId,
                        unlocked = state.unlocked,
                        onContactSelected = viewModel::selectContact,
                    )
                    val contact = state.selectedContact
                    if (contact != null && state.selectedContactId !in state.unlocked) {
                        // The SAME unlock card the Encrypt tab shows —
                        // the ecosystem route needs the era unlocked
                        // BEFORE the hide action runs.
                        Text(
                            stringResource(R.string.stego_locked_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PassphraseInput(
                            label = stringResource(R.string.passphrase_label),
                            buttonLabel = stringResource(R.string.unlock_encrypt_action),
                            busy = state.busy,
                            onSubmit = viewModel::unlockAndContinue,
                        )
                    } else {
                        Text(
                            stringResource(R.string.stego_ecosystem_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                state.stegoCarrier?.let { carrier ->
                                    createStego.launch(
                                        carrier.displayName.ifBlank { "carrier" },
                                    )
                                }
                            },
                            enabled = !state.stegoBusy &&
                                state.stegoCarrier != null &&
                                contact != null &&
                                state.selectedContactId in state.unlocked,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.stego_embed_action)) }
                    }
                } else {
                    // ── The passphrase-only model (plan §4.4) ────────
                    Text(
                        stringResource(R.string.stego_passphrase_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PassphraseInput(
                        label = stringResource(R.string.stego_passphrase_label),
                        buttonLabel = stringResource(R.string.stego_embed_action),
                        busy = state.stegoBusy,
                        onSubmit = { passphrase ->
                            pendingStegoPassphrase = passphrase
                            state.stegoCarrier?.let { carrier ->
                                createStego.launch(
                                    carrier.displayName.ifBlank { "carrier.png" },
                                )
                            }
                        },
                    )
                }
            }

            // ── The result card — EncryptedFileCard's shape ──────────
            state.stegoResult?.let { result ->
                SectionCard(
                    title = stringResource(R.string.stego_preview_title),
                    icon = TurnyptIcons.FileDoc,
                ) {
                    Text(
                        stringResource(R.string.stego_result_saved_as, result.displayName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.stego_result_details, result.carrierKind),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.stego_preview_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                ImageShare.uri(
                                    context,
                                    Uri.parse(result.outputUriString),
                                    "application/octet-stream",
                                    context.getString(R.string.stego_preview_share),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.stego_preview_share))
                        }
                        OutlinedButton(
                            onClick = viewModel::clearStegoCarrier,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.action_clear))
                        }
                    }
                }
            }
        } else {
            // ── Reveal — the ecosystem first, the passphrase second ──
            SectionCard(
                title = stringResource(R.string.stego_key_model_title),
                icon = TurnyptIcons.People,
            ) {
                DualSegmentRow(
                    firstSelected = state.stegoEcosystemMode,
                    firstLabel = stringResource(R.string.stego_key_contact),
                    firstIcon = TurnyptIcons.People,
                    onFirstSelected = { viewModel.setStegoEcosystemMode(true) },
                    secondLabel = stringResource(R.string.stego_key_passphrase),
                    secondIcon = TurnyptIcons.Lock,
                    onSecondSelected = { viewModel.setStegoEcosystemMode(false) },
                )
                if (state.stegoEcosystemMode) {
                    // Reveal for a contact: the trailer NAMES the contact
                    // (its era keyHint); unlock rides the SAME card the
                    // Decrypt tab shows for locked eras, and the extract
                    // retries automatically after a successful unlock.
                    Text(
                        stringResource(R.string.stego_reveal_ecosystem_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            viewModel.extractStegoFrom(CharArray(0))
                        },
                        enabled = !state.stegoBusy && state.stegoCarrier != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.stego_reveal_action)) }
                    if (state.unlockNeeded && state.unlockContactName != null) {
                        // The SAME unlock card shape — the contact the
                        // trailer named is locked on this device.
                        PassphraseInput(
                            label = stringResource(R.string.passphrase_label),
                            buttonLabel = stringResource(R.string.unlock_encrypt_action),
                            busy = state.busy,
                            onSubmit = viewModel::unlockAndContinue,
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.stego_passphrase_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PassphraseInput(
                        label = stringResource(R.string.stego_passphrase_label),
                        buttonLabel = stringResource(R.string.stego_reveal_action),
                        busy = state.stegoBusy,
                        onSubmit = { passphrase ->
                            viewModel.extractStegoFrom(passphrase)
                        },
                    )
                }
            }

            // ── The revealed message — ResultBlock's shape ─────────
            state.stegoExtracted?.let { text ->
                SectionCard(
                    title = stringResource(R.string.stego_revealed_title),
                    icon = TurnyptIcons.CheckCircle,
                ) {
                    ResultBlock(
                        text = text,
                        monospace = false,
                        copied = state.copied,
                        onCopy = viewModel::copyResult,
                        onClear = viewModel::clearStegoCarrier,
                    )
                }
            }
        }

        // Stego failures ride the SAME NoticeBanner every tab uses.
        NoticeBanner(state.stegoNotice)

        // v1.5.6: the screen's own clearance under the floating glass
        // bar — 96dp clears the ~64dp pane plus its 12dp vertical inset,
        // while scrolled content still slides beneath the glass.
        Spacer(modifier = Modifier.height(96.dp))
    }
}
