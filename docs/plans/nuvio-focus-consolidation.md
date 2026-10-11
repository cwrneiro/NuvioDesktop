# Plan — Nuvio focus navigation, consolidation round (testability, abstractions, upstream)

Status: **2026-10-09: all session acceptance green (E/F couch-verified PASS,
regression PASS).** Deployed through the 16th patch
`nuvio-focus-ef-fixes.patch` (package `k9c0zhw1…`, generation `zb2mbjgv…`).
Next: P2 (`FocusableSurface`, scoped focus state), patch-chain consolidation,
upstream PRs. The couch iterations are tracked in the implementation log below;
a fresh-session resume point is at the end of this file. Read this file end to end
before writing code. Supersedes the short "Dev-cycle improvements" note at the
tail of [nuvio-focus-graph-round4.md](nuvio-focus-graph-round4.md). Provenance:
oracle consult **ora-1** (retrospective) + **ora-2** (corridor design + retest-7
root cause) + librarian research **lib-1**, all 2026-10-08; findings folded into
STATE s13 addendum 8 / addendum 9 and ADR finding 21 (retrospective section).

This round does **not** chase a new couch finding. It changes *how* we iterate:
freeze verified behavior in tests, replace workarounds with supported APIs,
consolidate the patch chain, and push the generalizable deltas upstream.

## Where things stand (do not re-derive)

- Base: upstream **Dev** `d4bdf258` with **10 cwrneirOS patches** in
  `packages/nuvio-src/` (`nuvio-arrow-nav-0.1.27` … `nuvio-focus-round4d`).
- Deployed: package `ygicp2m4frgn51cqf74rqjrwyr651729`, generation
  `pgssrv1l78bjs7xnqylsbc2mb580f473`. **Couch retest 7 pending** (round-4d
  fixes: non-Settings content rings, switch-row ring, subpage same-frame
  handoff, Root first-entry Up cancel). Acceptance in
  [nuvio-focus-graph-round4.md](nuvio-focus-graph-round4.md) §Acceptance.
- Seven couch retests proved the architecture (OS-level key synthesis; no
  app-side controller code anywhere in the Nuvio family) but exposed a
  **process** problem:
  - every round adds a **new numbered patch** rather than revising;
  - there is **no regression net** — a future change can silently re-break a
    hardware-verified behavior;
  - **couch is the only gate** (remote key injection into Nuvio is structurally
    blocked by AWT Globally-Active focus reversion, ADR finding 20), so each
    round costs build + deploy + a human pass.
- The bugs were not ten unrelated defects. They are four recurring classes,
  each a violation of one missing invariant.

## The missing invariant

> For every focus stop, there should be a single declaration of (a) that it is
> a stop, (b) its ring, (c) its directional edges — and the global invariant
> **focus is never nowhere, invisible, or ringless** must hold.

The current code expresses none of this centrally: rings are applied per call
site with a manually-enforced chain-order rule; edges are per-item
`FocusRequester` wiring; "focus exists" is checked ad hoc; the debug tags that
would make violations observable are scattered through production files.

| Bug class | Instances |
|---|---|
| Invisible landings | retest-3 `anchor=true`; retest-6 non-Settings Right vanished |
| Edge leaks | rail↔categories; content-pane bottom → rail; Root top → categories |
| Composition races | subpage back-button request; anchor stealing focus |
| Ringless / dual-target surfaces | PR #180 mobile-only rings; hero/library; profile trigger; `.focusable()` (PR #180) and Material3 `Switch` both added a second stop |

## Workstreams

### P0 — before the next behavior change

**1. Headless focus-contract harness.** The single highest-leverage change:
freeze the seven rounds of verified behavior as executable assertions so retest
7 validates *new* behavior instead of re-validating old. The repo already has
the infra: `desktopTest` with `compose.desktop.uiTestJUnit4`
(`build.gradle.kts:1235-1238`) incl. `captureToImage().toAwtImage()`
(`DesktopJellyNavigationTest.kt:126`), and `androidHostTest` (Robolectric,
already hosts `RootTabHostTest.kt`) for commonMain logic.
Components:
- **A. Table-driven focus-graph spec** — rows of
  `(fromTag, direction, expectedTag | null)`, driving
  `focusManager.moveFocus`; encode `nuvio-focus-graph-round4.md:139-151`
  directly. Assert per row: expected focus, and **never** the recovery-anchor
  tag.
