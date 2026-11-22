package com.turnypt.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turnypt.app.R
import com.turnypt.app.ToolsViewModel
import com.turnypt.app.companion.ImageFiles

/**
 * Stego screen (stego plan §6.1 / S4): REAL wiring since v1.6.0-beta.4 —
 * SAF carrier pick, passphrase-only Cks2 embed into PNG/WAV, extraction
 * with the two-step boot, and the share-ready card with the document-
 * mode guidance (plan §3.1's three transfer tiers live here as text;
 * the Settings matrix lands with S5).
 *
 * The screen keeps the ecosystem's contact picker as UX sugar (whose
 * carrier this is) but the CONTAINER consults no contact, no era, no
 * session — passphrase-only per plan §4.4. The passphrase field below
 * IS the key.
 */
@Composable
fun StegoScreen(viewModel: ToolsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var message by rememberSaveable { mutableStateOf("") }
    var passphrase by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // SAF: pick the carrier (PNG or WAV) for embed or extract.
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
            viewModel.embedStegoTo(uri, message, passphrase.toCharArray())
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

        // ── Mode: hide | reveal ───────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { viewModel.setStegoExtractMode(false) },
                enabled = state.stegoExtractMode,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.stego_mode_hide)) }
            OutlinedButton(
                onClick = { viewModel.setStegoExtractMode(true) },
                enabled = !state.stegoExtractMode,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.stego_mode_reveal)) }
        }

        // ── Step 1: the carrier ────────────────────────────────────────
        SectionCard(
            title = stringResource(R.string.stego_step_carrier),
            icon = TurnyptIcons.Image,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.stego_carrier_hint_live),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val carrier = state.stegoCarrier
                if (carrier != null) {
                    CarrierPickedRow(
                        name = carrier.displayName,
                        format = carrierFormatOf(carrier.mime),
                        onClear = viewModel::clearStegoCarrier,
                    )
                } else {
                    OutlinedButton(
                        onClick = {
                            pickCarrier.launch(
                                arrayOf(
                                    "image/png",
                                    "audio/x-wav",
                                    "audio/wav",
                                    "application/octet-stream",
                                ),
                            )
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

        // ── S5: the three transfer tiers, always visible (plan §6.1) ──
        SectionCard(
            title = stringResource(R.string.stego_tier_title),
            icon = TurnyptIcons.Shield,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.stego_tier_direct),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.stego_tier_document),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.stego_tier_never),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (!state.stegoExtractMode) {
            // ── Step 2: the hidden message ─────────────────────────────
            SectionCard(
                title = stringResource(R.string.stego_step_message),
                icon = TurnyptIcons.Shield,
            ) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringResource(R.string.stego_message_label)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Step 3: the passphrase (the ONLY key — plan §4.4) ─────
            SectionCard(
                title = stringResource(R.string.stego_passphrase_title),
                icon = TurnyptIcons.Lock,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.stego_passphrase_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(stringResource(R.string.stego_passphrase_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── The action ────────────────────────────────────────────
            Button(
                onClick = {
                    createStego.launch(
                        state.stegoCarrier?.displayName ?: "carrier.png",
                    )
                },
                enabled = state.stegoCarrier != null &&
                    message.isNotEmpty() &&
                    passphrase.isNotEmpty() &&
                    !state.stegoBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.stego_embed_action))
            }

            state.stegoNotice?.let { StegoNoticeRow(notice = it) }

            // ── The share-ready result card ──────────────────────────
            state.stegoResult?.let { result ->
                SectionCard(
                    title = stringResource(R.string.stego_preview_title),
                    icon = TurnyptIcons.CheckCircle,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(
                            text = stringResource(
                                R.string.stego_preview_ready,
                                result.carrierKind,
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
                                onClick = {
                                    ImageShare.uri(
                                        context,
                                        android.net.Uri.parse(result.outputUriString),
                                        "application/octet-stream",
                                        context.getString(R.string.stego_preview_share),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.stego_preview_share))
                            }
                            OutlinedButton(
                                onClick = { viewModel.clearStegoCarrier() },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.stego_preview_reset))
                            }
                        }
                    }
                }
            }
        } else {
            // ── Reveal mode ──────────────────────────────────────────
            SectionCard(
                title = stringResource(R.string.stego_passphrase_title),
                icon = TurnyptIcons.Lock,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(stringResource(R.string.stego_passphrase_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            viewModel.extractStegoFrom(passphrase.toCharArray())
                        },
                        enabled = state.stegoCarrier != null &&
                            passphrase.isNotEmpty() &&
                            !state.stegoBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.stego_reveal_action))
                    }
                }
            }

            state.stegoNotice?.let { StegoNoticeRow(notice = it) }

            state.stegoExtracted?.let { text ->
                SectionCard(
                    title = stringResource(R.string.stego_revealed_title),
                    icon = TurnyptIcons.CheckCircle,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text, style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(
                            onClick = { viewModel.clearStegoCarrier() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.stego_preview_reset))
                        }
                    }
                }
            }
        }

        // v1.6.0-beta.1: the glass-era clearance spacer is gone — the
        // docked Scaffold NavigationBar reserves its own space.
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** Notices ride the shared UiNotice shape (res + arg, resolved at render). */
@Composable
private fun StegoNoticeRow(notice: com.turnypt.app.companion.UiNotice) {
    val text = notice.arg?.let {
        stringResource(notice.res, it)
    } ?: stringResource(notice.res)
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/** Maps a picked carrier's MIME to the display format. */
private fun carrierFormatOf(mime: String?): StegoCarrierFormat = when {
    mime == null -> StegoCarrierFormat.PNG
    mime.contains("wav", ignoreCase = true) -> StegoCarrierFormat.WAV
    else -> StegoCarrierFormat.PNG
}

/** Carrier formats the stego plan §3.2 matrix covers. */
enum class StegoCarrierFormat(val label: String) {
    PNG("PNG image"),
    WAV("WAV audio"),
}

/** Picked-carrier row: format chip, name, clear. */
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
        StatusChip(
            text = format.label,
            positive = true,
            icon = TurnyptIcons.CheckCircle,
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onClear) {
            Text(stringResource(R.string.stego_carrier_clear))
        }
    }
}
