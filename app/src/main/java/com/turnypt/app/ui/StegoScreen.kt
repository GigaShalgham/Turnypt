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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turnypt.app.R
import com.turnypt.app.StegoEmbedDone
import com.turnypt.app.ToolsViewModel
import com.turnypt.app.companion.ImageFiles
import com.turnypt.app.security.BiometricKeyStore
import com.turnypt.app.security.BiometricPromptHost
import com.turnypt.app.security.rememberBiometricPromptHelper

/**
 * Stego screen (stego plan §6.1 / S4), in the app's own v1.5.10 design
 * language since v1.6.0-beta.5: the SAME SectionCard sections, the SAME
 * DualSegmentRow pill the Encrypt/Decrypt tabs use (Hide | Reveal
 * instead of Text | Image), the SAME PickedImageCard for the chosen
 * carrier, the SAME PassphraseInput the unlock cards use, NoticeBanner
 * for failures, and an EncryptedFileCard-shaped result card with
 * Share-as-document / Clear.
 *
 * v1.6.0-beta.8 (the 2026-11-22 user decree, four parts):
 *
 *  1. OWN PASSPHRasse REMOVED. "Remove my own passphrase — contacts
 *     only": the Contact key | Own passphrase segment pair is GONE.
 *     Every seal rides a contact's era (hide) and every reveal resolves
 *     the contact from the carrier's trailer (reveal) — the ONLY key
 *     model on this screen now.
 *  2. SEPARATE CARRIERS. "Why is the carrier file shared between hide
 *     and reveal": it no longer is. The Hide half has its own carrier
 *     pick and the Reveal half has its own; toggling the segment never
 *     carries one file into the other half.
 *  3. SHARE SENDS THE REAL FILE. The result card's Share button now
 *     goes through the app FileProvider (a content uri with the
 *     carrier's own MIME - audio for a song, image for a picture)
 *     so Telegram/WhatsApp receive a real FILE, not a stream
 *     attachment that some apps silently re-encode or refuse.
 *  4. FINGERPRINT ON THIS SCREEN. The biometric prompt host and the
 *     "Use fingerprint" button the Encrypt/Decrypt tabs show live
 *     here too (the user report: "that page has no fingerprint
 *     button").
 *
 * v1.6.0-beta.7 lineage (kept): NO FILE-TYPE RESTRICTION — the SAF
 * picker launches with NO MIME filter; the ANY carrier (EOF-append)
 * rides everything, PNG/WAV keep their pixel-sample paths.
 */