- **B. Pure dispatcher decision** — extract
  `resolveArrowKey(keyCode, textActive, multilineActive): FocusDirection?`
  from `PlatformKeyboardNavigation.desktop.kt:38-51`; unit-test the truth
  table (Left/Right pass to caret only when text active; Up/Down only when
  multiline active).
- **C. Ring semantics marker** — have the ring modifier emit a semantics
  property (e.g. `focusRing = true`) and assert
  `onNodeWithTag(tag).assert(keyIsDefined(FocusRing))`, instead of pixel
  diffing.
- **D. Focus-tree invariant** — walk the semantics tree: exactly one focused
  node; it is ringed; it is not the anchor.
- **E. Journal-contract script** — `scripts/focus-contract.sh` pulls
  `nuvio-nav:`/`nuvio-focus:` from the unit journal and asserts:
  `moved=false` only at documented edges; every landing tag ∈ the expected set.
  Makes a couch run self-verdicting rather than a human eyeball.

**Runner (verified working 2026-10-08).** The packaging build does not run
tests, and the dev box cannot host Gradle; the suite runs **on brook** outside
the nix sandbox, with network (the lockfile `deps.json` has no test deps).
Recipe: sync the patched tree to brook, provide `local.properties`, then run
under `nix shell nixpkgs#jdk17_headless nixpkgs#gradle_9 nixpkgs#xvfb`:
start `Xvfb :99 -screen 0 1600x1000x24 +extension GLX` and export `DISPLAY=:99`,
`LIBGL_ALWAYS_SOFTWARE=1`, `SKIKO_RENDER_API=SOFTWARE`; set `LD_LIBRARY_PATH`
from the built app's Skiko RPATH
(`patchelf --print-rpath <app>/lib/app/libskiko-linux-x64.so` → libglvnd /
libx11 / fontconfig-lib / gcc-lib dirs). Then
`./gradlew --no-configuration-cache -Dorg.gradle.java.home=$JAVA_HOME`
`-Pkotlin.native.ignoreDisabledTargets=true`
`-Pkotlin.native.enableKlibsCrossCompilation=false`
`-x :composeMediaPlayer:buildNativeLinux -x :composeApp:buildLinuxPlayerBridge`
`:composeApp:desktopTest --tests <class>`. Verified end-to-end: `ArrowKeyNavTest`
4/4 and `FocusContractTest` (real `DesktopHoverSidebar` + `RootTabHost` +
LazyColumn content) pass. Note: `SkikoComposeUiTest` needs both a display and
`libGL`/`libfontconfig` on `LD_LIBRARY_PATH` — `nix shell` does not set
`LD_LIBRARY_PATH`, so the RPATH extraction is required. This is what makes the
focus-graph/ring/text classes independently testable without the couch.

Current suite (all green on brook): `ArrowKeyNavTest` (4), `FocusContractTest`
(rail Right lands on a ringed+actionable node, never the pane anchor; rail
corridor invariant walk), `SettingsSubpageEntryTest` (real `SettingsScreen`
subpage entry lands on a ringed+actionable node), plus
`scripts/focus-contract.sh` — a journal verdict (no `anchor=true`, all landing
tags known, longest `moved=false` dead-end run reported). The script flagged a
17-press dead-end run in the pre-13th-patch journal.

**2. Diagnostics facade.** Replace the 10 `nuvioDebugFocusTag` /
`nuvioDebugFocusGroupTag` call sites in 6 production files with one
`NuvioFocusDiagnostics` sink gated behind a single flag (`-Dnuvio.focus.debug`
or a build const). One switch removes all diagnostics at closeout; the same
modifier also attaches the semantics marker from C.

**3. `focusCorridor` first cut.** A declarative 1-D chain that auto-wires
neighbour edges and defaults the ends to `Cancel`, so a corridor no longer
depends on geometric 2D search. **Shipped as `corridorEdges(...)`** (returns a
single `FocusProperties.() -> Unit`; caller overrides run last in the same
block, so the Settings-tab Right redirect and the avatar profile-stack Down
override cannot be clobbered — see the precedence note in `FocusCorridor.kt`).
Migrated the desktop **rail**; the **settings category column is deferred**
(oracle ora-2): only the active category is a real stop today
(`SettingsScreen.kt:1096`), so migrating it requires new per-category
requesters — a graph change that is *not* behavior-neutral and would muddy the
pending retest 7. Follow-up spec is in the ora-2 consult / ADR finding 21.

### P1 — with/just after retest 7

