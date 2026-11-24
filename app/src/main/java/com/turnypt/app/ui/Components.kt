package com.turnypt.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turnypt.app.R
import com.turnypt.app.companion.UiNotice
import com.turnypt.app.ui.theme.LocalGlassTokens
import com.turnypt.app.ui.theme.LocalSemanticColors
import com.turnypt.app.ui.theme.glassSurface
import com.turnypt.app.ui.theme.GlassRadii
import com.turnypt.security.policies.PassphraseAdvisor

/**
 * Shared building blocks — v1.4.0 "Liquid Glass": the v0.5 "Calm Vault"
 * component set re-expressed as glass. Same names, same call signatures,
 * same a11y contract (state = icon + color, never color alone; interactive
 * targets ≥ 48dp; quick tiles 88dp) — the material changed, the language
 * did not.
 *
 * The glass rules (see theme/Glass.kt): every surface is the ONE
 * [glassSurface] chain (tint + specular bevel + refraction rim) over the
 * quiet two-tone backdrop; corner radii come from [GlassRadii]; text keeps
 * its Material-role on-color so contrast never depends on the backdrop;
 * motion is spring-based and subtle (no continuous animation).
 */

/**
 * v1.5.10 (lag audit): the shared 50%-radius pill shape, built once.
 * StatusChip sits inside EVERY ContactCard, so the shape used to be
 * re-allocated per contact on every list recomposition. Identical
 * geometry; only the allocation is gone.
 */
private val FiftyPercentCorner = RoundedCornerShape(percent = 50)

/**
 * Titled glass card group used by every screen.
 *
 * v1.4.0: the tonal container becomes a glass panel — [glassSurface] with
 * the card radius, the title's leading icon still in the primary color.
 * Signature and semantics unchanged from v0.5.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable () -> Unit,
) {
    val glass = LocalGlassTokens.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(glass, GlassRadii.cardShape),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                icon?.let {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

/**
 * Status pill ("Unlocked: 2" / "Contacts: 0").
 *
 * v1.4.0: a glass pill — the chip-radius [glassSurface] — with the state
 * inset as a solid tonal dot area: a small SUCCESS/secondary container
 * circle behind the leading icon keeps the state readable from icon +
 * color alone (never color only), while the pill itself stays neutral
 * glass. Same signature as v0.5.
 */
