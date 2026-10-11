# Plan — Nuvio focus graph, round 4: rail corridor, categories, content, anchor

Status: **retest 6 run; round-4d fixes deployed 2026-10-07 (session 13
continued, Dev base)** — retest 7 is the gate. Chain on Dev `d4bdf258`: 9
patches through `nuvio-focus-round4c.patch` plus the 10th
`nuvio-focus-round4d.patch`; current package
`ygicp2m4frgn51cqf74rqjrwyr651729`, generation
`pgssrv1l78bjs7xnqylsbc2mb580f473`. Findings: STATE s13 addenda 5–7, ADR
finding 21. Context: ROADMAP V1.2.

**2026-10-08 (s13 retrospective):** this round is **closed to new
finding-chasing patches**. Retest 7 remains the behavioral gate, but the next
implementation work is governed by
[nuvio-focus-consolidation.md](nuvio-focus-consolidation.md) — headless
focus-contract harness, supported-API replacements for the NaN anchor and
hand-wired edges, patch-chain consolidation, and upstreaming the generalizable
deltas. Do not open a "round 5" here.

## Retest 3 + investigation outcome (do not re-derive)

Couch retest 3 on the round-3 build: rings now good (Manage Profiles +
avatar picks fixed), text-field behavior fine, B works on first press after
subpage entry, ring retention on the rail works. Remaining failures and
their root causes (investigated 2026-10-06 from source + the in-app
`nuvio-nav:` journal, no code changes):

1. **Rail ↔ categories leak.** Up from Home and Down from the avatar move
   focus into the settings category column instead of staying on the rail.
   Round 3 cancelled only the category-side edges (`first.up`/`last.down`);
   the rail side has no explicit edges, so the geometric connection remains
   (see Geometry).