**4. Visible parking (replace the NaN anchor).** When focus is nowhere, park it
on a real, ringed, on-screen node — the active tab's rail icon or the page's
first entry — instead of an invisible 0×0 anchor. Removes `anchorActive`, the
dispatcher's clear-and-retry branch (`PlatformKeyboardNavigation.desktop.kt:53-59`),
and the undocumented NaN `focusRect` dependency in one move. Keep the anchor
object only as a last resort if a real target is not yet composed.

**5. `FocusGraphTest` acceptance encoding.** Land harness A–D as a suite that
covers the round-4 acceptance list; make it a required step in the retest
bundle.

### P2 — after the harness is green

**6. `FocusableSurface`.** A composable that owns the focus node + ring + tag
together and applies the ring in the correct chain position internally:
`FocusableSurface(shape, onClick, tag, modifier, content)`. Migrate the ~50
ring call sites incrementally; back it immediately with an
`assertFocusablesRinged` test. Then close the known ringless backlog:
`HomePosterHoverPreview` (×2), `StreamsScreen` filter row,
`CollectionEditorScreen` (×6), `AuthScreen` (×3), `DeviceLinkAuthSection`.

**7. Scoped `FocusLinks` + injectable `FocusNavigator`.** Replace the
process-global `SettingsFocusLinks` (`SettingsFocusLinks.kt:7-13`) with a
`CompositionLocal` / injected `FocusGraphController`, and introduce
`interface FocusNavigator { fun handleArrow(dir): Boolean }` with a desktop AWT
impl and a fake for tests. Inject `FocusPresenceState` instead of reading the
global object from desktopMain.

### P3 — consolidation & upstream

**8. Text-tracker consolidation.** One `TextInputFocusState` interface
(`isActive` / `isMultilineActive`) with one desktop actual and no-op others;
keep the documented counter invariant (`DesktopTextInputState.kt:14-19`).

**9. Consolidate the patch chain.** Merge rounds 3–4d into a single
`nuvio-focus-graph.patch` (they overlap `AppShellComponents.kt`,
`SettingsScreen.kt`, `SettingsComponents.kt`). Reduces context-shifting patches
over the most contended files from 5 to 1. Behaviour-free; verify chain
fidelity on a fresh Dev extract with GNU `patch` as usual.

**10. Upstream the generalizable deltas** (see §Upstream).

## Implementation log — P0 (2026-10-08, session 13 continued)

Shipped as the 11th patch `packages/nuvio-src/nuvio-focus-p0.patch` (applies
after `nuvio-focus-round4d.patch`; chain fidelity re-verified on a fresh Dev
`d4bdf258` extract with GNU `patch`; `default.nix` updated). Behavior-neutral:

- **`resolveArrowKey`** — `core/ui/ArrowKeyNav.kt` (commonMain):
  `NuvioNavKey` + `shouldConsumeArrowForFocusNav(key, textFieldActive,
  multilineFieldActive)`, an exact extraction of the AWT dispatcher's inline
  gating. `PlatformKeyboardNavigation.desktop.kt` now maps `KeyEvent` →
  `NuvioNavKey` → `FocusDirection` and calls it. Test:
  `commonTest/.../ArrowKeyNavTest.kt` (full truth table, `kotlin.test`).
- **Diagnostics facade + ring semantics** —
  `core/ui/NuvioFocusDiagnostics.kt`: one flag-gated `log()` sink and a
  `FocusRingSemantics` (`nuvioHasFocusRing`) marker. `NuvioFocusable.kt` routes
  `nuvio-focus:`/`nuvioDebugFocus*` through the sink and attaches the marker to
  `nuvioFocusBorder`; the 15 production call sites and their public signatures
  are unchanged. `PlatformKeyboardNavigation.desktop.kt` logs `nuvio-nav:`
  through the same sink.
- **`focusCorridor` first cut** — `core/ui/FocusCorridor.kt`:
  `CorridorOrientation` + `corridorEdges(orientation, requesters, index,
  start = Cancel, end = Cancel, overrideEdges = null)`. The desktop rail
  (`AppShellComponents.kt`) migrated to a `railRequesters` corridor with indices
  0–4; overrides preserve every hardware-verified edge: `avatar.up = Cancel`,
  `Settings.down = Cancel`, avatar `down = Default` when `profileStackVisible`
  else Home, `right = categoryEntry` on the Settings tab for all five items,
  and the Settings-tab NaN `focusRect` guard on the four nav items. Categories
  deferred (see P0-3).

