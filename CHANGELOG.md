# Changelog

All notable changes to Turnypt (formerly CipherKey) are documented in
this file. Entries up to 0.6.0 were written under the project's former
name and keep it — history is not rewritten.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.6.0-alpha.1] - 2026-11-20 — the stego frame, laid out first (prerelease)

### Design preview: "Hide in file"

The first piece of the steganography feature (the plan's stage S4:
the UI ships before any system exists behind it). Home gains a third
quick-action row — "Hide in file" — routing to a new screen that
walks the full Phase 1 embed flow in the app's Liquid Glass language:
pick a carrier file (PNG / JPEG / WAV / MP4), enter the shared
passphrase with the live strength hint, type the hidden message, and
review the preview card with its document-mode explainer (send the
result as a FILE, never as media — messengers re-encode photos,
audio and video, and re-encoding destroys hidden data).

Nothing behind it yet — deliberately. No crypto, no embedding, no
container: the carrier button, embed button and share button hold
local UI state only, and the screen says so plainly ("Design
preview — embedding is not wired yet"). The frame exists so the
layout, wording and step order can settle before a single line of
the real system (gated behind the plan's S0/S1 milestones) lands.
Persian translations included; `stego` route is stateless — no
ViewModel until the core module clears its gates.

## [1.5.8] - 2026-11-17 — two user-reported bugs (prerelease)

### Bug 1: the add-contact button sat UNDER the floating menu

User report, Contacts tab: the "add contact" button was effectively
invisible — it docked at the raw screen foot, directly beneath the
floating glass navigation bar that overlays that same foot. Scaffold
only reserves navigation-bar insets for a FAB; the glass bar is not a
Scaffold bar but an overlay, so nothing reserved its height. The FAB
now carries the bar's stand-off as bottom padding (96dp — the same
clearance the list's trailing spacer has used since 1.5.6), placing it
flush above the pane, end-anchored and RTL-safe. No other screen has a
FAB; nothing else moved.

### Bug 2: decrypt-from-selection-menu never unlocked the contact

User report: selecting a message in another app and choosing
"Decrypt (Turnypt)" opened the sheet, but neither the passphrase nor
the fingerprint ever opened the contact's lock — while encryption
worked fine. Root cause was twofold in the companion decrypt flow:

1. **The fingerprint path never armed the continuation.** Only the
   passphrase path (unlockThen) stashed `afterUnlock` — the work that
   NeedsUnlock interrupted (re-decrypt the envelope). The prompt
   succeeded, the session key activated… and nothing re-ran: the sheet
   sat on the unlock card forever. requestBiometricUnlock now arms the
   same continuation, derived from the phase on screen, and it is
   consumed once after a successful unlock.
2. **The session resync clobbered the just-written unlock.** The
   unlock's own sessionChanges bump fired resyncSessionState, which
   recomputed `unlocked` over the DECRYPT flow's empty contacts list
   (only the encrypt flow ever loads it) and wiped the entry the
   unlock had just written — the sheet flipped back to "locked" the
   moment it opened, retracting the fingerprint offer with it. The
   resync now keeps the pending contact's live state honest instead.

Four regression tests now drive the real CompanionViewModel through
the real crypto stack (CompanionDecryptUnlockTest): passphrase
continuation, fingerprint continuation, lockAll retraction mid-sheet,
and the wrong-passphrase failure surface. Full matrix: 387 tests, 0
failures.

## [1.5.7] - 2026-11-17 — one motion clock (prerelease)

### The app feels laggy; the bottom navigation is too fast and jumpy

User report, and a full performance audit of the UI layer. The headline
finding: there was no single "slow" thing — there were THREE motion
clocks disagreeing with each other on every tab change. The selection
pill spring ran at the library's default stiffness (10,000 — a ~120ms
snap), the page crossfade ran at 250ms in and 180ms out, and the icon
settle ran 500ms+ with its bounce landing after everything else had
stopped. The pill teleported, the outgoing screen faded out before the
incoming one arrived (a dip toward the backdrop mid-transition — the
"jump"), and the icon shimmered on after the page had settled.

**One clock now.** Every motion on a tab change runs the same ~280ms
beat:

- Page transitions: enter and exit share 280ms with the Material
  FastOutSlowIn curve (decelerating arrival). The exit no longer
  finishes 100ms before the enter completes — the crossfade trough is
  gone; the incoming screen reaches full opacity exactly as the
  outgoing finishes.
- Selection pill: stiffness 380, critically damped — a ~280ms glide
  that lands WITH the page (was: ~120ms snap). Still zero overshoot,
  still deferred-read (one layer translate per frame, the five slots
  never recompose).
- Icon scale/alpha: the soft-overshoot character stays (it is the
  deliberate life of v1.5.2) but at stiffness 1200 it settles with the
  pill instead of dragging on a quarter-second tail; the color
  crossfade joins the same clock.
- Home tiles: the press-release settle re-tuned from a ~400ms tail to
  the same beat.

### Hidden overhead removed (no visual change anywhere)

- `glassSurface` allocated two gradient brush objects per DRAW PASS —
  and the sliding pill repaints the whole glass bar every frame, so
  those allocations ran at animation frequency, every frame, on every
  glass surface (cards, chips, fields, the bar). The brushes are now
  built once when the modifier is created; the tint "gradient" was two
  identical stops and is now a plain solid fill. Byte-for-byte the
  same pixels, zero per-frame garbage.
- Every `QuickActionTile` ran TWO gesture pipelines — `clickable`'s
  own interaction tracking PLUS a private `pointerInput/
  awaitEachGesture` coroutine just to observe "pressed". The custom
  coroutine is gone: `collectIsPressedAsState()` observes the same
  press through the one `clickable` pipeline (and removes two
  edge-case races where the scale could stick at 0.96).
- The five-entry destination table was rebuilt (five data classes +
  a list) on every recomposition of the app root — i.e. on every tab
  change. It is now a file-level constant.

versionCode 30 / versionName 1.5.7.

## [1.5.6] - 2026-11-17 — the overlay was the viewport cut (prerelease)

### The opaque overlay between the content and the glass menu

Fifth report on the strip under the floating bar, and this one named the
mechanism exactly: an opaque layer between the content and the menu,
covering the LOWER half of the menu's backdrop (only the upper half
read as translucent glass), with scrolling content appearing from
behind it.

It was the NavHost's own viewport cut. The host carried
`.padding(bottom = 88.dp)`, hard-cutting every screen's scrollable 88dp
above the screen foot — while the ~64dp glass bar floats as low as the
navigation inset allows (navInset + 12dp above the foot). On a gesture-
nav device the bar spans roughly −100dp..−36dp from the foot, so its
lower ~52dp floated over a dead strip that showed nothing but the
opaque backdrop. That strip — no content, no glass sampling, nothing to
show through — WAS the "unwanted opaque overlay": only the bar's upper
fringe overlapped live content and read as glass, and scrolling items
vanished at the invisible −88dp cut line instead of sliding beneath
the pane.

v1.5.5 had recolored that strip (uniform neutral backdrop) but never
removed it — recoloring an opaque strip cannot make it translucent.

The cut is now gone. The NavHost fills the whole screen; every
scrollable scrolls UNDER the glass, so the pane's full height samples
real content top to bottom — the floating-bar behaviour the glass was
always meant to have. Each screen now provides its own trailing
clearance (96dp: the ~64dp pane + its 12dp inset + margin) so the last
resting item still clears the bar on every device:

- Home, Encrypt, Decrypt, Settings: trailing spacer 8dp → 96dp
  (Settings previously had none at all).
- Contacts: list tail 80dp → 96dp.

versionCode 29 / versionName 1.5.6.

## [1.5.5] - 2026-11-17 — the band, diagnosed at the pixels (prerelease)

### The purple block between the menu and the page elements

Fourth report, and this time the diagnosis went to the actual pixels
under the bar instead of to another theory. The finding is humbling:
every previous "fix" ADDED colour to the screen's lower region instead
of removing it.

- v1.5.4's "violet ambient bloom" painted a literal purple gradient
  across the screen's foot — the fix WAS the new purple block.
- The two-tone backdrop itself put a PLUM foot (0xFF171522) under a
  near-black crown (0xFF0E0C15): the screen's lower half was
  purple-er than its top BY DESIGN.
- The bar's own tint was a wash OF that plum (0x59171522), stacking a
  second purple over the field's purple.

The backdrop is now ONE uniform neutral field top to bottom — the
scheme's own background colour (0xFF12101A dark / 0xFFFAF8FD light) —
and the bar's tint samples that same neutral (0x5912101A). There is no
second colour anywhere near the screen's foot, so no region can read
as "an extra block". The glass keeps its presence through the violet
rim and the soft shadow: light, not paint.

### Home screen buttons

The quick-action tiles' content (medallion + label) was pinned to the
tile's top-start corner — a Box without contentAlignment — so the
values sat huddled in the corner with dead space around them, glaring
on wide tiles (tablets, landscape, two-per-row weights). The content is
now centred on both axes. The status chips' asymmetric padding
(start=8/end=12) read as the text being pushed off-centre inside the
pill; it is now symmetric.

versionCode 28 / versionName 1.5.5. Full matrix 496 tests green.

## [1.5.4] - 2026-11-17 — the shortcut fingerprint & the last band (prerelease)

### Fingerprint on the shortcut-opened decrypt sheet

The fingerprint offer, prompt and unlock logic are shared by every
surface — the in-app tools, the contact dialog and the companion sheets
— and unit tests prove the offer is armed on the shortcut path too. The
remaining difference is the WINDOW the prompt must render on: the
companion sheets are translucent, noHistory, empty-taskAffinity windows
floating OVER ANOTHER APP — precisely the hosting conditions the
androidx.biometric 1.2.0-alpha05 line (Sept 2022, pre-API-33 dialog
fragment) handles worst: the system prompt silently fails to appear on
those windows on several OEM builds. The library is upgraded to the
current 1.4.0-beta01 (Nov 2026) line, whose FingerprintDialogFragment
lifecycle/dismissal/non-activity-hosting rework is exactly this class
of fix. (developer.android.com release notes; the 1.2.0 line never had
a stable release.)

