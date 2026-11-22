# Steganography — Advanced Cryptography Implementation (v1.6.0-beta)

> Status doc for the stego track (plan: `Turnypt-Advanced-Cryptography-Plan.docx`,
> revision 1.1). Plain Markdown by project rule. This documents what is
> SHIPPED so far and what the next stages add — the plan remains the
> single source of truth for the design.

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

## Not yet shipped

- S3 WAV/MP4 carriers; S4 repository facade + real StegoScreen wiring
  (the screen is the plan's UI skeleton with NO crypto behind it);
  S5 transfer-tier guidance + Settings matrix; Phase 2 (S6–S8,
  file-in-file).
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