**Verification status:** patch applies cleanly to the patch-10 state,
reproduces the working tree exactly, and the package **builds on brook
(EXIT=0) and is deployed** (package `aq7blsw…`, generation `m1gry23…`). The
packaging build compiles all main/desktop sources (including the three new
files and the `AppShellComponents.kt` rail migration) but does **not** run
`commonTest`, so `ArrowKeyNavTest` still needs wiring into the P0 harness (P0
item 1). Behavior is intended to be identical, so validate retest 7 as a
regression pass: rail corridor, avatar profile stack, Right-from-rail-on-
Settings, Left return-to-owner, text-field caret, plus the round-4d acceptance
items.

### Retest-7 fixes (12th patch `nuvio-focus-retest7-fixes.patch`)

The P0 couch pass (retest 7): items 2/3 pass; item 1 (non-Settings rail
Right/A), item 4 (ring coverage + poster text field), and a new regression
(category→content when scrolled) failed. `ora-2` root-caused all four from the
Oct-08 journal (`/tmp/nuvio-oct08.txt`; `anchor=true` = 0, `home-hero` /
`library-source-chip` landings = 0) and source:

1. **Blocker.** `RootTabHost.kt` forced `canFocus = active` on the pane Box.
   Home/Search/Library content roots are `LazyColumn`s whose scroll anchor is a
   `Focusability.Never` focus target; forcing it `true` made it a legal 2D
   candidate with the full content rect, so Right from any non-Settings rail
   item landed on an invisible, dead-end pane anchor (no ring, no click, every
   further arrow `moved=false`; one Left returned via the explicit edge).
   Enter/A "dropped focus" for the same reason (the anchor has no click). Fix:
   `if (!active) canFocus = false` — only opt inactive panes out.
2. **Ring coverage.** `HomescreenCatalogRow` (CATALOGS & COLLECTIONS) was a
   multi-stop ringless cluster; fix mirrors `SettingsSwitchRow` (row ring +
   4 dp inset, inner `Switch` `canFocus = false`, lock/drag `IconButton`s
   suppressed). `PosterToggleRow`'s bare `Switch` and `PosterStyleOptionRow`'s
   `FilterChip`s are now ringed.
3. **Poster text-field trap.** The custom-poster URL field was declared
   multiline (`nuvioTracksTextInput(singleLine = false)`, `maxLines = 4`), so
   Up/Down were (correctly) left to the caret. Reclassified single-line.
4. **Scrolled category→content.** `contentEntry` pointed at the first Root row
   (`contentEntryRequester`), uncomposed once scrolled. Root now targets the
   always-composed `content-group` Box (`contentGroupRequester`); subpages keep
   the concrete header back button.

Must-not-break: the pane `canFocus` toggle was the tab-switch focus-clearing
mechanism (inactive panes still opt out, so clearing is preserved);
Left-from-content→rail for Home/Search/Library now relies on geometry (the
explicit pane `left` edge lived on the anchor, which is no longer focusable) —
flagged for the retest. Verification: patch applies to the P0 state, chain
fidelity == working tree, built `0vnvpv805…`, deployed generation `hji0k8j…`.

### Retest-7 follow-up fixes + first headless tests (13th patch `nuvio-focus-item-fixes.patch`)

Second couch pass on the retest-7 build: items 2/3/5 PASS; item 1 PARTIAL (hero
now focuses, but the hero subtree still had ringless dead-ends); item 4 FAIL
(Settings → General → Layout → scroll → B → reopen Layout → ring gone; Right
landed on the profile/avatar). `ora-2` root causes + minimal fixes:

- **Item 4.** Subpage re-entry restores the saved `LazyColumn` scroll offset, so
  the header back button is uncomposed and `backRequester` is unattached; the
  page-entry effect then never placed focus (focus-nowhere → geometric Right →
  avatar). Fix: `SettingsFocusLinks.contentEntry` now targets the
  always-composed content group on Root AND subpages, and the page-entry effect
  falls back to `contentGroupRequester` when `backRequester` fails. Root's
  auto-entry path still prefers `backRequester` + `moveFocus(Down)` (no flash).
- **Item 1 remaining.** `HeroPageIndicatorRow`/`HeroPageIndicatorDot` were
  ringless, neighbour-less focus stops (now `canFocus = false`; pointer-click
  preserved); `FullscreenActionButton` and the Library/Search header
  `IconButton`s are ringed; `SearchScreen` no longer re-captures focus on focus
  *loss* (only on gain), removing the arrow trap.