The helper's silent dead path is now honest too: when the wrap blob
cannot yield a cipher (blob too short / Keystore unavailable — the
prompt can never be built), it reports a localized reason instead of
a bare null that rendered as a plain user cancel.

### The black/white band between the glass menu and the texts

Three independent contributors, all removed:

- The backdrop spent its whole LOWER half on one flat colour — a raw
  plum-black page (dark) / raw white page (light). A backdrop is ambient
  light, not a painted wall: a violet bloom now rises from the foot of
  the field (stops at 62%/86%/100%), tuned to be felt as depth, never
  seen as a stripe. The bar's region becomes lit atmosphere instead of
  a separate flat sheet.
- Theme.Material's solid windowBackground flashed as a raw black/white
  sheet behind the glass during cold start before glassBackdrop painted.
  The window now starts transparent and is covered by the first content
  frame.
- Android 14+ paints a translucent contrast scrim over the navigation
  bar area unless opted out — on the dark theme it read as the flat
  black strip under the bar. enforceNavigationBarContrast and
  enforceStatusBarContrast are now false.

versionCode 27 / versionName 1.5.4. Full matrix 496 tests green.

## [1.5.3] - 2026-11-17 — three root-cause fixes (prerelease)

The v1.5.2 follow-up: each of the three repeat reports is fixed at its
systemic cause this time, not at a symptom.

### Top overspill

Edge-to-edge (targetSdk 36) drew the screens behind the status bar —
no status-bar inset existed anywhere in the root, and the screens' plain
16 dp padding let content scroll under the clock/status pills. The
NavHost now insets its content past the status bar once, for every
screen.

### The bottom band under the menu — the real cause

The v1.5.2 tint reduction treated the opacity, but the dark navTint was
still a distinctly purple fill (0x66221D33 smoke-plum) laid over the
backdrop's own plum — two near-neighbour purples stacking into a
visibly darker band under the bar, which read as "extra purple
background ruining the transparency." A pane of glass over this field
must not ADD colour: the tint is now a low-alpha wash of the backdrop's
OWN bottom colour (0x59 over 0xFF171522) — the bar area blends with
what is behind it; the pane's presence comes from the violet rim and
the soft shadow only. The light navTint keeps exact parity with the
general glass tint.

### Black text — the systemic cause

MaterialTheme does NOT provide `LocalContentColor`; its composition
local's factory default is `Color.Black`. Every `Text()`/`Icon()`
without an explicit `color=` in the app therefore fell back to raw
black — which is why the v1.5.2 one-by-one patches could never finish
and "the texts are still black and don't match the dark background."
Both theme roots now provide `LocalContentColor = onSurface` (app +
keyboard IME): every colour-less content in the app defaults to the
theme's onSurface, the same role M3 components use. Explicit colors
(primary values, error banners, monospace codes) still override
locally as before.

versionCode 26 / versionName 1.5.3. Unit tests green (app 57, keyboard
34; full matrix 496 unchanged — no logic touched).

## [1.5.2] - 2026-11-17 — the extension ruling, motion & polish (prerelease)

Five user rulings/fixes in one batch.

### The custom-extension ruling — "once and for all"

The v1.3.2 "type your own suffix" design is retired. The setting is now
a **pure toggle**, off by default:

- **OFF (default):** Turnypt's own suffixes — `.tnpi` for encrypted
  images, `.cki` for everything else, exactly as always.
- **ON:** every newly encrypted file — image or not — is named with the
  ONE unifying suffix **`.tnp`**. Only and only `.tnp`; no typed value,
  no field, no save button, no per-type branching.

Naming only, as ever: the CKF1 magic (not the name) decides what a file
is, decryption never depends on the suffix, and every file written under
any suffix — `.tnp`, `.tnpi`, `.cki`, or a legacy custom one — keeps
opening. A legacy stored suffix (v1.3.2–v1.5.1) is superseded by `tnp`
at read time; the toggle wipes it on every flip.

### Screen transitions & motion

- The tab switch to Settings (every tab, in fact) previously cut
  hard — now every route animates: the incoming screen fades in while
  sliding up a short 10% distance; the outgoing fades out; the back
  gesture mirrors it. Quarter-second tween, the app's calm tone kept.
- The nav-slot icons settle with a soft spring (a little life, not the
  previous dead snap) and cross-fade between selected/unselected tints;
  the selection pill glides with a single restrained overshoot; the
  quick-action tiles spring back on release with one soft overshoot.
  All deferred state reads — the added motion costs no recomposition.

### The bottom "purple chunk"

The navigation bar's tint was near-opaque (0xD8 light / 0x8A dark), so
the bar read as a dense solid slab at the screen's foot — visibly cut
from different material than the backdrop behind it. The tint now
carries the general glass weight (0xB8 / 0x66): the bar floats as glass
over the two-tone backdrop, presence carried by the violet rim and the
soft shadow instead of raw opacity.

### Black-looking text

Un-themed monospace blobs (suggested passphrases, result envelopes,
invite codes) and the un-coloured contact-detail lines now carry theme
roles: value/code texts get the primary tone (they are things you came
for, not dead black monospace), captions get onSurfaceVariant. Contrast
pairs unchanged (primary 7.08:1 on the dark field, 5.7:1 on light).

### Passphrase suggestion — entry point only

The strong-passphrase suggestion no longer appears in the add-contact
(friend-code entry) dialog. It lives ONLY where the user is actually
entering a passphrase: the unlock card and the shared-passphrase sheet
(SuggestPassphraseSection, unchanged there).

versionCode 25 / versionName 1.5.2. 496 unit tests green (494 + 2 new
for the tnp ruling).

## [1.5.1] - 2026-11-17 — three visual corrections (prerelease)

The follow-up batch to the Turnip identity: three user-reported visual
defects, each fixed at its cause. **No layout, structure, or behaviour
changes.**

- **Restored the launcher background.** The v1.5.0 logo field pass
  (`#2A2440`) read visually worse than the aubergine it replaced; the
  launcher background and the icon's keyhole/leaf fills are reverted to
  their previous treatment (`#331B52` field, matching keyhole) exactly as
  they were — the turnip artwork, proportions, and positioning are
  untouched.
- **Uniform glassmorphism on the bottom navigation.** The bar's specular
  bevel covered only the top 45% and its rim faded to 35% alpha at the
  bottom, so the upper half read as glass while the lower half read as a
  solid purple slab. Both layers now span the full height of every glass
  surface: one continuous specular gradient (still fading downward, still
  restrained) and one uniform hairline rim — one pane, top to bottom.
- **Removed the selection flash on the Encrypt/Decrypt Text|Image
  segment.** The bare `clickable` on each half drew the default
  unclipped ripple — a translucent square with no rounded corners and
  no border — that flashed over the pill track during selection. The
  halves are now invisible 48dp buttons (same treatment as the nav
  slots): the sliding indicator is the selection feedback, nothing else.
- Contrast pairs are untouched (38 pairs audited for v1.5.0 remain
  ≥ 4.5:1); the system-nav inset positioning from v1.5.0 is preserved.

## [1.5.0] - 2026-11-17 — the Turnip identity (prerelease)

