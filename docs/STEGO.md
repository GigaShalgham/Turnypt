# Steganography — Advanced Cryptography Implementation (v1.6.0-beta)

> Status doc for the stego track (plan: `Turnypt-Advanced-Cryptography-Plan.docx`,
> revision 1.1). Plain Markdown by project rule. This documents what is
> SHIPPED so far and what the next stages add — the plan remains the
> single source of truth for the design.
>
> **Phase 1 (S1–S5) is COMPLETE as of v1.6.0-beta.4.** Phase 2 (S6–S8,
> file-in-file) remains open work.

## Shipped

### S1 — `:core:stego` module, Cks2 codec (v1.6.0-beta.2)

- Pure Kotlin, JVM-tested, no Android imports; depends only on
  `core:common` and `core:crypto` (plan §5.1 placement).
- **Cks2 container** (plan §4.2 rev 1.1): passphrase-only key model;
  Argon2id master with the two nonces bound as associated data;
  two-key split `K_frame`/`K_payload` via HKDF info strings.
- **64-byte frame**: 12B cleartext frame nonce (the extractor needs it
  before any key exists — a sealed nonce is a chicken-and-egg), 40B
  AES-256-GCM seal of `sealed_len(24B)`, 12B zero pad.
- **Body**: 64 KiB chunked AEAD with the CKF1 rules — counter nonces
  `payloadNonce[0..4) || counter(8B BE)`, AAD = frame ‖ position ‖
  finality, strictly-short final chunk as the truncation sentinel.
  The payload nonce derives from the frame nonce (HKDF, info
  `CKS2/payloadnonce/v1`) — de-circularised: deriving it from the
  sealed frame bytes made sealed_len depend on the nonce that depends
  on sealed_len.
- **BitSpread** (plan §4.3): 4-round Feistel over the index space with
  cycle-walking over `[0, capacity)`; half-width = ceil(bits/2) — the
  first cut used the full width and diverged (caught by the bijection
  tests).

### S3 — WavCarrier (v1.6.0-beta.4)

- **Pure-Kotlin RIFF parser**: walks the chunk list (word-aligned, odd
  sizes padded), accepts PCM 16/24-bit only, validates fmt/data shapes
  without allocating the PCM block (plan §5.3 memory discipline).
- **One LSB per PCM sample** across all channels — the plan §3.2 WAV
  row's chosen technique (amplitude domain: overwrite-LSB is the
  standard; ±1 is a pixel-domain trick). Sample delta ≤ 1 always.
- **Same two-zone layout** as PngCarrier: sequential salt+frame
  prefix, keyed BitSpread body — the facade treats both carriers
  uniformly.
- 8 new JVM tests green: 16/24-bit round-trips, empty round-trip,
  sample-delta null-test + header-untouched, wrong passphrase
  `NotACarrier`, `CarrierTooSmall` pre-flight, corrupt/non-PCM parser
  rejection, odd-chunk padding parse.

### S4 — Facade + UI (v1.6.0-beta.4)

- **StegoCipher facade** (`data:repository`, the MediaCipher twin per
  plan §5.1): signature-sniffs PNG/WAV, embeds via the ciphers,
  re-encodes PNG losslessly (`Bitmap.compress(PNG, 100)` — that IS the
  §6.1 S2 "decoders still read the output" gate), passes WAV through
  byte-exact except the LSBs. Passphrase-only key model (§4.4): the
  facade consults NO contact, NO era, NO session — DI injects only
  randomness, dispatchers and the BALANCED Argon2 profile.
- **Outcomes mirror MediaCipher's discipline**: `Embedded`,
  `CarrierTooSmall` (the §6.3 pre-flight UI state — carries needed/
  available numbers), `NotACarrier`, `Failed`.