@Composable
fun StegoScreen(viewModel: ToolsViewModel, biometrics: BiometricKeyStore) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var message by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // v1.6.0-beta.8: the fingerprint prompt host — the SAME machinery
    // the Encrypt/Decrypt tabs use, now on the stego screen too.
    val promptHelper = rememberBiometricPromptHelper(biometrics)
    BiometricPromptHost(
        helper = promptHelper,
        request = state.biometricPrompt,
        loadRememberKey = viewModel::consumeRememberKey,
        onUnlock = viewModel::onBiometricUnlockResult,
        onRememberDone = viewModel::onRememberDone,
    )

    // SAF: the HIDE side's carrier — NO MIME FILTER (the beta.7 decree:
    // every file, every extension; the ANY carrier handles them all).
    val pickHideCarrier = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.pickStegoCarrier(
                uri,
                ImageFiles.queryDisplayName(context, uri),
                ImageFiles.querySize(context, uri),
                ImageFiles.queryMimeType(context, uri),
                forReveal = false,
            )
        }
    }

    // SAF: the REVEAL side's own carrier — its OWN picker, its OWN slot
    // (the beta.8 decree: never shared with the Hide half).
    val pickRevealCarrier = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.pickStegoCarrier(
                uri,
                ImageFiles.queryDisplayName(context, uri),
                ImageFiles.querySize(context, uri),
                ImageFiles.queryMimeType(context, uri),
                forReveal = true,
            )
        }
    }

    // SAF: where the embedded carrier lands (CreateDocument, user-picked).
    val createStego = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            viewModel.embedStegoTo(uri, message, CharArray(0))
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

        if (!state.stegoExtractMode) {
            // ── The HIDE carrier — its own pick, every file ──────────
            SectionCard(
                title = stringResource(R.string.stego_step_carrier),
                icon = TurnyptIcons.Image,
            ) {
                Text(
                    stringResource(R.string.stego_carrier_hint_live),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val carrier = state.stegoHideCarrier
                if (carrier != null) {
                    PickedImageCard(
                        displayName = carrier.displayName,
                        sizeBytes = carrier.sizeBytes,
                        busy = state.stegoBusy,
                        onReplace = {
                            pickHideCarrier.launch(arrayOf("*/*"))
                        },
                    )
                } else {
                    Button(
                        onClick = {
                            pickHideCarrier.launch(arrayOf("*/*"))
                        },
                        enabled = !state.stegoBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.stego_pick_carrier)) }
                }
            }

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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── The contact — the ONLY key model (beta.8 decree) ────
            SectionCard(
                title = stringResource(R.string.stego_key_model_title),
                icon = TurnyptIcons.People,
            ) {
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
                    // v1.6.0-beta.8: the fingerprint shortcut the other
                    // tabs show — right on the locked contact card.
                    if (state.biometricOffered) {
                        OutlinedButton(
                            onClick = viewModel::requestBiometricUnlock,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.use_fingerprint)) }
                    }
                } else {
                    Text(
                        stringResource(R.string.stego_ecosystem_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            state.stegoHideCarrier?.let { carrier ->
                                createStego.launch(
                                    carrier.displayName.ifBlank { "carrier" },
                                )
                            }
                        },
                        enabled = !state.stegoBusy &&
                            state.stegoHideCarrier != null &&
                            contact != null &&
                            state.selectedContactId in state.unlocked,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.stego_embed_action)) }
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
                                // v1.6.0-beta.8 (user report: "the share
                                // button does not send it as a file, e.g.
                                // the song"): the share now goes through
                                // the app's FileProvider with the
                                // CARRIER'S OWN MIME — audio/* for a
                                // song, image/* for a picture — so the
                                // receiver's app treats it as a real
                                // file (a document send with the right
                                // type), not an anonymous octet-stream
                                // blob.
                                StegoShare.shareResult(
                                    context,
                                    result,
                                    state.stegoHideCarrier?.mime,
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
                            Text(stringResource(R.string.action_clear))
                        }
                    }
                }
            }
        } else {
            // ── The REVEAL carrier — its OWN pick, never the hide one ──
            SectionCard(
                title = stringResource(R.string.stego_step_carrier),
                icon = TurnyptIcons.Image,
            ) {
                Text(
                    stringResource(R.string.stego_reveal_carrier_hint_live),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val carrier = state.stegoRevealCarrier
                if (carrier != null) {
                    PickedImageCard(
                        displayName = carrier.displayName,
                        sizeBytes = carrier.sizeBytes,
                        busy = state.stegoBusy,
                        onReplace = {
                            pickRevealCarrier.launch(arrayOf("*/*"))
                        },
                    )
                } else {
                    Button(
                        onClick = {
                            pickRevealCarrier.launch(arrayOf("*/*"))
                        },
                        enabled = !state.stegoBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.stego_pick_reveal_carrier)) }
                }
            }

            // ── Reveal — the file names the contact itself ────────────
            SectionCard(
                title = stringResource(R.string.stego_key_model_title),
                icon = TurnyptIcons.People,
            ) {
                // The trailer NAMES the contact (its era keyHint);
                // unlock rides the SAME card the Decrypt tab shows for
                // locked eras, and the extract retries automatically
                // after a successful unlock.
                Text(
                    stringResource(R.string.stego_reveal_ecosystem_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        viewModel.extractStegoFrom(CharArray(0))
                    },
                    enabled = !state.stegoBusy && state.stegoRevealCarrier != null,
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
                    // v1.6.0-beta.8: the fingerprint shortcut on the
                    // reveal-side unlock card too.
                    if (state.biometricOffered) {
                        OutlinedButton(
                            onClick = viewModel::requestBiometricUnlock,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.use_fingerprint)) }
                    }
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
                        onClear = { viewModel.clearStegoCarrier(forReveal = true) },
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