@Composable
fun StatusChip(
    text: String,
    positive: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val semantic = LocalSemanticColors.current
    val glass = LocalGlassTokens.current
    val stateBg = if (positive) {
        semantic.successContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val stateFg = if (positive) {
        semantic.onSuccessContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Box(
        modifier = modifier.glassSurface(
            glass,
            // v1.5.10 (lag audit): hoisted file-level constant — this chip
            // sits inside EVERY ContactCard, so the old per-recomposition
            // RoundedCornerShape allocation ran per contact on every list
            // recomposition. A 50% corner radius is the same pill shape.
            FiftyPercentCorner,
        ),
    ) {
        Row(
            // v1.5.5: symmetric padding — the old start=8/end=12 (an RTL
            // artifact of the icon's medallion overhang) read as the
            // text being pushed off-centre inside the pill.
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Surface(
                    color = stateBg,
                    shape = CircleShape,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = stateFg,
                        modifier = Modifier
                            .padding(3.dp)
                            .size(12.dp),
                    )
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Quick-action tile (Home grid): a glass tile with a circular medallion
 * over a two-line label, at least 88dp tall.
 *
 * v1.4.0: glass panel (card radius) + a primary-tinted medallion; the
 * press feedback is a spring SCALE (0.97f) — a physical "glass gives"
 * press instead of ripple-only. Touch target and hierarchy unchanged.
 */
@Composable
fun QuickActionTile(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val glass = LocalGlassTokens.current
    // v1.5.7 perf: pressed-state tracking runs on the ONE gesture
    // pipeline. The tile previously wired BOTH clickable's
    // interactionSource AND a private pointerInput/awaitEachGesture
    // coroutine per tile — two hit-test pipelines for every tile on
    // Home, and the raw-pointer path could race the clickable one
    // (double press flips, stuck 0.96 scale on cancellation edge
    // cases). clickable already reports presses through its
    // interactionSource; collectIsPressedAsState() is the built-in
    // observer for exactly that. One pipeline, zero custom coroutines,
    // the same deferred-read scale animation.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // v1.5.0 perf: deferred read — the press scale repaints one layer;
    // the tile content never recomposes. v1.5.2 (user report: "add
    // animation everywhere"): a little settle on release — the tile
    // springs back with one soft overshoot instead of snapping flat.
    // v1.5.7 (user report: "the app feels laggy"): the release settle
    // ran at StiffnessMedium — a ~400ms tail on a 96%-scale press, a
    // lingering shimmer after every tap. Re-tuned to the app's shared
    // ~280ms beat (stiffness 1200) so the glass settles WITH the tap
    // instead of dragging after it; same soft-overshoot character.
    val scale = animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 1200f,
            visibilityThreshold = 0.01f,
        ),
        label = "tilePress",
    )
    val shape = GlassRadii.cardShape
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .glassSurface(glass, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        // v1.5.5 (user report: "the values in each button on the Home
        // screen are not in the button's centre"): a Box without
        // contentAlignment pins its child to the top-start corner, so the
        // medallion + label sat huddled in the tile's corner with the
        // rest dead space — glaring on wide tiles (tablets, landscape,
        // two-per-row weights). Center the content BOTH axes.
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(22.dp),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Shared empty-state block — icon + title + body on a quiet glass panel,
 * centered, with an optional call to action.
 *
 * v1.4.0: glass panel; the icon sits in a frosted medallion (tintHigh)
 * so the glyph's outline color reads on both modes. CTA contract from
 * v0.5 unchanged — an empty state is where new users land first, and the
 * direct pathway stays.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val glass = LocalGlassTokens.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(glass, GlassRadii.cardShape),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                color = glass.tintHigh,
                shape = CircleShape,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(28.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.size(4.dp))
                Button(onClick = onAction) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Read-only result block (envelope or plaintext) with copy/clear row.
 *
 * v1.4.0: the inset becomes a raised glass layer (tintHigh at the field
 * radius) — one glass over another is the Liquid Glass depth cue that
 * replaced the v0.5 tonal tier. Monospace envelopes still render in
 * bodySmall; buttons keep the 48dp row.
 */
@Composable
fun ResultBlock(
    text: String,
    monospace: Boolean,
    copied: Boolean,
    onCopy: () -> Unit,
    onClear: () -> Unit,
) {
    val glass = LocalGlassTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(glass, GlassRadii.fieldShape, tint = glass.tintHigh),
        ) {
            SelectionContainer {
                Text(
                    text = text,
                    style = if (monospace) {
                        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (copied) {
            Text(
                stringResourceCompat(R.string.notice_copied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Text(stringResourceCompat(R.string.action_copy))
            }
            OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                Text(stringResourceCompat(R.string.action_clear))
            }
        }
    }
}

/** Passphrase input + submit row shared by every unlock card.
 *
 * v0.5.2: [allowSuggestion] adds the "Suggest a strong passphrase"
 * affordance right at the password-entry moment (user request) — but only
 * where it is honest: the caller passes it only when THIS side is choosing
 * the passphrase (a locally created era that was never unlocked). An
 * imported era means the peer already picked the passphrase.
 */
@Composable
fun PassphraseInput(
    label: String,
    buttonLabel: String,
    busy: Boolean,
    onSubmit: (CharArray) -> Unit,
    allowSuggestion: Boolean = false,
    onCopySensitive: ((String) -> Boolean)? = null,
) {
    var passphrase by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text(label) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Password,
                // v1.6.0-beta.8 (user decree): every text input gets a
                // Done key — tapping it closes the keyboard.
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        // v0.4.0: live strength hint while the user types.
        PassphraseStrengthHint(passphrase)
        if (allowSuggestion && onCopySensitive != null) {
            SuggestPassphraseSection(
                onUse = { passphrase = it },
                onCopySensitive = onCopySensitive,
            )
        }
        Button(
            onClick = {
                onSubmit(passphrase.toCharArray())
                passphrase = ""
            },
            enabled = passphrase.isNotEmpty() && !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) { Text(buttonLabel) }
    }
}

/**
 * v0.5.2: the strong-passphrase suggestion, AT the password-entry point
 * (user request: "you should offer it when entering the password").
 * Collapsed by default; "Use" fills the field above, "New one" rerolls,
 * "Copy" uses the SENSITIVE clipboard path and never lands in the
 * clipboard history.
 */
@Composable
fun SuggestPassphraseSection(
    onUse: (String) -> Unit,
    onCopySensitive: (String) -> Boolean,
) {
    var visible by remember { mutableStateOf(false) }
    if (!visible) {
        TextButton(onClick = { visible = true }) {
            Text(stringResourceCompat(R.string.passphrase_suggest_action))
        }
        return
    }
    var suggestion by remember { mutableStateOf(PassphraseAdvisor.recommend()) }
    var copied by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SelectionContainer {
            Text(
                suggestion,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                // v1.5.2 (user report: monospace blobs read as raw black):
                // the suggested passphrase carries the primary tone — a
                // value the user is meant to notice and use, tinted like
                // the invite codes, not default-black monospace.
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            stringResourceCompat(
                R.string.contact_suggestion_bits,
                PassphraseAdvisor.GUARANTEED_BITS,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onUse(suggestion) }) {
                Text(stringResourceCompat(R.string.passphrase_suggestion_use))
            }
            TextButton(onClick = {
                suggestion = PassphraseAdvisor.recommend()
                copied = false
            }) {
                Text(stringResourceCompat(R.string.passphrase_suggestion_new))
            }
            TextButton(onClick = { copied = onCopySensitive(suggestion) }) {
                Text(stringResourceCompat(R.string.action_copy))
            }
            TextButton(onClick = { visible = false }) {
                Text(stringResourceCompat(R.string.contact_suggest_hide))
            }
        }
        if (copied) {
            Text(
                stringResourceCompat(R.string.passphrase_suggestion_copied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            stringResourceCompat(R.string.passphrase_suggestion_first_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * v0.4.0: live strength estimate under a passphrase field. Display only —
 * it never blocks submission (that stays with KeyPolicy on the crypto
 * path); it teaches what a strong shared passphrase looks like while
 * typing.
 *
 * v0.5: STRONG now renders in the SUCCESS semantic tone (green), FAIR
 * keeps the warm ochre tertiary, WEAK stays error red.
 */
@Composable
fun PassphraseStrengthHint(passphrase: String) {
    if (passphrase.isEmpty()) return
    val semantic = LocalSemanticColors.current
    val strength = remember(passphrase) { PassphraseAdvisor.strength(passphrase) }
    val bits = remember(passphrase) { PassphraseAdvisor.estimateBits(passphrase) }
    val (labelRes, color) = when (strength) {
        PassphraseAdvisor.Strength.WEAK ->
            R.string.passphrase_strength_weak to MaterialTheme.colorScheme.error
        PassphraseAdvisor.Strength.FAIR ->
            R.string.passphrase_strength_fair to MaterialTheme.colorScheme.tertiary
        PassphraseAdvisor.Strength.STRONG ->
            R.string.passphrase_strength_strong to semantic.success
    }
    Text(
        text = stringResourceCompat(
            R.string.passphrase_strength_summary,
            stringResourceCompat(labelRes),
            bits,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

/** Screen title + subtitle header. */
@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Centered busy row ("Decrypting…"). */
@Composable
fun BusyRow(label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The four notice tones the UI distinguishes (docs/DESIGN.md §3.4). */
enum class NoticeTone { INFO, SUCCESS, WARNING, ERROR }

/**
 * Inline notice banner for typed notices.
 *
 * v1.4.0: glass banner (chip radius) + a solid TONAL STATE INSET — the
 * icon sits in a filled circle of the tone's container color. The four
 * tones (INFO/SUCCESS/WARNING/ERROR) keep their v0.5 semantic mapping and
 * remain readable from icon + tone, never tone alone; default stays
 * ERROR (what a raw notice has always meant at existing call sites).
 */
@Composable
fun NoticeBanner(
    notice: UiNotice?,
    tone: NoticeTone = NoticeTone.ERROR,
) {
    if (notice == null) return
    val text = notice.arg?.let { arg ->
        stringResourceCompat(notice.res, arg)
    } ?: stringResourceCompat(notice.res)
    val semantic = LocalSemanticColors.current
    val glass = LocalGlassTokens.current
    val (insetBg, insetFg, icon) = when (tone) {
        NoticeTone.INFO -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            TurnyptIcons.Info,
        )
        NoticeTone.SUCCESS -> Triple(
            semantic.successContainer,
            semantic.onSuccessContainer,
            TurnyptIcons.CheckCircle,
        )
        NoticeTone.WARNING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            TurnyptIcons.Warning,
        )
        NoticeTone.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            TurnyptIcons.Warning,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(glass, GlassRadii.chipShape),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(color = insetBg, shape = CircleShape) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = insetFg,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(14.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * v0.5: a labeled settings switch row — title, optional summary, and the
 * switch as the trailing control. The v0.3-v0.4 Settings showed the
 * literal text "On"/"Off" next to the switch, which read as a status
 * display instead of the feature's name; a control must be labeled by
 * what it controls.
 *
 * v1.4.0: the switch sits in a pill-shaped raised-glass well (tintHigh,
 * chip radius) — the Liquid Glass take on an inset control plate. The row
 * keeps its ≥48dp target and labels.
 */
@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    val glass = LocalGlassTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .glassSurface(
                    glass,
                    GlassRadii.chipShape,
                    tint = glass.tintHigh,
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        }
    }
}

/**
 * v0.5: contact avatar — the contact's initial on a tonal circle, green
 * when unlocked (the SUCCESS channel), quiet grey when locked.
 *
 * v1.4.0: the circle becomes a glass medallion: the glass tint over a
 * SOLID state under-fill (unlocked keeps its unmistakable green channel —
 * state color must not shift with the backdrop), locked stays neutral
 * glass. Same 40dp size and reading.
 *
 * v1.6.0-beta.9 (user report: "the initial of a contact name sits off
 * center — top-anchored, not in the middle"): the initial now centers
 * HONESTLY. `wrapContentSize(Center)` was never the problem — the glyph
 * box was: `titleMedium` carries a tall line height plus Android's
 * includeFontPadding ascent/descent, so the VISIBLE glyph rode high
 * inside the 40dp circle (worst for Latin caps; barely visible for
 * Persian). The fix is the standard Compose recipe, all three parts:
 * a Box with contentAlignment(Center), a style whose lineHeight EQUALS
 * its fontSize (no extra leading), and PlatformTextStyle with
 * includeFontPadding=false (no top/bottom font padding on API<29).
 */
@Composable
fun ContactAvatar(
    name: String,
    unlocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalSemanticColors.current
    val glass = LocalGlassTokens.current
    val stateFill = if (unlocked) semantic.successContainer else glass.tintHigh
    val fg = if (unlocked) semantic.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .size(40.dp)
            .background(stateFill, CircleShape)
            .glassSurface(glass, CircleShape, tint = glass.tint.copy(alpha = glass.tint.alpha * 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        // Persian names whose first codepoint is a joiner (ZWNJ or the
        // Arabic letters that join forward) would render as an invisible
        // or isolated glyph — walk to the first LETTER instead of
        // blindly take(1)ing the first codepoint.
        val initial = name.trim().firstOrNull { it.isLetter() || it.isDigit() }
            ?.uppercaseChar() ?: '?'
        Text(
            text = initial.toString(),
            style = MaterialTheme.typography.titleMedium.copy(
                lineHeight = MaterialTheme.typography.titleMedium.fontSize,
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                    includeFontPadding = false,
                ),
            ),
            color = fg,
        )
    }
}

/** Thin indeterminate progress line shown while busy. */
@Composable
fun BusyLine(visible: Boolean) {
    if (visible) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

/**
 * v1.6.0-beta.9 (user report: "the screens with a text input still have
 * no Done button in the UI that closes the keyboard — something came on
 * the keyboard but it doesn't work either"): an IN-APP "Done / تمام"
 * button that closes the software keyboard and clears focus — a real
 * affordance on the screen, not a keyboard-key that some IMEs ignore.
 *
 * Every text-bearing screen (Encrypt, Decrypt, stego message, unlock
 * cards, passphrase dialogs, the companion sheet, the contact form)
 * now renders this right after its field row. It uses BOTH the
 * keyboard controller (hides the IME) and the focus manager (clears the
 * focus so the field stops being the IME target), which is why the
 * keyboard actually STAYS closed — tapping an IME Done key alone could
 * leave the field focused and the keyboard re-appearing.
 */
@Composable
fun KeyboardDoneButton(modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    OutlinedButton(
        onClick = {
            keyboard?.hide()
            focus.clearFocus(force = true)
        },
        modifier = modifier,
    ) { Text(stringResourceCompat(R.string.action_done)) }
}

/**
 * v0.5.2: the clipboard HISTORY dialog (user request: "the list of things
 * you copied and had copied before") — replaces the v0.5.1 single-clip
 * preview. Tap an entry to paste it into the field; the per-row ✕ deletes
 * it; "Clear history" wipes the list. The current clip is recorded when
 * the dialog opens, so it always sits on top.
 */
@Composable
fun ClipboardHistoryDialog(
    entries: List<com.turnypt.data.repository.ClipboardHistoryEntry>,
    onUse: (com.turnypt.data.repository.ClipboardHistoryEntry) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResourceCompat(R.string.clipboard_history_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entries.isEmpty()) {
                    Text(
                        stringResourceCompat(R.string.clipboard_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(
                            count = entries.size,
                            key = { entries[it].id },
                        ) { index ->
                            ClipboardHistoryRow(
                                entry = entries[index],
                                onUse = { onUse(entries[index]) },
                                onDelete = { onDelete(entries[index].id) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (entries.isNotEmpty()) {
                androidx.compose.material3.TextButton(onClick = onClearAll) {
                    Text(
                        stringResourceCompat(R.string.action_clear_history),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResourceCompat(R.string.close))
            }
        },
    )
}

/** One history row: bounded preview, age + CK1 + truncated meta, ✕ delete. */
@Composable
private fun ClipboardHistoryRow(
    entry: com.turnypt.data.repository.ClipboardHistoryEntry,
    onUse: () -> Unit,
    onDelete: () -> Unit,
) {
    val glass = LocalGlassTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(glass, GlassRadii.chipShape, tint = glass.tintHigh)
            .clickable(onClick = onUse),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = entry.text,
                    style = if (entry.isEnvelope) {
                        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = clipboardAgeLabel(entry.capturedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (entry.isEnvelope) {
                        Text(
                            stringResourceCompat(R.string.clipboard_history_ck1_tag),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (entry.truncated) {
                        Text(
                            stringResourceCompat(R.string.clipboard_history_truncated),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            androidx.compose.material3.IconButton(onClick = onDelete) {
                Icon(
                    TurnyptIcons.Close,
                    contentDescription = stringResourceCompat(R.string.clipboard_history_delete_entry),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "just now" / "3 min ago" / "2 h ago" / "5 d ago" for a history row. */
@Composable
private fun clipboardAgeLabel(capturedAt: Long): String {
    val now = System.currentTimeMillis()
    val minutes = (now - capturedAt) / 60_000L
    return when {
        minutes < 1 -> stringResourceCompat(R.string.clipboard_history_just_now)
        minutes < 60 -> stringResourceCompat(R.string.clipboard_history_minutes_ago, minutes)
        minutes < 60 * 24 -> stringResourceCompat(R.string.clipboard_history_hours_ago, minutes / 60)
        else -> stringResourceCompat(R.string.clipboard_history_days_ago, minutes / (60 * 24))
    }
}

/** stringResource indirection so the private helpers above stay short. */
@Composable
private fun stringResourceCompat(res: Int, vararg args: Any): String =
    androidx.compose.ui.res.stringResource(res, *args)