A visual-identity pass on the v1.4.0 structure — **same layout, same
components, same interaction model** (per the brief: "Preserve the design.
Elevate its visual identity. Refine its execution.").

### The Turnip palette

Purple-dominant, green occasional; premium and restrained, never neon.
Dark: plum-black field `#12101A` / surface `#1C1928` / elevated
`#292338`, lavender primary `#B18AFB`, secondary purple `#9470D8`,
secondary green `#A3B98A`, accent `#DCCAFF`. Light: violet-warmed white
`#FAF8FD` / `#FFFFFF` / `#F0EAF9`, primary `#8256C7`, secondary purple
`#A17AD9`, secondary green `#718D5B`, accent `#E8DDF9`. Every brief hex
anchors its role; the derived roles keep every on/container pair ≥ 4.5:1
(38 pairs audited worst-case over both backdrop bounds — worst 4.85:1).

The semantic SUCCESS channel is now the Turnip green — the one green in
the identity, reserved for the unlocked/secure state. The floating
assistant overlay and the IME theme follow the same hexes.

### Lighting & depth

- Purple ambient light: glass tints and rims carry violet; the specular
  bevel is restrained (accent at low alpha — a ceiling light, not a
  glow).
- Every glass surface now casts ONE soft, diffused ambient shadow
  (6dp, low alpha, shadow color per theme) — depth without crisp or
  stacked shadows.
- Two-tone backdrops shift violet (#EDE7F5→#FAF8FD light,
  #0E0C15→#171522 dark) — the ambient purple the glass samples.

### Bottom navigation — smoothness & insets

- **Fixes the system-nav overlap**: the floating bar now insets itself
  above the Android navigation bar via `WindowInsets` navigation-bars
  padding — correct under gesture nav AND three-button nav, edge-to-edge
  safe at targetSdk 36, no hardcoded device offsets.
- Perf: the sliding selection pill and the icon scale animations are
  **deferred state reads** (read inside `offset`/`graphicsLayer`
  lambdas) — an animating pill repaints one layer and no longer
  recomposes the five slots. Springs are critically damped
  (`DampingRatioNoBouncy`) with tight visibility thresholds: the pill
  lands immediately, no overshoot, no bounce. Same treatment for the
  Text|Image indicator and the quick-tile press scale.

No behaviour change: routes, flows, tests, security surfaces untouched.

## [1.4.1] - 2026-11-17 — the invisible navigation bugfix

### Fixed

- **The floating navigation bar rendered at ZERO height** (and was
  therefore invisible — no way to reach Encrypt/Decrypt/Contacts/
  Settings from the bar). In Compose, children using `matchParentSize()`
  are excluded from the parent's size resolution; in v1.4.0 every child
  of the bar (the sliding selection pill AND the slot row) used it, so
  the wrap-content bar resolved to nothing. The slot row is now the
  size-defining child and the pill sits behind it in a `matchParentSize`
  wrapper — the intended Liquid Glass layout, finally visible.
- **The Text | Image segmented control rendered at ZERO height** the
  same way (all children `matchParentSize`) on the Encrypt, Decrypt tabs
  and the image companion sheet — the image mode was unreachable. Same
  restructure, same fix.
- The sliding selection pill also ignored its intended width
  (`matchParentSize` overrides an incoming `width`), so even where it
  rendered it spanned the whole track instead of one slot/half. It now
  takes its true slot width inside the wrapper.

No behaviour, route or state change — this is purely the v1.4.0
redesign's two collapsed layouts made visible again.

## [1.4.0] - 2026-11-17 — Liquid Glass redesign

A complete visual redesign of every surface in Apple's **Liquid Glass**
design language (the iOS 26 aesthetic): translucent glass surfaces with
real depth, floating rounded navigation, pill-shaped controls, specular
highlights and refraction rims, spring-based motion — with every
existing feature, flow, and behaviour preserved untouched.

### Added — the Liquid Glass system (`ui/theme/Glass.kt`)

- **Design tokens, not per-screen styling**: `GlassColors` (tint, raised
  tint, refraction border, specular highlight, nav tint, two-tone
  backdrop) provided adaptively for light (frost) and dark (night) via
  `LocalGlassTokens`; one `glassSurface` modifier assembles every glass
  panel (tint → bevel → rim); `GlassRadii` defines the single corner
  family (12/16/20/28 + pill). Screens consume tokens; nothing
  hand-rolls translucency.
- **Native window blur** for the translucent companion sheets:
  `Window.setBackgroundBlurRadius` (API 31+, compositor-side — no
  per-frame shader, no new dependency) makes the calling app genuinely
  read as frosted depth behind the sheet; below 31 the dimmed scrim
  continues as before.

### Changed — every surface, one language

- **Floating navigation bar**: the docked `NavigationBar` becomes a
  floating glass pill over edge-to-edge content — five ≥48dp slots, a
  spring-animated selection pill sliding between them, the active icon
  settling a hair larger. Same routes, same base-route selection logic
  (the v1.2.0 fix), same deep-link retap behaviour.
- **Components**: SectionCards, ContactCards, EmptyStates, ResultBlocks,
  NoticeBanners, quick-action tiles, cipher chips, picked-file rows,
  clipboard-history rows and settings rows are all glass panels now;
  state (unlocked/warning/error) renders as solid tonal insets ON the
  glass — the SUCCESS/ochre/error channels never fade with the backdrop.
  The Text|Image segment switch is a glass segmented control with a
  sliding indicator; Home carries a glass hero; the decrypt preview and
  incoming drafts rest on raised glass plates.
- **The floating assistant** (classic Views): night-glass bubble and
  panel with light rims, glass-row wells, and a restrained 160 ms
  scale+fade expand (was an instant visibility flip).
- **Motion**: springs tuned small (no overshoot clipping), no continuous
  or looping animation anywhere; nothing animates per-frame.
- **Palette**: surfaces cool one step (light) / deepen one step (dark)
  so glass reads as a LAYER, not a match; brand primary, Vazirmatn
  typography and all semantic pairs unchanged.

### Preserved (the redesign contract)

- All functionality, crypto, flows, dialogs, clipboard history,
  biometrics, custom file extension, navigation behaviour and Persian
  translation — untouched (a visual layer, not a feature rewrite).
- Accessibility: every text-on-glass pair audited numerically at both
  backdrop bounds — worst case 6.40:1, all ≥ WCAG AA 4.5:1; touch targets
  unchanged (≥48dp, tiles 88dp); state still icon + color, never color
  alone; FLAG_SECURE untouched.
- Performance: no per-frame blur in-app (only the single platform window
  blur on translucent sheets), no new dependencies, token-level reuse
  instead of duplicated styling.

## [1.3.2] - 2026-11-17 — custom file extension (off by default)

User request: "add an option in setting named Custom file extention and
turn it off in default".

### Added

- **Custom file extension** — a new Settings row (in the Features
  section, after Clipboard history), **off by default**: when ON, NEW
  encrypted files are named with the user's own suffix instead of
  Turnypt's ecosystem suffixes (`photo.jpg.tnpi` → `photo.jpg.secret`;
  non-image sources `notes.txt.cki` → `notes.txt.secret`). The row
  carries its own field — type the suffix, save it, then flip the
  toggle; enabling with no stored value or entering an invalid one
  shows an honest error banner instead of a silent no-op.
  - The suffix is sanitized and validated at every layer:
    letters/digits/`-`/`_` only, 1–16 characters, a leading dot accepted
    and stripped, and the reserved Turnypt suffixes (`cki`, `tnpi`)
    refused so a custom value can never masquerade as the defaults;
    persisted values are re-validated on read (a corrupted or forged
    store falls back to blank/OFF).
  - Naming only — what a file IS was always decided by CONTENT (the
    CKF1 magic), so decryption never depends on the name: every file
    ever written under `.tnpi`/`.cki` (or any custom suffix) keeps
    opening exactly as before, and the plaintext restore strips the
    custom suffix too (`photo.jpg.secret` → suggested save name
    `photo.jpg`). Names already carrying `.tnpi`/`.cki` still pass
    through unchanged — no double suffixing.
  - Turning the toggle OFF also blanks the stored suffix — "off" must
    mean off, so a later re-enable starts from a clean slate.
  - 7 new test groups cover the sanitizer (accepts/normalizes, refuses
    junk/reserved/oversized), suffix replacement across image and
    non-image sources, passthrough for already-encrypted names,
    default-preserving null/blank suffixes, and the original-name/
    plaintext-name stripping.

## [1.3.1] - 2026-11-16 — lock-time fix + Vazirmatn

Two user reports in one: "changing lock time had some bugs and didn't
work", and "add font for Persian words".

### Fixed

- **Changing the lock time now actually works** (user report: "changing
  lock time had some bugs and didn't work"). Before, the timeout was
  read exactly ONCE — at the moment of unlock — and frozen into an
  absolute deadline inside the session manager. Every consequence of
  that single decision was wrong:
  - tightening (24 h → 1 min) did nothing to already-unlocked sessions:
    they stayed live until the stale 24 h deadline, and even the 60 s
    background sweeper kept honouring the frozen deadline;
  - re-entering the passphrase could not fix it either — the contact was
    "already unlocked", so the answer was AlreadyUnlocked and the frozen
    deadline stayed;
  - the Settings chip row therefore looked completely dead whenever a
    session was live.
  The deadline is now derived from live fields (`unlockedAt` + the
  CURRENT timeout) and evaluated at every query/sweep, and a settings
  change calls `rearmTimeout`, which applies the new value to every live
  session as if it had been the setting AT UNLOCK: tightening wipes a
  session that already outlived the new budget immediately (a tightening
  can LOCK, not merely shorten future lifetimes), while an extension
  grants the fuller budget from the session's original anchor — but
  never resurrects an already-expired session. The "no sliding renewal,
  countdown starts at unlock" doctrine is preserved: a change can never
  grant more lifetime than the new setting allows from the original
  anchor. Wipes bump the sessionChanges counter, so every open screen
  re-reads who is unlocked. The timeout summary string (en + fa) now says
  the change applies to what is unlocked right now.
  - `KeySessionManager.rearmTimeout` (+ clamping against `KeyPolicy`, so
    a forged/out-of-range value can never reach the session policy), the
    `MessageCipher.rearmSessionTimeout` facade pass-through, and the
    `SettingsViewModel` wiring (persist, then re-arm).
  - Tests: 9 manager-level cases (tightening wipes over-lived sessions,
    younger sessions keep the new budget, the anchor never slides,
    extension never resurrects, historical sessions follow, no-op,
    clamping, counter bumps) and 4 facade-level cases through
    `MessageCipher` (tightening locks an over-lived live session, the
    setting travels to the next unlock, loosening never resurrects,
    re-unlocking after expiry re-arms under the live setting).

### Added

- **Vazirmatn, a bundled Persian font** (user request: "add font for
  Persian words") — the Persian translation (v0.5.1) rendered through the
  system's fallback stitching, readable but visually second-class.
  Vazirmatn (https://github.com/rastikerdar/vazirmatn, SIL OFL 1.1, kept
  at `res/raw/vazirmatn_ofl.txt`) covers Persian in proper Naskh AND
  Latin, so one family serves every locale with no per-locale branching:
  - app Compose theme: all type-scale styles (headline/title/body/label)
    set the family — Regular/Medium/SemiBold/Bold, bundled per weight
    (no downloadable-fonts provider: Turnypt is local-first and renders
    offline);
  - keyboard module: its own res/font copy (Regular/Medium) wired into
    the IME typography (the module keeps its own theme and never depends
    on the app module);
  - floating-assistant panel (plain Views, not Compose): the row labels
    and header use Vazirmatn Medium, the hint line Vazirmatn Regular,
    each cached once with a platform fallback.

## [1.3.0] - 2026-11-13 — Turnypt-ecosystem image extension (.tnpi)

The user's ruling on the 1.2.0 image-extension approach, applied at the
source: the extension of an encrypted image must carry the Turnypt theme —
not CipherKey's `.cki`, and not the source's own image extension — and
Turnypt must be suggested for opening files with that extension.

### Changed

- **Encrypted images are named in the TNP family** (user ruling: "not
  .cki and not jpg or anything like it — the extension must be aligned
  with the Turnypt ecosystem, with that theme"): the encrypted output of
  an image is `<original-name>.tnpi` — `photo.jpg` → `photo.jpg.tnpi`
  (the original name is kept intact inside, so decrypting restores it
  exactly); an extension-less source derives its real image extension
  first (`IMG_001` + image/png → `IMG_001.png.tnpi`). The pair carries
  its own `application/x-turnypt-image` MIME: the SAF create dialog is an
  x-turnypt-image create, and sharing the finished file advertises that
  type. What is encrypted is still decided by CONTENT (the CKF1 magic),
  never by the name. Sources not recognizable as images keep the legacy
  `<name>.cki` + `application/x-cipherkey` output, and every `.cki` file
  ever written keeps opening exactly as before.
- **Turnypt is suggested for opening `.tnpi` files** (user ruling: "for
  opening a file with that extension, Turnypt must be suggested"):
  `ACTION_VIEW`/`ACTION_SEND` intent filters in the four shapes real
  file managers produce (content/file, typed and untyped — mirroring the
  proven .cki set) make the OS offer Turnypt for any `.tnpi` file, and
  the user can set it as the default opener. The 1.2.0 plain-image
  registration is GONE: normal images open in the gallery as usual;
  sharing one INTO Turnypt (SEND image/*, kept since v0.6.0) still offers
  the encrypt flow. A `.tnpi`/`.cki` file that is not CKF1 by content
  gets the honest "not encrypted by Turnypt" notice plus an "Encrypt
  this instead" recovery.
- Decrypting now suggests the original name with the suffix stripped; a
  result that carries no extension at all (a renamed source, say
  `photo.tnpi`) gets its extension from the DECRYPTED CONTENT's sniffed
  MIME (`photo` + sniffed image/jpeg → `photo.jpg`) — the one honest
  witness of what the plaintext actually is.

### Compatibility

- v1.2.0-era outputs (encrypted images written under their real image
  extension) are unchanged files and still decrypt through every entry
  point that routes by content: the in-app Decrypt tab picker, and
  Share → Turnypt (the magic sniff routes them to the decrypt flow). They
  simply no longer match a VIEW filter by name — they are Turnypt
  ciphertext, not real images, and their share MIME is now the honest
  `application/x-cipherkey`.
- The binary CKF1 magic, the `.cki` extension/MIME and every `.cki`
  filter are untouched; the wire prefixes (TNP1:/TNS1:, legacy
  CK1:/CKS1:) are untouched.

## [1.2.0] - 2026-11-13 — Image extensions, unlock fixes, navigation fixes

The four user reports on the 1.1.0 release, each traced to its root cause
and fixed at the source.

### Changed

- **Encrypted images keep their real image extension** (user report:
  "images are still saved or exported with the .cki extension — image
  files must use the correct image file extension"): the Encrypt tab and
  the companion sheet now suggest the SOURCE's own name for the encrypted
  output (`photo.jpg` → `photo.jpg`; an extension-less name derives it
  from the advertised MIME), the SAF create dialog is an image create, and
  the finished file shares with the matching image MIME so messengers
  treat it as an image attachment. What is encrypted is decided by
  CONTENT — the CKF1 magic — never by the name. Sources that are not
  recognizable as images keep the legacy `<name>.cki` +
  `application/x-cipherkey` output, and every `.cki` file ever written
  keeps opening exactly as before.
- **Turnypt is registered as an opener for the supported image types**
  (user report: "Turnypt must be the default application for opening
  files with the supported image extension"): `ACTION_VIEW` intent
  filters (content/file, typed and extension-matched, mirroring the
  proven .cki shapes) make the OS offer Turnypt — and let the user set it
  as default — for jpg/jpeg/png/webp/gif/bmp/heic/heif. Opening an
  encrypted image lands straight in the decrypt flow (magic sniff); a
  normal image gets an honest "not encrypted by Turnypt" notice plus an
  "Encrypt this image instead" button, so the registration never dead-
  ends. Sharing an image INTO the app sniffs the magic too: encrypted →
  decrypt, plain → encrypt (the old name-based heuristic misrouted
  camouflaged files into the encrypt flow).

### Fixed

- **The password unlock bricked the Encrypt tab** (user report: "the
  password input does not function correctly"): on a standalone open (no
  text and no image queued), the unlock's success path never reset the
  `busy` flag — after typing the passphrase the Encrypt button stayed
  disabled forever. Both unlock paths (passphrase and fingerprint) now
  reset it; the continuations set it again themselves.
- **A finished draft could re-encrypt itself later**: the pending text
  was never consumed after a successful encrypt/decrypt, so a later
  unlock of a locked contact silently re-ran the old message. Drafts are
  consumed on completion, and switching contacts discards any queued
  continuation (it belonged to the old contact).
- **The fingerprint button only appeared after extra taps** (user report:
  "it shows up only after opening the list, clicking a contact and
  another loading cycle"): the initial screen load restored the sticky
  contact but never armed the unlock card — only an explicit contact
  click computed the fingerprint offer. `refresh()` now arms the card
  (pending state, honest suggestion gate, fingerprint offer) on first
  appearance. The Encrypt tab's unlock card also moved into its own
  titled section — the same shape the Decrypt tab uses — so the
  passphrase field, strength hint, suggestion and fingerprint button
  always render as one complete, stable card.
- **The bottom navigation bar never highlighted Encrypt/Decrypt and taps
  looked dead** (user report: "clicking does not consistently work; the
  bar fails to reflect the active page"): those two destinations live in
  the nav graph under query-pattern routes (`encrypt?mode={mode}`), and
  the selected-state check compared the bare tab route against the full
  pattern — never a match, on any screen. Selection now matches on the
  base route, and tapping the tab you are on resets any deep-link
  arguments to the tab's default state (a visible response instead of an
  apparent no-op).

## [1.1.0] - 2026-11-13 — TNP1:/TNS1: wire prefixes, contact-open fixes

The user's two reports on top of the 1.0.0 rebrand, plus the prefix
migration they asked for.

### Changed

- **Wire prefixes moved off the retired brand** (user rule: "CK1 was for
  CipherKey; now it must be TNP — the app should recognize old and new by
  itself, with no explanation to the user"): text envelopes are now
  written `TNP1:` and session invites `TNS1:`. Legacy `CK1:` / `CKS1:`
  messages and invites still decode everywhere — detection is by prefix,
  automatic, and invisible. The byte layout behind the prefix (including
  the keyId that resolves the contact and its friend era) is unchanged,
  so a legacy envelope walks the identical path; the golden vector keeps
  its body and only the written prefix moved (the legacy twin is covered
  by tests). User-visible hints moved with it (EN + FA strings: decrypt
  hints, clipboard-history tag, invite help, the About card).
- Deliberately UNCHANGED (invisible bytes inside files, renamed buys
  nothing): the binary `CKF1` magic, the `"CipherKey-CKF1-v1"` HKDF info
  string, the `.cki` extension and the `application/x-cipherkey` MIME
  type — every .cki file ever written keeps opening.

### Fixed

- **Opening a contact with the password did nothing** (user report: "the
  password shortcut in the encryption section doesn't work"): selecting a
  LOCKED contact in the Encrypt tab showed the unlock card, but submitting
  the passphrase hit a `pendingText == null` guard (no text and no image
  operation queued) and silently returned. The unlock now stands on its
  own: the contact opens, the card swaps for the Encrypt button, and the
  Remember-key offer fires, so the next unlock can be a fingerprint.
- **No fingerprint when opening a contact** (user report: "not in the
  encryption and decryption sections, and outside the app on the floating
  assistant"): the fingerprint offer is now computed the moment a locked
  contact is selected (Encrypt tab picker, companion prepare card), and
  the companion prepare card — the sheet the floating assistant opens
  over other apps — renders the "Use fingerprint" button it never had.
  The image flows share the same unlock card and benefit automatically.

## [1.0.0] - 2026-11-13 — the Turnypt rebrand: new name, new mark, .cki opens anywhere, assistant stays in context

CipherKey is now **Turnypt** — turnip + encrypt. The identity changes;
the security architecture, the wire formats and every existing message
and file do not: `CK1:` envelopes, `CKS1:` invites and `.cki` files
(including the `application/x-cipherkey` MIME and the
`CipherKey-CKF1-v1` HKDF info string) are byte-identical across the
rename, so a Turnypt user and a CipherKey 0.x user decrypt each other's
traffic without any migration.

### Added

- **Tapping a `.cki` file now suggests Turnypt in any file manager**
  (user report: "it doesn't suggest the app — and it's the only app
  that can read the file"). File managers cannot know a custom MIME
  type, so for the unregistered `.cki` extension they send
  `application/octet-stream` — or no type at all — and the v0.6.0
  MIME-only `ACTION_VIEW` filter never fired. The image companion now
  ALSO matches by extension via `pathPattern=".*\\.cki"` in the four
  shapes real file managers produce: `content://`/`file://` URIs, each
  typed and untyped. `ACTION_SEND` (sharesheet) gets the same treatment,
  so a `.cki` can be shared INTO the app too.
- **New launcher icon: the turnip mark** — cream bulb, three green
  leaves, a keyhole cut out of the bulb's face (turnip + encrypt), on a
  deep aubergine field. Ships with a monochrome layer for themed icons
  (Android 13+); all geometry verified inside the adaptive-icon safe
  zone (max radius 29.3 of 33).

### Changed

- **Rebrand CipherKey → Turnypt**, project-wide: app name (English and
  the Persian translation), package `org.cipherkey` → `com.turnypt`
  (applicationId, namespaces, source tree, manifest action/extra
  identifiers, shortcuts, Room schemas path), themes, DataStore and
  database file names, Keystore aliases, the CI artifact name, all
  documentation, and the GitHub repository itself. Protocol identifiers
  listed above are deliberately untouched.
- **The floating assistant no longer hijacks the screen** (user report:
  "tapping en/decrypt closes my app and switches to the full app").
  Companion sheets launched with `FLAG_ACTIVITY_NEW_TASK` resolved to
  the app's EXISTING task — dragging the main screen in front of the
  messenger behind a translucent sheet. Every companion activity now
  declares `taskAffinity=""`: the sheet runs in its own featherweight
  task stacked over whatever the user is doing, the messenger stays
  visible behind it, and finishing returns to the exact spot in the
  chat. Sharesheet and PROCESS_TEXT entries inherit the same fix.
- Version identity: `0.6.0` / versionCode 14 → `1.0.0` / versionCode 15.

### Upgrade notes

- The application id changed with the rename (`org.cipherkey` →
  `com.turnypt`), so Android treats Turnypt as a NEW app: install fresh
  and re-create contacts by exchanging `CKS1:` invite codes again
  (local-first means no export channel exists by design), then
  uninstall the old CipherKey app. Existing `CK1:` messages and `.cki`
  files remain fully readable.

## [0.6.0] - 2026-11-13 — second stable release: image encryption, ChaCha20-Poly1305, Automatic cipher

Promoted from the 0.6.0-alpha candidate after on-device validation:
identical features, no code change beyond the version identity. The
second full (non-pre-) release — and the first stable build to carry
the 0.5.x line: the Calm Vault design language with launcher fallback
shortcuts (0.5.0), the fingerprint / 256-MiB / Persian fixes batch
(0.5.1), clipboard history with entry-time passphrase suggestion
(0.5.2), plus the 0.6.0 headline: natively integrated image
encryption, ChaCha20-Poly1305 and the Automatic cipher preference.

### Changed

- Version identity: `0.6.0-alpha` / versionCode 13 → `0.6.0` / versionCode 14.
- README release badge and checksum example now point at v0.6.0.

## [0.6.0-alpha] - 2026-11-13 — image encryption, native; ChaCha20-Poly1305; Automatic cipher

The headline: **image encryption is no longer a side feature — it is part
of the CipherKey ecosystem.** Same contacts, same key eras, same
passphrase (and fingerprint) unlock, same visual language, same entry
points. Plus the long-requested second cipher with honest, practical
guidance and an Automatic mode that measures instead of guessing.

### Added

- **Image encryption & decryption, natively integrated**
  (user direction: "it must feel like it was always a core capability").
  - **CKF1 container format** (`.cki`, `application/x-cipherkey`): the
    exact CK1 55-byte header + independently sealed 64 KiB chunks. A
    per-file subkey (`HKDF-SHA256(eraKey, salt=nonce)`) makes chunk
    nonces collision-free across files; chunk AAD binds header,
    position and finality (reordering / splicing / truncation all
    fail authentication). Peak memory is ONE chunk — the 256 MiB OOM
    lesson of v0.5.1, now by construction. Full design:
    `docs/IMAGE-CRYPTO.md`, wire spec: `docs/PROTOCOL.md` §CKF1.
  - **The SAME trust system**: encrypt an image for the SAME contact
    with the SAME passphrase; ONE unlock serves text and files. Old-era
    `.cki` files unlock through the same historical-era path as old
    messages. No parallel contacts, no second key store.
  - **Information architecture**: a third row in Home Quick Access
    (Encrypt/Decrypt image); a Text | Image segment switch inside BOTH
    the Encrypt and Decrypt tabs; sharesheet entries ("Encrypt image"
    for image shares, "Decrypt .cki file" for our MIME type); an
    "Encrypt image" row in the floating assistant; in-sheet SAF
    pickers (no storage permission). Results: saved `.cki` with
    share-back; decrypted images preview (downsampled, private cache)
    with Save/Share via SAF/FileProvider.
- **ChaCha20-Poly1305 (RFC 8439) as the second CK1 cipher** (wire id
  `0x02`, additive — old builds reject it by design, so no protocol
  version bump). Implemented through BouncyCastle's lightweight API
  (already a dependency for Argon2id — zero new dependencies, no JCA
  provider insertion, one implementation on every API level and in JVM
  tests). Pinned byte-for-byte to the RFC 8439 §2.8.2 AEAD vector.
- **Cipher preference with practical explanations + Automatic**
  (user direction: "not just two options — explain the difference").
  Settings → Unlock & security → Cipher offers Automatic /
  AES-256-GCM / ChaCha20-Poly1305, each with a one-line practical
  description, plus an expandable "Which one should I choose?"
  explainer (hardware AES vs constant-time software, security
  equivalence, compatibility). **Automatic micro-benchmarks both
  ciphers once per process and uses the faster** — a real measurement
  of this device, not a CPU-name guess — and shows what it resolved
  to. The same dialog is reachable from the cipher chip at the point
  of use in the image flow. Decryption always follows the wire: mixed
  AES/ChaCha traffic decrypts transparently under any preference.

### Changed

- `ProtocolHeader`/`PassphraseCryptoProvider` are algorithm-aware; the
  CK1 header layout and every existing AES envelope are byte-identical
  (golden vector unchanged).
- `MessageCipher` (text) and the new `MediaCipher` (files) both draw
  the cipher for NEW encryptions from one injected preference source —
  one setting, every surface, text and files alike.

### Tests

- 440 unit tests (was 407): RFC 8439 §2.8.2 vector; RFC 5869 HKDF
  vectors (TC1/TC2); CKF1 roundtrips on every chunk boundary (empty,
  ±1 byte, exact multiples, multi-chunk) and the adversarial set
  (tamper header/chunk, truncation mid-file and sentinel-stripped,
  chunk reordering, trailing garbage, wrong key, unknown cipher id);
  `MediaCipher` two-device roundtrip over the same eras/sessions as
  text; Automatic selector outcome/caching; image naming/formatting
  helpers.

## [0.5.2-alpha] - 2026-11-07 — clipboard history, panel close, passphrase suggestion

Three clarifications from the same user who reported the v0.5.1 batch.

### Added

- **Clipboard history** (user request: "the list of things you copied and
  copied before") — the "Clipboard" button next to Paste (Encrypt and
  Decrypt tabs) now opens a HISTORY dialog instead of a single-clip
  preview: tap any entry to insert it, per-entry ✕ delete, "Clear
  history". Entries carry a relative age, a CK1 tag for envelopes and a
  "truncated" marker.
  - Capture points, all focus-gated or user-initiated (the app still
    registers NO clipboard listener and reads NOTHING in the background —
    Android 10+ would deny it anyway): the clipboard read when CipherKey
    gains window focus (copy in the messenger, then open the app), the
    assistant's Encrypt/Decrypt-clipboard rows, Paste, and the copies the
    app itself makes (envelopes, invite codes).
  - PRIVACY: decrypted plaintext and suggested passphrases copy through
    the SENSITIVE path and are NEVER recorded (the recorder marks them
    seen so a later focus read cannot pick them up either). Storage is
    capped — 25 entries, 7 days, 2 000 chars per entry — and lives in the
    private, non-backed-up database. A Settings toggle (default ON) turns
    capture off, with a "Clear history" action.
- **Strong-passphrase suggestion AT the password-entry moment** (user
  request) — every passphrase field (Encrypt/Decrypt tabs, the companion
  sheets, the contact detail dialog) can now offer "Suggest a strong
  passphrase" with Use / New one / Copy / Hide. "Use" fills the field;
  "Copy" takes the sensitive clipboard path and never enters the history.
  - GATED BY THE FRIEND INVITE (user's rule): the affordance appears ONLY
    when this era was created locally — i.e. no friend invite code was
    entered, so THIS side is choosing the shared passphrase right now —
    AND the era was never unlocked on this device. When a friend invite
    was entered, the peer already picked the passphrase the user must
    type, so no suggestion is offered. A new `session_keys.imported`
    column (DB v2 migration) records how each era arrived; after the
    first successful unlock the suggestion disappears for good.
- **Floating assistant panel ✕** (user report: "you can't just close or
  minimize it — you must do something or turn it off entirely") — the
  panel now has a header with a ✕ that COLLAPSES it back to the bubble
  without running anything; the service keeps running. Previously the
  only ways out of an open panel were performing an action or "Turn off".

### Changed

- The "View clipboard" button is renamed "Clipboard" and opens the
  history dialog; the v0.5.1 single-clip preview dialog is gone (the
  current clip is recorded on open and sits on top of the list).

### Fixed

- Suggested passphrases copied from the Add-contact dialog previously
  went through the PLAIN clipboard path (visible preview, and they would
  have been recorded by the new history) — they now use the sensitive
  path like every other secret CipherKey copies.

## [0.5.1-alpha] - 2026-11-06 — user-reported fixes batch

All six items below come from one user test pass on v0.5.0-alpha.

### Fixed

- **Fingerprint unlock never worked anywhere (HIGH).** Three stacked
  causes, all fixed:
  - `androidx.biometric` 1.1.0 (2021) silently failed to show the system
    prompt with `BIOMETRIC_STRONG` + `CryptoObject` on several OEM builds
    of Android 12+ — upgraded to the maintained **1.2.0-alpha05** line.
  - The **Remember-key offer was hidden behind the Settings toggle**
    (default off), so after a passphrase unlock the fingerprint was never
    offered anywhere unless the user had found the toggle first. The
    system prompt itself ("Remember this key" / "Not now") is now the
    consent: the offer appears after every passphrase unlock on a capable
    device, and success persists the toggle ON. Turning the toggle OFF
    still wipes every wrap — semantics unchanged.
  - Failures were **silent or generic**. The prompt helper now runs a
    `canAuthenticate(BIOMETRIC_STRONG)` pre-flight before even building
    the prompt (localized reason via `BiometricDiagnostics.reasonRes`),
    and non-cancel authentication errors surface the system's own message
    in the notice ("Fingerprint unlock did not complete (…) — use the
    passphrase."). Cancels stay quiet on purpose.
- **256 MiB encryption crashed the app (HIGH).** Two distinct OOM paths:
  - The PARANOID Argon2id preset (256 MiB) allocates one contiguous
    256 MiB `int[]`; on a stock heap (128–256 MB) that threw
    `OutOfMemoryError` through the unlock path and killed the process.
    A pre-flight heap check (`Argon2MemoryGuard`, 1.25× headroom) now
    rejects impossible profiles BEFORE allocating, the repository maps
    the typed `KdfMemoryException` to a new
    `UnlockOutcome.MemoryHungry`, and the UI answers with a localized
    "this era needs N MiB — pick a lower strength" notice. The manifest
    also gains `largeHeap` so capable devices can actually run the
    preset.
  - A multi-hundred-KiB/MiB paste/share/selection was size-checked by
    first materialising its **full UTF-8 encoding** (`toByteArray`) —
    itself the crash. Both `ProcessTextContract` and
    `MessageCipher.encryptDraft` now reject on the cheap character count
    first (a UTF-8 byte is never shorter than a char) and never allocate
    the encoding of an oversized input.

### Added

- **Persian (فارسی) translation** — a complete `values-fa` string set for
  every user-visible string; RTL was already supported
  (`supportsRtl`). Persian digits come from locale-aware formatting.
- **View clipboard** button next to Paste (Encrypt and Decrypt tabs): a
  bounded preview dialog (first 400 chars, total length, CK1-envelope
  detection, monospace for envelopes) so you can see WHAT the clipboard
  holds before pasting it.
- **Floating assistant panel, rebuilt** (it "was too weak, no options"):
  labeled rows instead of three bare icons — **Encrypt clipboard**,
  **Decrypt clipboard**, **Lock all sessions** (wipes every in-memory
  key, confirmed by a toast), **Open CipherKey**, **Turn off assistant** —
  plus an in-messenger hint line ("copy the message, then tap Decrypt
  clipboard"). The panel still never reads the clipboard itself (Android
  10+ denies that to unfocused windows; no app can read another app's
  selection in Telegram's custom menu — docs/COMPATIBILITY.md).

### Changed

- **Settings re-ordered and slimmed**: the everyday **Features** section
  now leads the screen; the session-timeout card ("lock session time")
  lost its pinned top slot and shrank from seven 40dp radio rows plus a
  full-width 48dp button to a single chip row plus a compact lock button
  (user feedback: "not very necessary so unpin it from top and make it
  smaller").

## [0.5.0-alpha] - 2026-11-06 — the "Calm Vault" redesign + messenger fallback shortcuts

### Added

- **Design research + the v0.5 design language** (`docs/DESIGN.md`): a full
  audit of the v0.4 UI against the Material 3 role system, dark-theme
  guidance, vault-app precedents and NN/g empty-state / progressive-
  disclosure research, distilled into the "Calm Vault" system — implemented
  across every screen.
- **Launcher app shortcuts (zero permissions)**: long-press the CipherKey
  icon for **Encrypt clipboard** / **Decrypt clipboard**. This is the
  universal fallback for messengers whose text-selection menu hosts no
  third-party entries — Telegram's message bubbles (and WhatsApp's) draw
  their own menu, so the selection-toolbar entry can never appear there;
  the recommended flow becomes copy → long-press CipherKey → Decrypt
  clipboard. Documented honestly with sources in `docs/COMPATIBILITY.md`.
- Settings gains a **Features** section; the empty Contacts screen gains a
  direct **Add contact** call to action (NN/g empty-state guidance).

### Changed — the v0.5 design system

- **Color**: the full Material 3 role set is now declared (the
  surface-container tier, inverse roles, `outlineVariant`, `scrim`,
  `surfaceTint`); grouped surfaces are tonal instead of bordered. A
  **SUCCESS semantic extension** (green) becomes the dedicated
  "unlocked/secure" channel — the vault-app pattern — so the brand indigo
  never has to mean two things. No dynamic color, by design: state colors
  stay constant.
- **Components**: `SectionCard` is tonal with an optional leading icon;
  `StatusChip` is a pill with a leading state icon (readable without
  color); `QuickActionTile` is an 88dp tile with a 40dp icon circle (was a
  40dp outlined button row); `EmptyState` gained a CTA; `NoticeBanner`
  gained INFO/SUCCESS/WARNING/ERROR tones so confirmations stop reading as
  errors; new labeled `SettingSwitchRow` (the old row showed the literal
  text "On"/"Off" next to the switch); new `ContactAvatar`; `ResultBlock`
  renders on a tonal inset.
- **Screens**: Home (brand header, status card, 2×2 action grid — still no
  tutorial walls), Contacts (avatar cards, extended FAB, success-tone
  feedback), Settings (four titled sections, prominent error-toned "Lock
  all sessions now"), Encrypt/Decrypt (same flows, restyled; the ad-hoc
  `fontSize` emphasis is gone), ContactPicker (lock-state icons instead of
  prepended words).
- **Passphrase strength**: STRONG now renders in the success green (FAIR
  keeps the warm ochre, WEAK stays red).

### Security

- No new permission, no network code, no schema or wire change. The
  shortcuts route through the existing audited clipboard companion flows
  (focus-deferred read, `ClipboardAccess` chokepoint, `FLAG_SECURE`,
  `noHistory`).

## [0.4.2] - 2026-11-06 — first stable release

Promoted from the 0.4.2-alpha candidate: identical features and fixes, no
code change beyond the version identity. This is the first release
published as a full (non-pre-) release, marking the repository's public
launch.

### Changed

- Version identity: `0.4.2-alpha` / versionCode 8 → `0.4.2` / versionCode 9.

### Repository

- Prepared for public release: README rewritten as a product document,
  issue/PR templates generalized, internal engineering artifacts (working
  specs, Spec Kit tooling, session reports, reassessment draft) removed
  from the tree. Git history is preserved.

## [0.4.2-alpha] - 2026-11-06 — deep-scan fixes: reactive re-lock, era-change resync, honest errors

Second on-device follow-up: a deep scan of the v0.4.1 UI found two HIGH
stale-state bugs, two MEDIUM honesty bugs and a set of LOW polish items.
All addressed; no schema, permission or wire change.

### Fixed

- **HIGH (N1) — the UI forgot to re-lock.** The in-app Encrypt/Decrypt tabs
  and the Contacts screen snapshotted "who is unlocked" once and never
  re-read it: after "Lock all sessions now" (Settings), a session timeout
  or the 60-second expiry sweeper, the Encrypt tab kept showing its Encrypt
  button with NO unlock card until the user tapped something. `KeySessionManager`
  now exposes a monotonic `sessionChanges` StateFlow (bumped on every
  activate/lock/lockAll/expiry), `SessionKeyRepository` an `eraChanges`
  counter, and `MessageCipher` combines both into one `sessionChanges`
  signal that Tools/Contacts/Companion ViewModels collect to re-read the
  live unlock state. Locking anywhere now re-locks every visible surface
  immediately.
- **HIGH (N2) — stale "unlocked" + wrong fingerprint offer after an era
  change.** A companion sheet (selection toolbar / sharesheet / floating
  assistant) kept claiming its contact was unlocked after the contact's key
  era rotated, and kept offering a fingerprint wrap bound to the OLD era.
  The sheet now resyncs on every session/era change; an offer whose wrap no
  longer matches the current era is retracted instead of dead-tapping; and
  the prompt's "contactId:eraId" key is re-checked when the sensor confirms,
  so a wrap of a rotated era can never be activated as the new era's key
  (it is discarded with the honest "out of date" notice).
- **MEDIUM (N3) — the Add dialog masked the real error.** The dialog
  discarded the ViewModel's detailed failure ("This invite key already
  belongs to another contact", "Invalid invite code: …") and printed a
  generic "Creation failed". The detailed notice now flows through as a
  typed `UiNotice` rendered from string resources.
- **MEDIUM — wrong "no key era" count for brand-new contacts.** A just-created
  contact's card flashed "no key era" until the async era query landed. The
  map is now seeded synchronously from the era that `createContact` just
  created or imported.
- **LOW — hardcoded English strings** in ContactsViewModel feedback/errors
  and the companion contact dropdown's lock glyphs are now string resources.

### Security

- **FLAG_SECURE on MainActivity** (the companion activities already had it):
  decrypted plaintext and passphrase fields can no longer leak through
  screenshots or the recents preview of the main app.

### Changed

- **Theme**: a deliberate type scale (`Typography`) joins the v0.4.1 color
  schemes and shapes — same titles/body/label weights everywhere instead of
  per-screen `fontSize` guesses.
- **Components**: `SectionCard` is outlined (1dp `outline` border, no drop
  shadow); `StatusChip` uses distinct container roles (positive =
  `primaryContainer`, informational = `secondaryContainer` — the two Home
  chips no longer blur together); `NoticeBanner` is a real banner
  (`errorContainer` background) instead of a bare error-colored line;
  `QuickActionTile` uses a real 8dp gap instead of two literal spaces
  inside the label text.
- **EmptyState** (icon + title + body on a tonal surface) replaces the bare
  left-aligned text pairs on the Contacts and Encrypt screens.
- **Rotation resilience**: the Encrypt/Decrypt input fields are
  `rememberSaveable` — a rotation no longer wipes the typed draft or the
  pasted envelope. Passphrase fields are deliberately NOT saved: view state
  must never outlive the interaction that typed it.

### Compatibility

- No database, preference, permission or CK1 wire-format change;
  versionCode 8. Old-key-era decryption, the passphrase advisor and the
  biometric gate behave exactly as in 0.4.1-alpha.

## [0.4.1-alpha] - 2026-11-06 — device-feedback fixes: design, tutorials, passphrase suggestion, biometrics

Four pieces of on-device feedback against v0.4.0-alpha, all addressed.

### Fixed

- **The recommended passphrase read as compulsory** (user feedback): the
  v0.4.0 new-contact dialog auto-showed a monospace "Recommended passphrase"
  the moment the invite field was blank — which looked like a
  system-assigned password, and taken at face value on two phones would
  give each side a DIFFERENT passphrase (they could then never decrypt each
  other's messages). The card is gone. What stays: one rule line ("Both
  people must enter the SAME passphrase — agree on it in person") and an
  OPTIONAL suggestion behind an explicit "Suggest a strong passphrase
  (optional)" button, labeled with the in-person sharing warning.
- **Biometric gate/prompt/key class mismatch**: the gate and the system
  prompt accepted `WEAK|STRONG` while the Keystore wrap key is created with
  `AUTH_BIOMETRIC_STRONG` — a Class 2 sensor passed the gate, was offered
  the prompt, and could then never authorize the cipher. Gate, prompt and
  key now all agree on STRONG (`canAuthenticate(BIOMETRIC_STRONG)`,
  `setAllowedAuthenticators(BIOMETRIC_STRONG)`).
- **Silent biometric failures**: the Settings row guessed "No fingerprint
  enrolled" for every failure. It now maps the raw `canAuthenticate` result
  to an honest reason (nothing enrolled / no sensor / sensor busy /
  pending security update / not Strong-class) — `BiometricDiagnostics`,
  pinned by unit tests (symbolic mapping, so a library renumbering cannot
  change the meaning).
- **Fingerprint affordance missing where users look for it**: "Use
  fingerprint" now also appears in the contact detail dialog (the natural
  place), with in-dialog notices for cancel/stale outcomes; the
  "Remember this key?" offer fires right after a passphrase unlock there.

### Changed

- **Tutorial walls dropped** (user feedback): the Home "How it works" and
  "Security" cards are gone — status chips and quick actions only. Guides
  live in README/CHANGELOG, not in the app. Helper text in the contact
  dialogs was cut to single lines.
- **Complete Material 3 color schemes + rounder shapes**: v0.3.0 set six
  color roles and let the rest (primaryContainer, tertiary, …) fall back to
  the baseline purple — the blue/purple clash that read as broken on
  device. Every role is now declared (calm indigo primary, slate secondary,
  warm ochre tertiary for the FAIR strength hint), cards 24dp / dialogs
  32dp rounded. Contact cards use proper containers; the 🔒/🔓 emoji labels
  became plain colored text.
- Contact dialogs migrated off hardcoded literals to string resources
  (localizable); profile pickers use radio buttons consistently.

### Compatibility

- No schema, permission or wire-format change — in-place update over
  0.4.0-alpha (versionCode 7). Still zero network code.

### Verified

- New `BiometricDiagnosticsTest` (symbolic gate mapping + fallback);
  everything else compile- and CI-gated as usual.

## [0.4.0-alpha] - 2026-11-06 — old-key-era decryption + passphrase advisor

The two items picked from the v0.4.0 roadmap proposal, audited in
`docs/DEEP-SCAN-2026-11-06.md`.

### Added

- **Old-key-era message decryption**: an envelope whose keyId belongs to a
  superseded era now decrypts. `KeySessionManager` keeps a bounded LRU
  historical cache (≤ 8 entries, decrypt-only — a historical key is never
  the sending key and never visible to active-session lookups); entries
  are wiped on eviction, expiry, `lock(contact)` and `lockAll`. With no
  historical session yet, decryption returns `NeedsEraUnlock`; the UI
  collects the passphrase of that era and `unlockEra` re-derives it
  against the era's wrapped digest verifier (the same honest-error
  contract as `unlockContact`). Biometric offers stay off for superseded
  eras — a fingerprint wrap is bound by AAD to the latest era only.
- **Stale-active-session guard**: a latest-era envelope is never decrypted
  with an active session that predates the rotation (previously a
  guaranteed AEAD failure); it now asks for a fresh unlock.
- **Passphrase advisor** (`security:policies`): a live strength meter
  under every passphrase field, plus a recommended-passphrase generator
  in the new-contact dialog (7 words from a 512-word list + 2 ASCII
  digits, `Locale.ROOT` — 69 bits guaranteed on top of the Argon2id KDF
  cost). The recommendation is display-only and never stored; it is
  copied through the `ClipboardAccess` chokepoint. Enforcement stays
  with `KeyPolicy`.

### Changed

- `unlockContact` and `unlockEra` share `verifyAndPersistVerifier` (the
  extracted verifier step; v1→v2 migration rewrite and best-effort
  first-unlock persist preserved).
- Era-aware passphrase prompts across the tools tab, the companion flows
  and the dormant IME; `MessageCipher` gained `NeedsEraUnlock` /
  `unlockEra` and lost the unreachable `StaleKeyEra` outcome.

### Compatibility

- No Room schema change (multi-era rows already existed), no new
  permissions, no DI changes — installs as an in-place update over
  0.3.0-alpha. Still zero network code.

### Verified

- New unit suites: historical-session lifecycle, era-unlock round trip,
  stale-session guard, advisor (wordlist pinned at 512 so it cannot
  silently drift). CI green (`gradle test` + `assembleDebug`, JDK 21 /
  SDK 36) after the audit's two fixes (F1 initialization order `c4de3ce`,
  F2 floor-case test expectation `4efe856`); full report in
  `docs/DEEP-SCAN-2026-11-06.md`.

## [0.3.0-alpha] - 2026-11-05 — the lost release, re-implemented

> The original v0.3.0-alpha source was never committed (see spec 005 and
> `recovered/v0.3.0-alpha-from-apk`). This release re-implements everything
> the shipped APK contained, in Kotlin, on top of v0.2.2-alpha.

### Added

- **Floating assistant** (opt-in, off by default): a draggable overlay
  bubble with quick Encrypt / Decrypt-clipboard actions, hosted by a
  specialUse foreground service. Needs the "Display over other apps"
  permission, which Settings deep-links to; "Close" on the panel persists
  the toggle off. Adds `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_SPECIAL_USE` and `POST_NOTIFICATIONS` permissions —
  with the feature off, none is requested and no service runs.
- **Fingerprint unlock** (opt-in, off by default): after a passphrase
  unlock the user may remember the session key behind the system
  BiometricPrompt. The key is wrapped with an AES-256-GCM cipher whose key
  lives in the Android Keystore with `setUserAuthenticationRequired(true)`;
  the wrap AAD is `"contactId:eraId"`, so a rotated key era invalidates the
  remembered wrap automatically (stale wraps are discarded, never used for
  the wrong era). Turning the feature off wipes every blob AND the Keystore
  wrap key. The passphrase path always remains available.
- **In-app Encrypt / Decrypt tabs**: manual encryption and decryption of
  typed or pasted text (contact picker, CK1 detection, unlock cards, result
  block with Copy) — for apps where neither the selection toolbar nor the
  sharesheet reaches the text. The main navigation is now five tabs:
  Home, Encrypt, Decrypt, Contacts, Settings.
- **Settings expansion**: timeout picker with 1 min → 24 h choices
  (`KeyPolicy.MAX_SESSION_TIMEOUT_MILLIS` extended accordingly,
  `nearestTimeoutChoice()` added), fingerprint-unlock toggle,
  floating-assistant toggle, and the existing KDF-strength picker.
- **Typed notices**: companion notices are now `(string resource, arg)`
  pairs (`UiNotice`) resolved at render time — locale-correct after
  configuration changes and assertable in unit tests.
- **`MessageCipher` biometric surface**: `currentEraId()`,
  `exportSessionKey()` (returns a copy) and `unlockContactWithKey()` (the
  caller's buffer is wiped on every path).

### Changed

- `BaseCompanionActivity` and `MainActivity` extend `FragmentActivity`
  (required by the system BiometricPrompt; still a `ComponentActivity`).
- The "Replace in field" affordance now also appears for
  decrypt-from-selection (insertable for both directions).
- Home screen rebuilt around quick actions, status chips, how-it-works and
  the security-properties card, all string-resource driven.

### Security

- No new network code; the app remains local-first with zero network
  access. Both new features are opt-in and permission-gated; every crypto
  path still funnels through the audited `MessageCipher` facade.
- The assistant service holds no key material and performs no crypto — it
  only launches the existing companion activities.

### Verified

- 90 unit tests, 0 failures (logic; device gate for the new UI remains, as
  before, a maintainer task — see spec 005 task list).
- `assembleDebug` and `assembleRelease` (unsigned) build cleanly.

## [0.2.2-alpha] - 2026-11-05 — remediation of the 2026-11-05 deep-scan findings

Every code finding from `docs/DEEP-SCAN-2026-11-05.md` is addressed below
(numbering follows that document). Machine-verified after the fixes:
197 unique tests / 19 classes / 0 failures (+10 regression tests) and a
clean `assembleDebug`.

### Fixed — shipped code (MEDIUM findings)

- **Unlock-verifier race (finding 1)**: `KeySessionManager.activate()` now
  copies `keyBytes` (it already copied `keyId`/`salt`). A concurrent
  `lock()`/`lockAll()` between activation and verifier persistence can no
  longer zero the buffer mid-wrap and permanently poison the era's verifier.
- **Keystore failures misreported as "Wrong passphrase" (finding 2)**: a
  `KeyProtectionException` during unwrap (e.g. master key invalidated by a
  system update) is now surfaced as `UnlockOutcome.Failed` with a
  "Keystore error" reason; the cached Keystore key handle is evicted so the
  next attempt reloads it. Wrong-passphrase detection is unchanged (it comes
  from the verifier comparison, not from the unwrap).
- **No `FLAG_SECURE` on plaintext screens (finding 3)**: all companion
  activities (the only screens that render decrypted plaintext) now set
  `FLAG_SECURE` — screenshots and screen recording capture nothing.
- **Decrypt failures headed "Encrypt" (finding 4)**: each entry activity
  declares its mode and passes it into notices; the clipboard flow sets its
  mode before the first read. An empty/oversized **decrypt** invocation is
  now correctly titled "Decrypt".
- **Stored verifier was the full session key (finding 5)**: the at-rest
  verifier is now a Keystore-wrapped **digest** (`0x02 || SHA-256(key)`).
  Wrong-passphrase rejection is identical, but a DB + Keystore compromise no
  longer yields the raw key. Legacy full-key blobs from ≤ 0.2.1 are still
  accepted and transparently migrated on the next successful unlock.

### Fixed — shipped code (LOW/INFO findings)

- Contact-creation failures now render inside the add-dialog, not only
  behind its scrim (finding 6).
- Settings copy no longer references the removed keyboard toolbar; the About
  card shows the real `BuildConfig.VERSION_NAME` instead of a stale "0.1.0"
  (finding 7).
- Expired sessions are swept by a low-frequency background sweeper, so an
  expired-but-unqueried key no longer sits in memory until process death
  (finding 8).
- Key fingerprints format with `Locale.ROOT` — Persian-locale devices no
  longer render "۰۷۲"-style digits that break out-of-band comparison
  (finding 9).
- Companion notices no longer render twice (card + inline error line,
  finding 10).
- Removed the dead `onNewIntent` "singleTop relaunch" path no manifest
  activity declares (finding 11).
- All companion flows run through a never-crash launcher: unexpected
  exceptions surface as notices with `busy` cleared, and the passphrase
  `CharArray` is wiped on every path including exceptions (finding 12).
- The timeout slider writes to DataStore only when the drag finishes, not
  on every animation frame (finding 13).
- "Copy invite" goes through the `ClipboardAccess` chokepoint and reports
  success/failure inline (finding 14).
- `deleteContact` clears the fingerprint cache along with the era cache
  (finding 15).
- Same-millisecond key eras resolve deterministically (`ORDER BY createdAt
  DESC, sessionId DESC`); `importEra` loses its check-then-act window on the
  unique `keyId` index by re-classifying after a constraint failure
  (finding 16).
- Copied decrypted plaintext is marked `EXTRA_IS_SENSITIVE` on Android 13+
  so the clipboard overlay hides it (finding 17).

### Fixed — dormant `:keyboard` library (compiled + tested, not shipped)

- `replaceAll` is now atomic (`beginBatchEdit`/`endBatchEdit`) with a
  best-effort restore of the deleted draft if the insert fails, and replaces
  an active selection directly instead of leaving it in place (findings 21/22).
- `readDraft` includes the active selection and returns an honest
  `DraftRead` result: `Unreadable` and `TooLong` are no longer conflated
  with an empty draft (finding 22).
- Draft reads are capped at the 16 KiB protocol limit (+1 to detect
  overflow) instead of hauling up to 100,000 chars per side over Binder on
  the main dispatcher (finding 23).
- Locking the active contact also clears the decrypted preview
  (finding 24).
- The `isSensitiveField` live re-check is now consulted at action time
  (encrypt, decrypt-clipboard, insert) as defense in depth (finding 26).

### Documentation

- README rewritten for the shipped v0.2 companion architecture and the
  197-test reality (it still described the v0.1 IME and 140 tests).
- THREAT_MODEL / ARCHITECTURE aligned with the digest verifier, the
  clipboard-copy behavior, and the actual wipe triggers.

### Not addressed (documented decisions)

- Finding 25 (`KeyboardLifecycleOwner` dispatches only CREATE/DESTROY):
  safe today and only reachable if the IME ships again; left as documented
  limitation.
- Finding 27 (GitHub PAT) — re-provisioned by the maintainer after the scan.
- Finding 28 (release signing keystore lost): the next *signed* release
  still needs a new keystore and a disclosed key change; no signing identity
  is recoverable on this machine.

## [0.2.1-alpha] - 2026-11-04 (device-testing pre-release, tag `v0.2.1-alpha`)

Samsung companion fixes (spec 004), from the maintainer's Galaxy A54 5G
(One UI) report against v0.2.0-alpha. Same signing key as v0.2.0 —
installs as an in-place update, no uninstall needed.

### Fixed

- **False "the clipboard is empty" on Android 10+** (spec 004 RC-1):
  clipboard launches read the clipboard during `onCreate`, before the
  window had focus — an unfocused window is denied clipboard access
  since Android 10. Clipboard workflows now defer the read to
  `onWindowFocusChanged(hasFocus=true)`, with an idempotence guard, a
  transient "Reading the clipboard…" state, and a **Try again** button
  on clipboard notices.
- **Clipboard failure taxonomy** (spec 004 FR-2): genuinely empty,
  non-text (e.g. an image), and OEM-blocked (`SecurityException`)
  outcomes now produce distinct, honest notices instead of all
  collapsing into "empty".
- **`EXTRA_PROCESS_TEXT_READONLY` ignored on encrypt** (spec 004 RC-3):
  read-only selections no longer offer "Replace in field", which the
  calling app would have ignored.

### Investigated, no code change

- Selection-toolbar entries not visible on One UI (spec 004 RC-2): the
  shipped manifest was verified correct (`aapt2 dump xmltree`); on
  modern Android/One UI third-party `PROCESS_TEXT` handlers live in the
  toolbar's ⋮ overflow, and some apps draw custom selection UIs that
  never surface them. The clipboard ladder is the designed fallback;
  see `specs/004-samsung-companion-fixes/DEVICE_TEST.md` for exactly
  where to look.

### Tests

- 187 unique tests / 18 classes / 0 failures (+27 over v0.2.0's 160):
  19 `ClipboardParser` tests (Persian, emoji, multiline LF/CRLF,
  non-text mimes, blocked-vs-empty) and 8 companion workflow round-trip
  tests over the real crypto stack.

## [0.2.0-alpha] - 2026-11-04 (device-testing pre-release, tag `v0.2.0-alpha`)

Companion architecture (Option E, spec 003): CipherKey becomes a
permission-free companion app instead of a keyboard. You keep Samsung
Keyboard — or any keyboard — and encryption moves to OS text mechanisms.
No backend, no accounts, no network code, zero Android system permissions.

### Added

- "Encrypt (CipherKey)" selection-toolbar entry (`ACTION_PROCESS_TEXT`):
  select a draft in any app, confirm, and the selection is replaced in
  place by the `CK1:` envelope. Sticky last-used contact; passphrase
  prompt only when the session is locked.
- "Decrypt (CipherKey)" selection-toolbar entry: select a received
  `CK1:` message (read-only views supported), preview the plaintext
  explicitly, copy it — or insert it directly in editable fields.
- "Decrypt with CipherKey" sharesheet target (`ACTION_SEND text/plain`)
  for messengers where sharing is easier than selecting.
- Clipboard fallback ladder on the Home screen ("Encrypt clipboard" /
  "Decrypt clipboard") for apps whose selection toolbar is unavailable.
- Hardening seam (`ProcessTextContract`) with adversarial unit tests:
  null/blank/oversized/malformed inputs produce graceful notices, never
  crashes or silent truncation.
- Sticky-contact preference (single additive DataStore key).

### Changed

- The `:keyboard` IME module is no longer part of the release APK. The
  shipped app registers no input method at all, which removes the entire
  Compose-in-IME crash class observed on device with v0.1.0. The module
  remains in-repo and keeps compiling in CI.
- Home screen reworked to the companion workflow (IME setup card removed
  with the module).

### Security

- Still zero Android permissions. Clipboard is read only on explicit
  user action in the foreground through the single `ClipboardAccess`
  chokepoint. Decrypted plaintext is previewed and never written to disk.

### Known issues

- Selection-toolbar entries depend on the target app supporting standard
  text processing; per-app variance is real (fallback ladder exists).
- Release builds are signed with a NEW v0.2 key: the v0.1.0 keystore was
  lost with a build-environment recycle. Uninstall v0.1.0 before
  installing v0.2.0 (v0.1.0 never launched on device anyway).

## [0.1.0] - 2026-11-04

First alpha. Local-first end-to-end encryption layered on top of any Android
messaging app via the keyboard (IME). No backend, no accounts, no network
code, zero Android system permissions.

### Added

- 15-module Gradle project: `app` (management UI), `keyboard` (the IME —
  contains no cryptographic code), `core` (crypto, messages, keys,
  sessions), `data` (Room, repositories, preferences), `security`
  (Keystore protector, clipboard chokepoint, Argon2id policy profiles),
  `plugins` (public-key plugin API + registry).
- Mode A encryption: Argon2id key derivation (RFC 9106 known-answer
  vector verified byte-identical) + AES-256-GCM AEAD; the entire CK1
  protocol header is bound as associated data.
- `CK1:` versioned wire envelope (strict canonical base64url, golden
  vector pinned in tests) and `CKS1:` invite codes for contact pairing.
- Compose-based keyboard with QWERTY + symbols layouts, crypto toolbar,
  passphrase unlock dialog, draft replacement via InputConnection (with
  clipboard fallback), explicit clipboard decryption with preview-before-
  insert, and sensitive-field (password) suppression.
- Contact and key-era management backed by Room; DataStore settings;
  Android Keystore wrapping for at-rest key material; session manager
  with idle timeout, cross-contact isolation, and wipe-on-lock.
- 140 unique unit tests across 15 classes (0 failures), including
  two-device roundtrip simulation, wrong-passphrase rejection (verifier
  and AEAD paths), ciphertext/header tamper detection, Persian/Unicode
  roundtrips, and zero-clipboard-read checks on password fields.
- Documentation: `docs/PROTOCOL.md`, `docs/ARCHITECTURE.md`,
  `docs/THREAT_MODEL.md`, `docs/PLUGINS.md`, engineering report.
- GitHub Spec Kit integration: project Constitution (security-first,
  honesty non-negotiables) and specifications under `specs/`.
- CI: unit tests, debug build, Gradle wrapper validation, and secret
  scanning on every push and pull request.

### Security

- Zero Android permissions (only the AndroidX self-owned
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` marker); `allowBackup=false`;
  no INTERNET permission; no Accessibility service; clipboard is read
  only immediately after an explicit user action.

### Known limitations (v0.1)

- No on-device verification has been performed yet (no emulator/device in
  the build environment) — treat as alpha software.
- No forward secrecy in mode A (static shared passphrase per key era).
- Messages encrypted under superseded key eras cannot be decrypted.
- Latin QWERTY layout only (the crypto stack is fully Persian-capable).
- Metadata (sizes, timing, participants) remains visible to the transport.
- Release builds ship unminified for this alpha (documented rationale in
  the release notes).