2. **Right from any rail item other than Settings, and Right from a
   category, makes the ring disappear** — journal shows focus landing on the
   invisible recovery anchor (`moved=true … anchor=true`; the log prints
   after the move). The anchor's far `focusRect` guard is ineffective
   (`Rect(10_000f, 10_000f, 10_000f, 10_000f)` is an empty rect and/or the
   anchor's real 0×0 bounds still participate in the search).
3. **After two more Right presses the ring reappears on the profile
   avatar.** Once focus sits on the anchor, the dispatcher's
   clear-and-retry searches from the window corner and picks the nearest
   top-left focusable — the avatar. This is a symptom of the anchor being
   selectable (2), not a separate rail/graph bug.
4. **Subpage entry: ring missing, B works.** The page-transition effect
   requests focus on the header back button, but the request does not stick
   (target likely not composed/attached yet at request time, or it loses to
   the anchor effect). Focus ends on the anchor → Esc has a target (B
   works), nothing visible is ringed.
5. **Category → content: Right vanishes.** The `right = contentEntry`
   redirect targets the content group without a reliably attached ringed
   first item, so the same anchor fallthrough as (2) occurs.
6. Fixed/confirmed: field Up/Down behavior, the two new rings, rail ring
   retention.

## Geometry (root of the problem)

- Desktop sidebar: 68 dp overlay at the left edge (`DesktopHoverSidebar`,
  `align(Alignment.CenterStart)`); tab content is padded to start after it
  (`contentStartPadding = DesktopSidebarCollapsedWidth`).
- Settings tablet layout: `Row { 240 dp category column | content pane }`.
  Categories are top-anchored at x≈68–308 (y≈150–400).
- Rail nav column is vertically centered (Home/Search/Library/Settings,
  x≈0–68, y≈450–670); the profile avatar sits at the top (x≈0–68, y≈55).
- Compose arrow navigation is geometric, so the left side forms one
  continuous vertical band: avatar → categories → rail nav. Any edge not
  explicitly wired is decided by nearest-candidate geometry and leaks
  across that band.

## Decisions (user, 2026-10-06)

- On the Settings tab, D-pad **Right from any rail nav item**
  (Home/Search/Library/Settings) enters the **categories column**.
- Proceed with the plan below; implementation in a fresh session
  (documentation-first closeout of this one).

## Implementation plan (round 4)

1. **Make the recovery anchor unselectable by navigation.** First try a
   valid, non-empty far `focusRect` (e.g. `Rect(10_000f, 10_000f, 10_001f,
   10_001f)`); verify via the journal that no move line ever ends with
   `anchor=true`. If that still fails, isolate it another way or replace the
   invisible anchor with **visible parking** (on focus loss, request a real
   on-screen node). Never leave a selectable invisible focus target.
2. **Rail corridor (explicit edges).** Give the rail nav items and the
   profile avatar per-item `FocusRequester`s and wire: `avatar.down →
   Home`, `Home.up → avatar`, `Home/Search/Library/Settings up/down → rail
   neighbor`, `Settings.down → Cancel`, `avatar.up → Cancel`. This removes
   the rail→categories geometric leak (file: `AppShellComponents.kt`).
3. **Right from any rail nav item on the Settings tab → categories.**
   Extend the existing `SettingsFocusLinks.categoryEntry` right-override
   from the Settings item to all rail nav items, gated on
   `selectedTab == Settings` (file: `AppShellComponents.kt`). Left from
   categories keeps the default (returns to rail).
4. **Category → content lands on a visible ringed node.** Point the
   `contentEntry` redirect at a concrete target that is attached (first
   ringed content row on Root; header back button on subpages), with
   frame-safe retries (`FocusRequester.requestFocus` returns Boolean).
   Ensure anything it can land on has a ring (files: `SettingsScreen.kt`,
   plus ring coverage if a landing node is ringless).
5. **Subpage back-button focus.** Make the page-transition request win:
   request after composition (retry across frames until the requester
   reports success, or request from the back button's `onPlaced`), so the
   ring appears immediately and B stays first-press (files:
   `SettingsScreen.kt`, `SettingsComponents.kt`).
6. **Keep the `nuvio-nav:` diagnostics** (`focus=`/`anchor=`/`moved=`) for
   retest 4; remove at closeout.
7. **Packaging:** 8th patch `nuvio-focus-round4.patch` after the existing
   7; build/switch on brook via the standard loop.

## Implementation outcome (2026-10-06, session 13 continued)

- **1. Anchor isolation.** Done, with a deliberate divergence from the
  "first try" text: a valid far `focusRect` cannot work — in CMP 1.12.0 the
  candidate check (`TwoDimensionalFocusSearch.isCandidate()`) still accepts a
  far/empty rect; it is a legal candidate and wins whenever it is the only
  readable one at its hierarchy level (exactly the retest-3 failure). The
  anchor now uses `focusRect = Rect(NaN, NaN, NaN, NaN)`: every directional
  comparison fails, so no arrow move can land on it, while `requestFocus()`
  and key routing are unaffected (placed 0×0 node; the focus-grant path has
  no placement guard). Esc/B parking + the dispatcher clear-and-retry are
  unchanged.
- **2. Rail corridor.** Per-item `FocusRequester`s + explicit edges:
  avatar `up = Cancel`, `down = Home` (default geometry while the profile
  switcher stack is open, so the stack stays reachable); Home/Search/Library/
  Settings chained `up`/`down`; Settings `down = Cancel`.
- **3. Right → categories.** The `right = categoryEntry` override now sits on
  all four rail nav items, gated on `selectedTab == Settings`.
- **4. Category → content.** `SettingsFocusLinks.contentEntry` now points at a
  concrete attached node: `backRequester` on subpages, and a new
  `contentEntryRequester` attached to the first ringed row of the active Root
  section (`firstRowModifier` threaded through `settingsRootContent` /
  `SettingsNavigationRow`). The category click path retries across frames.
- **5. Subpage back-button focus.** The page effect retries
  `backRequester.requestFocus()` for up to 8 frames until it reports success,
  so the ring appears immediately and B has a target on first press.
- **6. Diagnostics** kept (`focus=`/`anchor=`/`moved=`).
- **7. Packaging.** 8th patch generated with the symlinked `a`/`b` method;
  `git apply --check` against the round-3 state passes; chain fidelity
  re-verified on a fresh 0.1.27 extract (patches 1–8 == working tree).
  Built on brook (one compile fix en route: missing `focusRequester` modifier
  import in `AppShellComponents.kt`); switched without reboot.

Residual notes for retest 4: if a category's first Root row is uncomposed
(deep scroll), the Right redirect degrades to a no-op with focus retained
(never an invisible landing); with the Root search field visible, the target
is still the first row below the field.

## Acceptance (couch retest 4)

- No arrow press leaves focus invisible: journal shows no `anchor=true`
  outcomes from moves; every landing shows a ring.
- Rail is a corridor: Up from Home → avatar, Down from avatar → Home, ends
  cancel; no leaks into categories from either side.
- Settings: Right from ANY rail item → the active category (ringed); A or
  Right from a category → first **ringed** content item (or the subpage back
  button); Left from content returns to categories.
- Subpage: ring appears on the header back button immediately; B pops on
  the first press.
- Regressions: text-field behavior, prior rings, home-root B no-op
  unchanged.

## Working notes for the fresh session

- **Current base (2026-10-07):** upstream **Dev** `d4bdf258` (0.1.27-alpha +
  merged PR #791) with **10 cwrneirOS patches** in `packages/nuvio-src/`
  (`nuvio-arrow-nav-0.1.27` … `nuvio-focus-round4d`). Deployed package
  `ygicp2m4frgn51cqf74rqjrwyr651729`, generation
  `pgssrv1l78bjs7xnqylsbc2mb580f473`; retest 7 pending.
- Working tree: `/tmp/opencode/nuvio-spike/work` (Dev + patches). `/tmp` is
  tmpfs and was already wiped once — regenerate with:
  ```sh
  cd /tmp/opencode && mkdir -p nuvio-spike && cd nuvio-spike
  curl -sL -o dev.tar.gz \
    https://github.com/NuvioMedia/NuvioDesktop/archive/d4bdf2588d46a4db7131e105efe6bd7576944a1b.tar.gz
  mkdir work && tar xzf dev.tar.gz -C work --strip-components=1
  cd work
  for p in nuvio-arrow-nav-0.1.27 nuvio-arrow-nav-fixes nuvio-focus-ring \
           nuvio-text-input-tracker nuvio-focus-hardening nuvio-focus-round3 \
           nuvio-focus-round4 nuvio-focus-round4b nuvio-focus-round4c \
           nuvio-focus-round4d; do
    patch -p1 < <repo>/packages/nuvio-src/$p.patch
  done
  ```
- Journal after a couch pass (the app prints `nuvio-nav:` per press and
  `nuvio-focus: <tag>` when a tagged node takes focus):
  ```sh
  ssh brook 'journalctl --user -u cwr-app-nuvio --no-pager -n 6000 | \
    grep -E "nuvio-nav:|nuvio-focus:"'
  ```
  Ignore `nuvio-focus: X` lines from Oct-05 sessions (old log format, not
  focus landings).
- Patch methodology (Dev base): edit the tree → `diff -ruN` via symlinked
  `a`/`b` dirs (a = previous state tree, b = edited) → apply-check on the
  previous state (`git apply --check` or `patch -p1 --dry-run`) →
  chain-fidelity check (fresh Dev extract + all patches == edited tree) →
  copy into `packages/nuvio-src/` → add to the `default.nix` patch list (src
  is rev-pinned to Dev; `deps.json` covers `jna` 5.19.1) → build on brook
  (`nix build .#packages.x86_64-linux.nuvio-src`) → `nixos-rebuild switch`
  → couch pass. nixpkgs applies `patches` with GNU `patch -p1`, so generated
  patches must apply with plain `patch` (verified every round).
- Key files: `AppShellComponents.kt` (rail items, avatar, sidebar),
  `SettingsScreen.kt` (category column, content group, page effect),
  `SettingsComponents.kt` (`SettingsSidebarItem`, `TabletPageHeader`),
  `MainAppContent.kt` (anchor, Esc hook), `PlatformKeyboardNavigation.desktop.kt`
  (dispatcher), `PlatformFocusPresence.kt` (anchor state),
  `SettingsFocusLinks.kt` (category/content/rail registry),
  `RootTabHost.kt` (per-tab Left → rail icon), `HomeHeroSection.kt` /
  `LibraryScreen.kt` (content-surface rings).

## Next session (resume point, 2026-10-07)

Priority: **couch retest 7** on the round-4d build (launch from the tile; the
app is stopped). Focus items from retest 6:

1. **Non-Settings D-pad (the retest-6 blocker):** Right from a rail item on
   Home/Search/Library must land with a visible ring (`home-hero` /
   `library-source-chip` tags) and the page must be navigable; A activates.
2. Subpage entry: ring lands on the first content entry with no back-button
   flash; Up → back button; B pops on first press.
3. Settings Root: Up on the topmost content entry stays (no categories leak).
4. Switch rows: full ring on all four sides; A toggles.
5. Regression pass: rail corridor, Right from any rail item on Settings →
   active category, Left returns, profile stack, text fields.

Still-open backlog (after retest 7):
- Ringless surfaces only reachable deeper in flows: `HomePosterHoverPreview`,
  `StreamsScreen` filter row, `CollectionEditorScreen` (×6), `AuthScreen`
  (×3), `DeviceLinkAuthSection`.
- Cosmetic: outer ring corners on first/last settings rows can meet the card's
  rounded corner diagonally.
- **Consolidation round (2026-10-08):** the headless focus-contract harness,
  supported-API replacements (NaN anchor → visible parking; hand-wired edges →
  `focusGroup`/`onEnter`/`onExit`), `focusCorridor`/`FocusableSurface`
  abstractions, patch-chain consolidation, and the upstream plan are now in
  [nuvio-focus-consolidation.md](nuvio-focus-consolidation.md). Take those next
  after retest 7; do not add new round-N patches here.