Also added the first **headless focus-contract test**
`composeApp/src/desktopTest/.../FocusContractTest.kt`: mounts the real
`DesktopHoverSidebar` + `RootTabHost` + a LazyColumn content root, focuses the
rail, moves Right, and asserts focus lands on a node that is ringed **and**
actionable (never the non-actionable pane scroll anchor). Verification: chain
fidelity == work tree, package built `ijiy2xhi…`, deployed generation
`74z3pkhf…`.

### Ring coverage + Settings UX (14th patch `nuvio-focus-ring-coverage.patch`)

Couch pass on the 13th build: item 4 PASS (two follow-ups requested — reset
subpage scroll on close; return Right to the last-focused row), item 1 PARTIAL
(focus reaches all items, but many display no ring), regression PASS (theme
colors ringless). Audit **exp-3** mapped the ringless class; oracle **ora-2**
chose targeted ring additions now and `FocusableSurface` in P2.

- **Ring coverage (~26 surfaces):** Continue Watching cards (Card/Wide/Poster),
  `CollectionFolderCard` home folders, `PosterGridTile` (Search Discover +
  Library Saved grids), theme chips/swatches/slots, `NuvioDropdownChip`,
  `NuvioViewAllPill`, catalog tiles, recent-search rows, Classic nav items,
  Dialog/Menu/BottomSheet rows, and settings rows (library list editor, update
  channel, streams, debrid preference, licenses, supporters, tracking approval,
  downloads, poster-zoom menu).
- **Q1 — scroll reset:** non-Root Settings subpages now
  `listState.requestScrollToItem(0)` on page entry (Root keeps its position; the
  saver is retained). The item-4 content-group fallback stays as a safety net.
- **Q2 — last-row restore:** `focusRestorer()` added to BOTH the content-group
  Box and its `LazyColumn` (a restorer at each nesting level, since the group's
  only direct child is the scroll anchor); Right from a category now restores
  the previously focused row via the group's `onEnter` → `restoreFocusedChild()`.

Verification: chain fidelity == work tree; all 7 headless tests green
(`FocusContractTest` 2, `SettingsSubpageEntryTest` 1, `ArrowKeyNavTest` 4);
package built `8iriw9ka…`, deployed generation `jvlg25jr…`.

### Behavior fixes (15th patch `nuvio-focus-behavior-fixes.patch`)

Couch pass on the 14th build: items 1/2/3 PASS with four follow-ups; `ora-2`
root-caused each.

- **A — double Up/Down in Home folders:** the desktop shelf **title** is itself
  a focus stop (`ShelfComponents.kt` title `.clickable`) and, being full-width,
  is the nearest node below every poster row. Fixed by keeping the title
  pointer-clickable but `canFocus = false` (the "View all" pill stays a stop).
- **B — details focus stuck on back:** the details screen had no entry focus
  effect, so after the nav push removed the focused poster, focus parked on the
  header back button. Fixed by threading a `FocusRequester` to the primary (play)
  action (`DetailActionButtons`/`DetailActions`/`DesktopDetailHero`) and
  requesting it across up to 8 frames on open; `detail-play`/`detail-back` debug
  tags added.
- **C — re-entry flicker:** the `requestScrollToItem(0)` reset ran post-paint.
  Replaced with a Root-only saver: non-Root pages use a plain
  `remember(LazyListState)` (no restore → no flicker), Root keeps its position.
- **D — Left went to the back button:** the content-group `left = categoryEntry`
  does not reach rows (the LazyColumn scroll anchor sits between). Added an
  explicit `left = categoryEntry` to `SettingsNavigationRow` and
  `SettingsSwitchRow` (row-level, so it does not collide with `focusRestorer`'s
  `onExit`).

Verification: all 7 headless tests green; chain fidelity == work tree; package
`cqa30kaq…`, deployed generation `azs7cmg…`.

### E/F fixes (16th patch `nuvio-focus-ef-fixes.patch`) — implemented, build pending

Couch pass on the 15th build: A/B/C/D PASS; two follow-ups.

- **E:** Left from the Settings subpage **back button** still landed on the
  profile. Fixed by adding `left = SettingsFocusLinks.categoryEntry` on the
  `TabletPageHeader` back button (`SettingsComponents.kt`) — the row-level D
  pattern applied to the header.