/**
 * v1.6.0-beta.8 (user report: "the share button you put there does not
 * send it as a file, e.g. the song"): the stego result's share path.
 *
 * The old path handed the raw SAF uri with a hardcoded
 * `application/octet-stream` — many apps (Telegram included) treat that
 * as an anonymous blob: no file name, no type, sometimes silently
 * re-encoded. The fix goes through the app's OWN FileProvider:
 *
 *  1. the SAF output is copied into the app's private cache (a real
 *     file with the carrier's real name — song.mp3 stays song.mp3);
 *  2. the file is shared as a content uri with the carrier's OWN
 *     advertised MIME (audio for a song, image for a picture,
 *     video for a clip) — the receiver sees a genuine FILE of the
 *     type it already knows how to receive;
 *  3. the cache copy is marked delete-on-share (FLAG_GRANT_READ is
 *     enough for the receiver; the file wipes on the next embed or on
 *     app exit — the cache dir is the app's own).
 */
object StegoShare {

    /** The cap on one cache copy (matches the stego carrier ceiling). */
    private const val MAX_CACHE_BYTES = 256L * 1024L * 1024L

    /**
     * Copies [result]'s SAF output into the app cache and shares it as a
     * real file with the carrier's [carrierMime] (fallback: the honest
     * generic document type). Falls back to a direct SAF grant share
     * (still as a file) when the copy fails — never to a text/blob send.
     */
    fun shareResult(
        context: android.content.Context,
        result: StegoEmbedDone,
        carrierMime: String?,
    ) {
        val uri = android.net.Uri.parse(result.outputUriString)
        val cache = runCatching { copyToCache(context, uri, result.displayName) }
            .getOrNull()
        val fileUri = cache?.let { file ->
            runCatching {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            }.getOrNull()
        }
        if (fileUri != null) {
            ImageShare.uri(context, fileUri, resolveMime(carrierMime, cache), null)
            return
        }
        // Copy failed (huge file / stream hiccup): grant-share the SAF
        // uri directly, still typed as a file — never a blob.
        ImageShare.uri(
            context,
            uri,
            resolveMime(carrierMime, null),
            context.getString(R.string.stego_preview_share),
        )
    }

    /**
     * The MIME the share carries: the carrier's advertised type when the
     * system knows one, else the honest application/octet-stream (the
     * file IS bytes; the name carries what it is).
     */
    private fun resolveMime(carrierMime: String?, cache: java.io.File?): String {
        if (!carrierMime.isNullOrBlank() && carrierMime != "*/*") return carrierMime
        return runCatching {
            cache?.let { android.webkit.MimeTypeMap.getFileExtensionFromUrl(it.name) }
                ?.let { ext ->
                    android.webkit.MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(ext.lowercase())
                }
        }.getOrNull() ?: "application/octet-stream"
    }

    /**
     * Copies the SAF output into a fresh cache file named after the
     * carrier (bounded by [MAX_CACHE_BYTES]; a larger file stays on the
     * direct-grant path — the messenger streams it from SAF).
     */
    private fun copyToCache(
        context: android.content.Context,
        uri: android.net.Uri,
        displayName: String,
    ): java.io.File? {
        val safe = displayName.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "carrier"
        val target = java.io.File(context.cacheDir, "stego_share_${System.currentTimeMillis()}_$safe")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val size = input.available()
            if (size > MAX_CACHE_BYTES) return null
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        return target
    }
}