- **StegoScreen is live** (was the plan's UI-only skeleton): SAF
  OpenDocument carrier pick (PNG/WAV), hide/reveal mode switch,
  message field, passphrase field (the ONLY key — the screen's
  contact-picker ecosystem look is UX sugar; no unlock gating),
  CreateDocument output, share-as-document intent on the result card,
  and the reveal flow with the recovered message.
- **ToolsViewModel** carries the stego state + ops on the shared
  ToolsViewModel the tabs already use; stego notices ride the existing
  UiNotice channel (res + arg, locale-resolved at render).

### S5 — Platform guidance + verification (v1.6.0-beta.4)

- **Three-tier transfer guidance** on the stego screen (plan §3.1):
  direct transfer > document-mode share > never media-mode, always
  visible with the embed flow, reinforced on the share-ready card.
- **Settings capability matrix** (Settings → new card): PNG/WAV
  supported; JPEG rejected with the reason; MP4 explicitly
  pending-S0-gate; lossy media never carries bits.
- **THREAT_MODEL.md adversary row 0**: the honest steganalysis claim —
  concealment from casual inspection, not from a determined analyst;
  content sealed regardless; no format oracle.
- **Strings in both locales** (en + fa) for every new surface.

### S2 — PngCarrier (v1.6.0-beta.3)

- **±1 LSB matching** over RGB; alpha never touched. Pixel delta ≤ 1
  per channel by construction; boundary values (0/255) degrade to
  overwrite — the standard ±1 caveat, ~2/256 values (plan §6.1 S2 gate
  wording covers this).
- **Two-zone layout** (the shipped design):
  - prefix = `salt(16B) || frame(64B)` at SEQUENTIAL slots from the
    front — plan §4.2: "read exactly the 64-byte frame from the front";
  - body at the keyed BitSpread positions over the space AFTER the
    prefix (plan §4.3: payload positions keyed by K_payload).
  - Zones disjoint by construction. An earlier draft spread the prefix
    over the FULL capacity while the body occupied the sub-space — the
    zones collided and one ±1 write clobbered another bit; the zone
    probe caught it as a single flipped salt bit.
- **Extraction** (the plan's two-step boot): read prefix → derive keys
  → the frame tag authenticates (wrong passphrase dies HERE, before
  any body bit — no oracle, plan §4.5) → sealed_len sizes the body
  read → AEAD-decode.
- **CapacityPlanner**: pre-flight fit/headroom math;
  `CarrierTooSmall` is a UI state, not a runtime surprise (§6.3).

## Not shipped (by plan decision)

- **MP4 carrier**: the plan gates tail-append on the S0 proof matrix
  ("PENDING PROOF, not a default") and ships it off-by-default if any
  rejection class exists; S0 did not clear it, so the carrier is NOT
  built and the Settings matrix says exactly that.
- **JPEG carrier** (APPn segment, plan §3.2): Phase 1 scope in the
  table but not required for the message-in-file exit — PNG is the
  primary image carrier. Open for a follow-up beta.
- **Phase 2 (S6–S8, file-in-file)**: inner type=file record, capacity
  planner UI for files, per-format carrier guidance, hardening +
  golden-file corpus. All open.
- The salt+frame prefix riding sequential slots is public structure by
  design (salt is not secret; the frame is tag-verified). Steganalysis
  hardening of the prefix (optionally folding it into the keyed zone
  with a fixed 80B bootstrap) is a plan follow-up measured against the
  S2 chi-square/sample-pair gates before any release beyond beta.

## Test matrix (JVM)

- Cks2: round-trip (empty/tiny/odd/chunk-boundary/multi-chunk),
  wrong passphrase → typed `NotACarrier`, tamper → typed failure,
  truncation → typed `Truncated`.
- BitSpread: bijection at even and odd capacities, key separation,
  determinism.
- PngCarrier: message/empty round-trips, delta ≤ 1 per channel +
  alpha untouched, wrong passphrase `NotACarrier`,
  `CarrierTooSmall` pre-flight, planner math, ±1 contract, per
  passphrase spread separation, collision diagnostic at capacity
  12 288.
- WavCarrier: 16/24-bit round-trips, empty round-trip, sample-delta
  null-test + header-untouched, wrong passphrase `NotACarrier`,
  `CarrierTooSmall` pre-flight, parser rejection (corrupt, non-PCM,
  odd-chunk padding).