- **F:** the Home folder category arrows open `CatalogScreen` ("View all"), whose
  entry focus parked on the back button. Fixed with `focusGroup()` +
  `FocusRequester` on the grid-wrapping Box, requested across frames when items
  load (the grid is declared before the header, so the group enters the first
  tile).

Verification: patch applies to the 15th state; chain fidelity == work tree;
package built `k9c0zhw1…`, deployed generation `zb2mbjgv…` (2026-10-09, no
reboot). **Couch-verified PASS** (E: Left from back → categories; F: catalog
opens onto the first tile), regression PASS.

## Supported-API replacements (stop the workarounds)

Adopt the official APIs available in CMP 1.12 instead of custom machinery:

- **NaN `focusRect` hack → structural de-focus.** Remove
  `.focusable()`/`.clickable()` from aggregate/wrapper nodes that should not be
  2D candidates, and use `focusProperties { canFocus = false }` for
  decorative/aggregate containers. This is what #180's newest commit does for
  shelf wrappers. NaN comparisons are undocumented and can change without
  notice.
- **Hand-wired `up/down/left/right` edges → `focusGroup()` +
  `focusProperties { onEnter/onExit }`** (with `cancelFocusChange()` and
  `FocusRequester.Cancel`/`Redirect`) for boundary shaping. This is the
  official custom-focus-search mechanism and reduces per-item wiring.
- **Hand-wired "return to owner" / restore → `focusRestorer(fallback)` +
  `saveFocusedChild()` / `restoreFocusedChild()`.** Caveat: since Compose
  1.9.0-alpha01 `FocusRestorer` no longer pins the item, so restored items need
  **stable composition keys**. Lazy containers are implicit focus groups and
  already restore.
- **Future:** `androidx.navigationevent` 1.2.0 (stable 2026-10-07) is the
  blessed KMP back/predictive-back API; upstream's merged #791 used a
  hand-rolled registry instead. Not this round.

## What NOT to abstract (YAGNI)

- The evsieve / OS input layer — already a clean, verified contract (ADR §3).
- A general TV-focus DSL/framework (no serialization, no plugins).
- The AWT dispatcher into a Compose-only key handler — Compose Desktop has no
  root key interceptor; the dispatcher is required for the "no node focused
  yet" case.
