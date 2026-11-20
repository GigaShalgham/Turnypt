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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.turnypt.app.R
import com.turnypt.app.ui.theme.GlassRadii
import com.turnypt.app.ui.theme.LocalGlassTokens
import com.turnypt.app.ui.theme.glassSurface

/**
 * Stego frame, UI-only (stego plan §6.1 / S4): the SKELETON the plan ships
 * first — every surface of the Phase 1 embed flow (carrier step, passphrase
 * step, message step, preview card) laid out in the app's v1.4 "Liquid
 * Glass" component set, with NO crypto, NO BitSpread, NO container behind
 * it. The controls hold local state and nothing else; wiring to a future
 * :core:stego module comes only after the S0/S1 gates pass.
 *
 * v1.4 note: this screen follows the glass rules from theme/Glass.kt by
 * using the shared SectionCard (a glass panel) rather than building its
 * own surfaces — one visual language, same as Encrypt/Decrypt.
 */
@Composable
fun StegoScreen() {
    var carrierPicked by rememberSaveable { mutableStateOf(false) }
    var carrierName by rememberSaveable { mutableStateOf("") }
    var carrierFormat by rememberSaveable { mutableStateOf(StegoCarrierFormat.PNG) }
    var passphrase by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var showPreview by rememberSaveable { mutableStateOf(false) }

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

        // ── Step 2: the shared passphrase ──────────────────────────────
        SectionCard(
            title = stringResource(R.string.stego_step_passphrase),
            icon = TurnyptIcons.Key,
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
                    singleLine = true,
                    visualTransformation =
                        androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                PassphraseStrengthHint(passphrase)
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
            enabled = carrierPicked && passphrase.isNotEmpty() && message.isNotEmpty(),
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

        // v1.5.6: the NavHost no longer cuts its viewport above the bar,
        // so this spacer is the screen's own clearance under the floating
        // glass — same 96dp clearance every scrolling screen carries.
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
    val glass = LocalGlassTokens.current
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