- D-pad repeat / fast-scroll throttling until the player round with real media
  (NuvioTV's `DpadThrottleModifier` 80/112 ms numbers are recorded as spec).
- Ring animation / marquee (`FocusMarqueeText`-class) yet.
- A full geometry-agnostic "region" model — right in principle, over-scoped
  for ~4 regions; revisit past ~50 focus nodes.

## Must-not-break (hardware-verified contracts)

- Press-time **A = key-up** activation; do not move activation to key-down
  (ADR finding 19.3).
- Rail corridor, including avatar `down = Default` while the profile switcher
  stack is open, and the Settings-tab rail NaN `focusRect` non-candidacy
  (`AppShellComponents.kt:870,896,922,948`) until the structural replacement
  lands.
- **Single-owner Esc** in `MainAppContent.kt:1285-1303`; the AWT dispatcher
  returns `false` for `VK_ESCAPE`.
- Rings observe `hasFocus`, not `isFocused`; outset ring placed **before** any
  `.clip` (`NuvioFocusable.kt:39-45,54`).
- Material3 `Switch` inner `focusProperties { canFocus = false }`
  (`SettingsComponents.kt:416-418`).
- Lazy `bringIntoView` stays free — do not add scroll code.

## Migration order

- **P0:** diagnostics facade → `resolveArrowKey` + unit test → `focusCorridor`
  first cut (rail + categories). No behavior change; immediate leverage.
- **P1:** visible parking → `FocusGraphTest` encoding the acceptance list.
- **P2:** `FocusableSurface` + `assertFocusablesRinged` + ringless backlog →
  scoped `FocusLinks` + injectable `FocusNavigator`.
- **P3:** text-tracker consolidation → patch consolidation → upstream PRs.

## Upstream contribution plan (updated 2026-10-08)

Upstream state (lib-1, GitHub API 2026-10-07/08):
- **PR #180 (arrow navigation) is OPEN, not merged** — blocked on maintainer
  re-review since 2026-09-05 (reviewer's three points already answered by the
  author's 2026-10-06 pushes); 5 open UX bugs from a tester remain. Desktop-input
  PRs otherwise land fast (#791 open→merged in 7 days).
- **PR #791 merged** to Dev 2026-10-06 (`d4bdf258`); our back patch already
  dropped. **New PR #853** (macOS Esc/Settings, 2026-10-07) touches
  `MainAppContent.kt`/`SettingsScreen.kt`/`Main.kt` — reconcile at the next
  rebase.
- **NuvioTV / NuvioWeb already own explicit focus stacks**
  (`FocusRestoreUtils.requestFocusAfterFrames`, `DpadThrottleModifier`,
  `FocusRingStyle`, `DrawerFocusRequesters`; Web `focusEngine.js`). Framing our
  work as **"bring Desktop to TV/Web parity"** is the strongest pitch.
- CMP's own docs do not document arrow/D-pad 2D desktop navigation — a
  contribution gap.

Contributions (small, focused, one problem each):
1. #180 review feedback: the double-`.focusable()` fix + `hasFocus` outset-ring
   guidance (reviewer already asked for both); desktop-surface coverage list.
2. The text-input arrow tracker — still #180's acknowledged blocker; most
   self-contained and generally useful.
3. "Seed focus when a screen opens with none" (the legitimate Compose-Desktop
   gap the AWT dispatcher covers) once visible parking lands.
Do **not** upstream the NaN anchor — it is a workaround for a missing parking
model, not a feature.

## Exit criteria for the fork (when V1.2 stops forking)

- Retest 7 passes **and** a dedicated regression retest passes on the same
  build.
- The headless focus-contract suite passes and covers the round-4 acceptance
  list.
- Upstream PRs for the generalizable deltas are filed (merged or pending).
- The deep-flow ringless backlog is covered or explicitly waived.

## Working notes

- Base, patch methodology and journal-pull commands are unchanged — see
  [nuvio-focus-graph-round4.md](nuvio-focus-graph-round4.md) §Working notes and
  §Next session. Working tree: `/tmp/opencode/nuvio-spike/work` (tmpfs; it was
  wiped once this session). Regenerate from the repo patches: fetch the Dev
  tarball `d4bdf2588d46a4db7131e105efe6bd7576944a1b`, extract, then
  `for p in $(the 17 patch names in §Resume point); do patch -p1 < packages/nuvio-src/$p.patch; done`.
- Retest bundle target: `nix build .#nuvio-src` → headless focus suite →
  (brook) launch where possible → pull journal → `scripts/focus-contract.sh`.
  The human couch pass then tests only feel/latency/physical mapping.

## Resume point (fresh session, 2026-10-09)

## Resume point (fresh session, 2026-10-09)

**Status:** E/F couch-verified PASS; regression PASS. Deployed through the 16th
patch `nuvio-focus-ef-fixes.patch` — package `k9c0zhw1…`, generation
`zb2mbjgv…` (2026-10-09). All focus acceptance items from this session are green.

Operational: brook's battery is not holding a charge — **keep it on AC power**
(it lost power repeatedly this session). The local `/tmp` working tree was wiped
once; regenerate it from the repo patches before running the headless suite (base
+ patch list in §Working notes; current chain is the 17 patches below).

Next (fresh session): **P2** — `FocusableSurface` (own focus node + ring + tag)
and migrate the ring call sites; scoped focus state
(`FocusLinks`/`FocusNavigator`); then patch-chain consolidation and upstream PRs.

**Autonomous harness:** run the focus suite on brook — sync the tree to
`/tmp/nuvio-test`, copy `packages/nuvio-src/local.properties`, then
`nix shell nixpkgs#jdk17_headless nixpkgs#gradle_9 nixpkgs#xvfb -c /tmp/run-focus-tests.sh`
(the script starts `Xvfb :99` and sets `LD_LIBRARY_PATH` from the built app's
Skiko RPATH, then runs `:composeApp:desktopTest` filtered to the contract tests).
Journal verdict: `scripts/focus-contract.sh <journal>`.

**Patch chain (17):** `nuvio-arrow-nav-0.1.27`, `nuvio-arrow-nav-fixes`,
`nuvio-focus-ring`, `nuvio-text-input-tracker`, `nuvio-focus-hardening`,
`nuvio-focus-round3`, `nuvio-focus-round4`, `nuvio-focus-round4b`,
`nuvio-focus-round4c`, `nuvio-focus-round4d`, `nuvio-focus-p0`,
`nuvio-focus-retest7-fixes`, `nuvio-focus-contract-tests` (dev-only),
`nuvio-focus-item-fixes`, `nuvio-focus-ring-coverage`,
`nuvio-focus-behavior-fixes`, `nuvio-focus-ef-fixes`. Consolidate rounds 3–4d +
the follow-ups in P3.

**Known residuals (ora-2 handoff):**
- Other "back-button, no entry focus" full-screen destinations likely share F's
  class: `PersonDetailScreen`, `TmdbEntityBrowseScreen`, `CollectionEditorScreen`,
  `CollectionManagementScreen`, `FolderDetailScreen`, Streams/Downloads screens.
  Audit each for an entry-focus request; add debug tags on their back buttons.
- `NuvioViewAllPill` remains a focus stop between shelves (possible double-press
  in the right column) — decide exclude vs a poster up/down corridor.
- Custom `SettingsChipRow`/filter chips still fall back to geometric Left.
- NaN recovery anchor / visible parking (P1) still outstanding.
- Ringless backlog: `HomePosterHoverPreview`, Streams filter row,
  `CollectionEditorScreen` (×6), `AuthScreen` (×3), `DeviceLinkAuthSection`.
- `focusRestorer` cross-page relies on the per-page `SaveableStateProvider`; add
  a test asserting restoration does not leak across pages.

## P2 spec (fresh-session starting point) — FocusableSurface + scoped focus state

Written 2026-10-09 while brook was down; no code yet.

### `FocusableSurface` (ring-by-construction)
Replace the per-call-site `nuvioFocusBorder` + `clickable`/`selectable`/
`posterCardClickable` pattern — the root of every ring-coverage miss — with one
component that owns the node + ring + semantics and applies the ring in the
correct chain position internally.
```kotlin
@Composable
fun FocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    borderWidth: Dp = 3.dp,
    role: Role? = null,            // null = clickable; else selectable
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    focusTag: String? = null,      // diagnostics + semantics
    content: @Composable () -> Unit,
)
```
Internally the ring is always outermost (before any `.clip` and before the
click/select target): `modifier.nuvioFocusBorder(shape, borderWidth).then(
if (role == null) Modifier.clickable(...) else Modifier.selectable(...))`.
- Migrate the ~40 ring call sites from the exp-3 audit area by area (poster
  cards/`NuvioPosterCard`, Continue Watching cards, folder cards, `PosterGridTile`,
  catalog tiles, theme chips/swatches, `NuvioDropdownChip`, settings rows).
- Delete the ad-hoc `focusRect = NaN` / manual chain ordering where the wrapper
  subsumes it.
- Add `assertFocusablesRinged()` (walk the semantics tree: every focusable with a
  click/select action must carry `FocusRingSemantics`) and run it over each
  migrated subtree in `desktopTest`.

### Scoped focus state
Replace the process-global `core/ui/SettingsFocusLinks.kt` with a
`CompositionLocal<FocusLinks>` (or an injected `FocusGraphController`) exposing
the same fields (`categoryEntry`, `contentEntry`, `railEntries`). Read via
`LocalFocusLinks.current`; removes cross-test global leakage and makes tests
parallel-safe. Update `AppShellComponents`, `RootTabHost`, `SettingsScreen`,
`SettingsComponents` accordingly.

### `FocusNavigator` seam
Extract the desktop AWT dispatcher's decision body behind
`internal interface FocusNavigator { fun handleArrow(key: NuvioNavKey): Boolean }`
with a desktop AWT impl and a fake for tests. The pure
`shouldConsumeArrowForFocusNav` already exists (`core/ui/ArrowKeyNav.kt`).

### Visible parking (P1 residual)
Replace the NaN-`focusRect` recovery anchor with visible parking: request a real
ringed node (the active tab's rail icon / first page entry) whenever main-scene
focus is nowhere, so the recovery path no longer depends on undocumented NaN
semantics. Keep the AWT dispatcher's clear-and-retry as a fallback only.

### Patch-chain consolidation (P3)
Merge rounds 3–4d + the follow-ups (patches 6–14: `nuvio-focus-hardening` …
`nuvio-focus-ring-coverage`) into one `nuvio-focus-graph.patch`; keep P0 + the
dev-only test patch separate; verify chain fidelity on a fresh Dev `d4bdf258`
extract with GNU `patch`; then upstream the generalizable deltas (`hasFocus`
outset-ring guidance, the text-input arrow tracker, seed-focus-on-open) while
PR #180 is active. Do not upstream the NaN anchor.
