# Nuvio — decision record & field findings (extracted from cwrneirOS ADR 0001)

*Provenance: extracted verbatim from `docs/adr/0001-tv-shell-session.md` in cwrneirOS. Original section and finding numbering is preserved for traceability. OS-side integration (session, compositor, input bridge, packaging flake) lives in cwrneirOS; only the Nuvio-relevant material is reproduced here.*

[ADR lines 105–117 — §4 "App distribution"; the opening lines are cwrneirOS OS-side distribution framing, retained verbatim for context:]

### 4. App distribution — hybrid, nixpkgs-primary

Flatpak-primary is **rejected** on NixOS + NVIDIA: nothing automates the Flathub
NVIDIA GL runtime (`org.freedesktop.Platform.GL.nvidia-*`); driver/runtime version
mismatch black-screens Flatpak GUIs. Stance:

- **nixpkgs-native** wherever a package exists (mpv, utilities; phase-2 Steam via
  `programs.steam`).
- **Nuvio:** not in nixpkgs, no community flake → wrap the upstream **AppImage** with
  `appimageTools.wrapType2` in the cwrneirOS flake (declarative, pinned, rollback-safe;
  Nuvio bundles JRE + libmpv, making it a good wrap candidate). Trade-off on record:
  the wrapper is ours to maintain against an alpha app; the upstream-maintained Nuvio
  Flatpak is the per-app escape hatch if release-chasing gets expensive.

[ADR line 154 — "Alternatives considered" table row:]

| Nuvio native gamepad/TV mode | Rejected (infeasible) | Upstream has neither (NuvioMedia/NuvioDesktop#787, #305) — validates the input bridge |

[ADR line 176 — "Consequences → Negative / accepted costs":]

- Nuvio packaging burden (alpha, fast-moving) sits in our flake.

[ADR lines 216–217 — "Contingency":]

- Nuvio AppImage wrap rots → switch that one app to its upstream Flatpak (driver
  pinned) or DEB-in-FHS.

[ADR line 457 — "References":]

- Nuvio: <https://github.com/NuvioMedia/NuvioDesktop> (#787 controller, #305 TV layout)

[ADR lines 563–650 — Finding 20 in full:]

## Finding 20: App-3 — Nuvio wrap + AWT/XWayland focus fix (2026-10-05)

**Re-opened (controller interaction) 2026-10-05; wrap/focus findings stand.**
Nuvio Desktop wrapped from the upstream AppImage and hardware-verified; one
compositor↔toolkit focus bug found and fixed with a per-app helper. In-app
controller interaction is limited to A + SELECT (upstream has no arrow
navigation) — not accepted; alternatives under evaluation (see STATE
session 11 / ROADMAP V1.2).

1. **Packaging (ADR §4).** `packages/nuvio.nix` wraps the pinned
   **0.1.27-alpha** type-2 AppImage (`appimageTools.wrapType2`; the release
   sha256 published by the GitHub API is enforced at build). Nuvio is
   **Kotlin Multiplatform + Compose Desktop (Skiko)** — not Electron: its
   window is X11/XWayland. The Linux AppImage bundles the JRE but links the
   SYSTEM `libmpv.so.2` and WebKitGTK (Windows/macOS bundle libmpv; Linux
   does not), so the wrapper puts `mpv`, `webkitgtk_4_1`, `gtk3`, `glib`,
   `glib-networking`, `libX11`/`libXcomposite`/`libXext`, `libGL`/`libgbm`
   and `stdenv.cc.cc` on `LD_LIBRARY_PATH`, sets
   `_JAVA_AWT_WM_NONREPARENTING=1` and `GIO_EXTRA_MODULES`. Upstream is
   active alpha with a per-release breaking-change warning → the tag is
   pinned; the nixpkgs source-build PR (#543092, which also patches the
   in-app updater off) is the recorded fallback.
2. **Keyboard dead on launch — wlroots ICCCM Globally-Active gap (root
   cause).** Java AWT advertises `WM_HINTS input=False` + `WM_TAKE_FOCUS`
   (the ICCCM *Globally Active* input model, hardcoded in
   `XWindowPeer.updateFocusability()`; no `-D` property exists). wlroots'
   XWM sends `WM_TAKE_FOCUS` **once** at first activation and never calls
   `XSetInputFocus` for such windows. When that single message is missed
   during first map (observed with Nuvio), X focus stays on the AWT
   **toplevel**, which selects no `KeyPress` mask — every key is discarded
   and the app looks dead to both gamepad and keyboard while EWMH still
   reports `_NET_WM_STATE_FOCUSED` ("focused but deaf"). Evidence trail on
   brook: window event masks (`xwininfo -events`), stable X focus
   (`XGetInputFocus`), `xev` receiving XTEST keys while the app ignored
   them, and the physical keyboard only working after a real pointer click
   (which triggers AWT's own `requestWindowFocus` path). Upstream refs:
   sway#6967, wlroots#2604, sway#2231; `wtype` is known-broken for
   XWayland and is not a valid probe (atx/wtype#62).
3. **Fix: `nuvio-focus` helper.** The launcher starts
   `packages/nuvio-focus.c` (a small Xlib program) in the background: it
   waits up to ~20 s for the `com-nuvio-app-MainKt` toplevel, then
   re-sends `WM_TAKE_FOCUS` every 250 ms until X focus reaches a **child**
   of the toplevel (AWT's invisible `FocusProxy`). Two hardening lessons
   from bring-up: "focus != toplevel" is NOT success (Xwayland transits
   through `PointerRoot`, 0x1), and `XQueryTree(PointerRoot)` raises a
   BadWindow X error that terminates a default Xlib client — the shipped
   version guards `PointerRoot`/`None` and installs a non-fatal error
   handler. Verified on hardware: proxy focus at launch and Tab traversal
   live with **no click**.
4. **Gamepad navigation (upstream gap, local bridge).** Nuvio has no
   arrow-key navigation and no gamepad support (upstream #787/#305; the
   arrow-nav PR #180 is unmerged). The evsieve `action-keys` output now
   maps **SELECT → Tab** as an unconditional tap map (focus-routed like
   the nav keys). Phase-gating it was rejected: launching an app with A
   hides the shell without a Guide press, so the evsieve phase stays in
   Shell while the app runs and a phase-gated SELECT would be swallowed.
   SELECT leaves the App-mode pad passthrough (replace, not extend). User-
   verified result: **A activates, SELECT traverses; D-pad/B/shoulders are
   inert in Nuvio** until upstream arrow navigation lands.
5. **Close path = shell-level.** Nuvio's own Esc closes only its player
   overlay (upstream #717), so app close is the shell's B/Esc handler:
   stopping the transient unit when the shell is visible over the app
   (hardware-verified; this also exercised the Guide focus arbitration
   over an XWayland client, which works). Note: `systemctl stop` SIGTERMs
   the wrapper cgroup, so the launcher's exit-notify does not run on this
   path — the shell handler clears `activeApp` itself; clean self-exits
   still notify via `appExit`. No focus-stealing modal was observed, so
   the B1 "popup" subclause stays open.
6. **First run.** Account is optional: "Continue Without Account" (local
   data) is reachable with SELECT+A; account sign-in needs text entry,
   i.e. a physical keyboard until the V1.3 OSK. Documented for the couch
   flow.

**Verified (user-run acceptance, brook 2026-10-05):** wrap builds from the
pinned AppImage (hash enforced); launches from the shell tile, fullscreen,
no chrome; `nuvio-focus` puts X focus on the AWT proxy with no click;
SELECT (Tab) cycles focus and A activates; Guide overlays home; B closes
the app and returns home; Continue Without Account reachable.

**Residuals / watchpoints:** (i) D-pad/arrows inert until upstream #180 or
equivalent — only A + SELECT usable inside Nuvio (accepted V1.2 behavior);
(ii) the in-app updater remains enabled in the AppImage (compiled Kotlin
default, not patchable from outside) — it cannot write the store path;
switch to the source-build route if it misbehaves; (iii) the focus helper
is a per-app workaround pending an upstream `Window.toFront()` after
`setVisible(true)`; (iv) remote-verification note: pointer injection
(XTEST) under XWayland is unreliable for aiming clicks — `wtype` + `grim`
+ SCP (s9 method) was decisive, physical input remains the ground truth.

[ADR lines 652–967 — Finding 21 in full (through end of document):]

## Finding 21: Nuvio controller navigation — source build + focus fixes (2026-10-05, session 12)

**Implementation round 2 landed and deployed 2026-10-06 (session 13);
hardware verification pending the couch pass.** Three per-topic patches now
ship in `packages/nuvio-src/` after the original two (focus ring +
before-clip reorder across all 23 call sites; upstream PR #791 back handling
with single-owner Esc routing; text-input arrow tracker) — scope, exact
semantics and the couch protocol are in the plan and STATE s13. Remote
key-injection verification is blocked by the documented AWT focus reversion:
X focus returns from the FocusProxy to the toplevel within <50 ms of each
`nuvio-focus` success and keys are discarded (wtype/XTEST/XSendEvent/helper
loop all fail to reach AWT), so the physical couch pass is the gate.

**Couch round 1 (2026-10-06) then surfaced focus-topology bugs** — ring
vanishing on the first D-pad right after selecting Settings, arrows-vs-Tab
inconsistency in the settings category menu, B/Esc sometimes dropped until
an arrow re-established focus, and no way to leave a focused text field.
Oracle root-cause: `RootTabHost` cleared focus on every tab change;
focus-nowhere then degenerates (2D search anchors at the window corner, Tab
is the only mapped key, Enter/Esc never reach the root hook). Fixed and
redeployed the same day as the 6th patch, `nuvio-focus-hardening.patch`:
tab-focus retention; `isFocused` + multiline-aware tracker gating (Up/Down
traverse out of single-line fields); Esc exits a focused field before route
back; per-key arrow gating with `nuvio-nav:` journal diagnostics; focus
rings on the shared NuvioBackButton/PrimaryButton/ActionLabel/
IconActionButton/header primitives. Retest pending; details in STATE s13.

**Couch retest 2 + round-3 fix (2026-10-06).** Retention, caret and B-exit
improved; remaining: the rail→categories focus graph, focus loss on subpage
entry, and two ringless controls (details + journal evidence in STATE s13
addendum 2). Round-3 fix deployed the same day (7th patch): recovery anchor +
presence monitor (`Focusability.Never` target; 0×0 anchor with a far
`focusRect`), an explicit `SettingsFocusLinks` directional graph (rail
Settings right→category, category right→content, edge cancels,
select-moves-focus, subpage entry focuses the header back button), and rings
on Manage Profiles/avatar picks. Retest pending.

**Couch retest 3 + investigation (2026-10-06).** Rings/field/back work; the
remaining failures were root-caused from source + the in-app `nuvio-nav:`
journal: the 68 dp desktop sidebar and the top-anchored settings category
column (x≈68–308) share one geometric vertical band, so rail↔category
edges leak wherever no explicit edge exists (Up from Home / Down from the
avatar reach the categories); and the round-3 recovery anchor is selectable
by ordinary navigation (`moved=true … anchor=true`) because its far
`focusRect` guard is ineffective — once the anchor holds focus, the
dispatcher's clear-and-retry searches from the window corner and lands on
the profile avatar ("3× Right → avatar"). The subpage back-button focus
request does not stick (target not attached at request time), so focus
parks on the anchor (B works, ring absent). Round-4 plan (anchor
isolation/visible parking, rail corridor edges, right-from-any-rail-item →
categories, concrete category→content target, subpage back-button focus) is
documented in docs/plans/nuvio-focus-graph-round4.md.

**Round 4 landed, built and deployed 2026-10-06 (session 13 continued; 8th
patch `nuvio-focus-round4.patch`; package
`zdwmf93bl6skyfm6kvrwbddhfmn427s4`, generation
`jgc7821d5gabv35mcx3ih60nki1y8awv`; couch retest 4 is the gate).** The
anchor guard was implemented as a NaN `focusRect` instead of the planned far
non-empty rect — source-verified against CMP 1.12.0 `isCandidate()`
(`TwoDimensionalFocusSearch`): a far/empty rect still participates as a
candidate and wins when it is the only readable one (the round-3 failure
mode), while NaN comparisons fail every direction; the node stays placed and
programmatically focusable, so Esc/B parking is unchanged and the dispatcher's
clear-and-retry recovers to a visible node. Rail corridor: per-item
`FocusRequester`s + explicit Up/Down edges on avatar/Home/Search/Library/
Settings (avatar Down defers to geometry while the profile switcher stack is
open; Settings Down cancels). Right from ANY rail item on the Settings tab
redirects to the active category; category Right/select targets a concrete
attached node (first ringed Root row via a `firstRowModifier`, or the subpage
back button) with frame-safe retries; subpage back-button focus retries
across frames until `requestFocus()` returns true. `nuvio-nav:` diagnostics
kept for retest 4.

**Retest 4 + round 4b (2026-10-06).** Journal: the NaN anchor works (zero
`anchor=true` landings in the session) — the remaining invisible landings
were a geometric avatar→top-bar jump and an anchor-vs-transition race, not
anchor selection. Fixes (9th patch `nuvio-focus-round4b.patch`): B/Esc closes
the profile switcher and its last row exits Down to Home; avatar Right on
Settings → active category; per-tab `railEntries` so Left returns to the
owning icon (content → active category, categories → Settings icon, tab
content → its rail item); the subpage back-button request is retried until it
survives a frame while the anchor effect waits a frame and reads live state;
settings rows inset 4 dp to keep the outset ring un-clipped; `nuvio-focus:`
landing diagnostics for retest 5. Package `z42kq6vi…`, generation
`r3y5j1ps…`.

**Dev rebase + round 4c (2026-10-06/07).** Upstream #791 merged into Dev
(`d4bdf258`); our back-handling patch was dropped and the remaining eight
patches were 3-way re-rebased onto Dev (one conflict: SettingsScreen's
content Box — upstream `align/widthIn` kept alongside the focus tail), chain
fidelity re-verified with GNU patch, deps covered (only Gradle delta since
0.1.26 is `jna` 5.19.1, already in deps.json). Retest 5 then drove round 4c:
subpage entry focuses the first content entry after requesting the back
button; rail items become non-candidates for 2D search while the Settings tab
is open (NaN `focusRect`) so a content pane's bottom Down cannot leak onto
the rail — `onExit` was rejected as the guard because it is consulted only by
`clearFocus`, not directional moves; ring polish (switch rows, group
first/last breathing room, divider-aligned compensation) with a documented
corner-trim residual. Package `wxv56r84…`, generation `7pix028k…`.

**Retest 6 + round 4d (2026-10-07).** The non-Settings D-pad blocker was
ringless content surfaces, not the anchor: on desktop-with-sidebar there is no
top bar, so Right from the rail lands directly in tab content, and
`HomeHeroSection`/`LibraryScreen` had no focus rings at all. Round 4d adds
rings there (plus landing tags), suppresses the Material3 `Switch`'s own focus
target so switch rows ring as a single stop, moves the subpage-entry handoff to
the first content entry in the same frame, and cancels Up from the Root first
content entry. Package `ygicp2m4…`, generation `pgssrv1l…`.

The original root-cause record follows.

1. **Packaging (recorded deviation from §4).** `packages/nuvio-src/` vendors
   the nixpkgs PR #543092 Gradle expression pinned to 0.1.27-alpha plus two
   patches (below); deps.json is 0.1.26's — valid because 0.1.26→0.1.27
   changed no Gradle file. Gotchas: the fetchFromGitHub hash is of the
   UNPACKED tree (a plain tarball hash fails the FOD); `pkgs.nuvio`
   (AppImage) stays as fallback packaging. If upstream lands navigation,
   revert to the wrap (less maintenance).
2. **Double-focusable bug (fixed, hardware-verified).** PR #180's
   `nuvioFocusBorder` adds `Modifier.focusable()` to elements that are
   already clickable/selectable ⇒ TWO focus targets per item: arrows step
   through both (every other press looks dead), focus sometimes lands on
   the bare node where Enter has no click semantics, and the indication
   alternates ring/tint. Fix: drop the redundant focusable
   (`nuvio-arrow-nav-fixes.patch`). After the fix: one step per press, A
   activates reliably.
3. **Rail "missing ring" root cause: coverage, not rendering.** PR #180
   applied rings to the MOBILE bottom pill bar (NavigationBar.kt,
   `!isDesktop`-guarded); the desktop surfaces — `DesktopSidebarItem`
   (AppShellComponents.kt), the profile trigger (`indication = null`), and
   the top-bar JellyTabs — have no ring at all. The subtle tint the user
   sees is Surface's own click indication.
4. **No in-app back: stub actual.** `PlatformBackHandler` desktop actual is
   `= Unit` while Android wires a dispatcher; ~12 call sites (settings page
   stack, player overlays ×3, collection editor ×4, AppGate ×2,
   MainTabsDestination, PosterZoomActionOverlay) are wired and waiting.
   Upstream PR #791 (open, cleanly mergeable) implements exactly the needed
   common dispatcher + desktop actual + Esc dispatch — adopt, don't
   reinvent. Esc-at-root semantics (exit to profile selection) is a product
   question flagged for couch testing.
5. **Compose facts pinned (source-verified).** `selectable`/`clickable` are
   keyboard-focusable; focus gain auto-`bringIntoView` (LazyRow scrolls to
   the focused item — no app work); Enter/Space activate on KeyUp (the
   press-time A tap is correct); `onFocusChanged` observes the first
   focusTarget AFTER it — rings must use `hasFocus`, not `isFocused`;
   `border()` strokes inside bounds (a same-shape clip does not cut it).
   CMP has no built-in arrow navigation or BackHandler;
   `androidx.navigationevent` is the future official path. The AWT
   dispatcher eats arrows in Compose text fields (caret) — the plan adds a
   small text-input-focused flag consulted by the dispatcher.
6. **Upstream state (2026-10-05).** PR #180 open/dirty, idle since the Sep
   12 review (which had already flagged wrapper-before-card focus and
   missing focus indication — our fixes answer both). Nothing
   keyboard-nav-related landed on Dev after 0.1.27. Planned contributions:
   endorse/port #791 with hardware validation; PR #180 review feedback
   (double-focusable diff, outset `hasFocus` ring guidance, desktop-surface
   coverage); an issue on AWT-vs-Compose text-focus introspection.

**Verified:** the source build compiles and deploys from the pinned
expression; launch/focus-helper/Guide/close paths unchanged; arrows
single-step; A reliable (user couch pass). **Residuals:** rail/top-bar/
trigger rings, in-app back, text-field arrows, player transport (untested —
no media), popup focus behavior, focusRestorer — all scoped in the plan.

**Retrospective + consolidation decision (2026-10-08; oracle ora-1 + librarian
lib-1).** Stepping back before retest 7 (round 4d), the record across seven
couch retests was reviewed. No code changed. Conclusions:

- **The failures were four recurring classes, not ten one-offs.** Invisible
  landings, edge leaks, composition races, and ringless/dual-target surfaces —
  all violations of one missing invariant ("every focus stop declares its ring
  and its edges; focus is never nowhere, invisible or ringless"). The absence
  of that model is why each fix was a per-call-site patch and why classes
  recurred: the double-`.focusable()` bug was found twice (PR #180, then the
  Material3 `Switch` in round 4d).
- **The architecture is validated; the process is the cost driver.** OS-level
  key synthesis is right — no Nuvio client (Desktop/TV/Web/mobile) has
  app-side controller code. But one new numbered patch per couch finding, with
  no regression net and couch-only verification (remote injection stays
  blocked by AWT Globally-Active focus reversion, finding 20), multiplies
  rebase cost and human gates. Decision: **keep the fork as a bridge, stop
  finding-chasing patches, change the iteration model.**
- **Assertivity.** Add a headless focus-contract harness *before* the next
  behavior change: a table-driven focus-graph spec encoding the round-4
  acceptance list; a pure `resolveArrowKey` unit test; a ring semantics marker;
  a focus-tree invariant (exactly one focused node, ringed, never the anchor);
  and a journal-contract script so a couch run self-verdicts. The in-tree
  `desktopTest` (`compose.desktop.uiTestJUnit4`, image capture) and
  `androidHostTest` (Robolectric) infra already exist.
- **Abstractions.** `focusCorridor` (declarative sequential edges); a
  `FocusableSurface` ring-by-construction wrapper; one flag-gated diagnostics
  facade; scoped `FocusLinks` + an injectable `FocusNavigator`; and visible
  parking to replace the NaN anchor. Replace undocumented workarounds with
  supported CMP 1.12 APIs: `focusProperties { canFocus = false }` / structural
  de-focus for the NaN `focusRect`; `focusGroup` + `onEnter`/`onExit` for
  hand-wired edges; `focusRestorer` / `saveFocusedChild` for return-to-owner.
  YAGNI: full region model, TV DSL, D-pad throttles before the player round,
  `navigationevent` migration now.
- **Upstream (lib-1, GitHub API 2026-10-07/08).** PR #180 remains **OPEN and
  unmerged**, stalled on maintainer re-review since 2026-09-05 (the author's
  2026-10-06 pushes already answer its three review points; five tester UX bugs
  remain). #791 merged (adopted). **New PR #853** (macOS Esc/Settings,
  2026-10-07) touches `MainAppContent.kt`/`SettingsScreen.kt`/`Main.kt` —
  reconcile at the next rebase. NuvioTV/NuvioWeb already own explicit focus
  stacks (`FocusRestoreUtils.requestFocusAfterFrames`, `DpadThrottleModifier`,
  `FocusRingStyle`, `DrawerFocusRequesters`; Web `focusEngine.js`), so the
  Desktop work is best pitched as **TV/Web parity**. `androidx.navigationevent`
  1.2.0 stable (2026-10-07) is the future blessed back API. Upstream the
  generalizable deltas (the double-`.focusable()` fix + `hasFocus`/outset-ring
  guidance + desktop-surface list; the text-input tracker — still #180's
  acknowledged blocker; seed-focus once visible parking lands); do **not**
  upstream the NaN anchor.

Plan: [docs/plans/nuvio-focus-consolidation.md](../plans/nuvio-focus-consolidation.md).
It supersedes the "Residuals" list above where it names rail/top-bar/trigger
rings and in-app back (resolved by rounds 2–4d); the remaining residuals
(text-field caret edge cases, player transport, popup focus, deep-flow ringless
surfaces, focusRestorer) are folded into that plan. Fork exit criteria: retest
7 + a regression retest pass; headless suite green over the acceptance list;
upstream PRs filed; ringless backlog covered or waived.

**Retest-7 root cause + fixes (2026-10-08, later; 12th patch
`nuvio-focus-retest7-fixes.patch`).** The P0 couch pass surfaced the retest-6
class still unfixed. Journal evidence (PID 336515): `anchor=true` = 0 and
`home-hero`/`library-source-chip` landings = 0 — so the recovery anchor was not
involved and focus never reached the round-4d ringed content surfaces. Root
cause (ora-2): `RootTabHost`'s `RootTabPane` forced `canFocus = active` on the
pane Box. Home/Search/Library content roots are `LazyColumn`s whose scroll
anchor is a `Focusability.Never` focus target (Foundation attaches it for
`bringIntoView`/restore); forcing it `true` promoted that invisible anchor to a
legal 2D candidate covering the whole content rect, so Right from any
non-Settings rail item landed on it (no ring, no click, all further arrows
`moved=false`; one Left returned via the explicit rail edge). Enter/A dropped
focus for the same reason. Fix: `if (!active) canFocus = false` — only opt
inactive panes out, leaving the anchor non-focusable. Also fixed: ring coverage
for `HomescreenCatalogRow` (row treatment mirroring `SettingsSwitchRow` +
suppressed inner `Switch`/lock/drag stops), `PosterToggleRow`'s `Switch` and
`PosterStyleOptionRow`'s `FilterChip`s; the custom-poster URL field reclassified
single-line so Up/Down exits it; and Settings Root category→content now targets
the always-composed content-group Box so Right works when scrolled. Built
`0vnvpv805…`, deployed generation `hji0k8j…`; couch re-test pending. Note:
Left-from-content→rail on Home/Search/Library now relies on geometry (the
explicit pane `left` edge lived on the now-non-focusable anchor).

**Retest-7 follow-up + autonomous focus-test harness (2026-10-08, later; 13th
patch `nuvio-focus-item-fixes.patch`).** A second couch pass left item 4 failing:
Settings → General → Layout → scroll → B → reopen Layout lost the ring, and
Right landed on the profile/avatar. Root cause (ora-2): subpage re-entry
restores the saved `LazyColumn` scroll offset, so the header back button is
uncomposed and `backRequester` is unattached; the page-entry effect never placed
focus, and a later Right searched from the corner. Fix: `contentEntry` targets
the always-composed content group on Root **and** subpages, and the page-entry
effect falls back to it when `backRequester` fails (auto-entry still prefers the
back button + Down, so no flash). Item 1's remaining ringless dead-ends were the
hero page-indicator dots (now `canFocus = false`, pointer-click kept),
`FullscreenActionButton`, and the Library/Search header `IconButton`s (ringed);
the Search field's capture-on-focus-loss arrow trap was removed.

**Testability unlock:** the headless Compose focus suite now runs on brook
outside the nix sandbox — Xvfb + software Skiko, with `LD_LIBRARY_PATH` from the
built app's Skiko RPATH (SkikoComposeUiTest needs both a display and
`libGL`/`libfontconfig`). The first real-component `FocusContractTest` (mounts
`DesktopHoverSidebar` + `RootTabHost` + a LazyColumn, moves Right from the rail,
asserts the focused node is ringed **and** actionable — never the non-actionable
scroll anchor) passes, alongside `ArrowKeyNavTest` 4/4. Focus-graph, ring and
text-gating classes are now verifiable without a couch pass. Built `ijiy2xhi…`,
deployed generation `74z3pkhf…`.

**Ring coverage + Settings UX (2026-10-08, later; 14th patch
`nuvio-focus-ring-coverage.patch`).** The next couch pass confirmed item 4 fixed
and the non-Settings focus blocker gone, but many couch-reachable surfaces drew
no ring (Continue Watching cards, home folder/collection cards, Search Discover
grid, theme chips/swatches, plus shared primitives). An audit mapped the class;
the decision was targeted ring additions now and the `FocusableSurface` wrapper
in P2. The patch adds rings to ~26 surfaces (all `nuvioFocusBorder` prepended
before `.clip` and the focus target: Continue Watching ×3, `CollectionFolderCard`,
`PosterGridTile`, theme chips/swatches/slots, `NuvioDropdownChip`,
`NuvioViewAllPill`, catalog tiles, recent-search rows, Classic nav items,
Dialog/Menu/BottomSheet rows, and several settings rows). Two Settings behaviors:
non-Root subpages now `requestScrollToItem(0)` on entry (close/reopen starts at
top; Root keeps its position), and `focusRestorer()` is applied to BOTH the
content-group Box and its `LazyColumn` so Right from a category restores the
last-focused row (a restorer is required at each nesting level — the group's only
direct child is the scroll anchor). All 7 headless tests green; built
`8iriw9ka…`, deployed generation `jvlg25jr…`.

**Behavior fixes (2026-10-08, later; 15th patch
`nuvio-focus-behavior-fixes.patch`).** Four couch follow-ups root-caused and
fixed: (A) the desktop shelf title was itself a full-width focus stop, so Down
from a poster landed on the next shelf's title — a double press to cross Home
folder rows; the title is now pointer-clickable but `canFocus = false`. (B) the
details screen had no entry focus effect, so after opening a title focus parked
on the header back button; a `FocusRequester` on the primary play action
(threaded through `DetailActionButtons`/`DetailActions`/`DesktopDetailHero`) is
requested across up to 8 frames on open, plus `detail-play`/`detail-back` debug
tags. (C) the submenu re-entry flicker was the post-paint `requestScrollToItem(0)`
reset; replaced with a Root-only saver (non-Root pages use a plain
`remember(LazyListState)`), so subpages open at the top with no restore and Root
keeps its position. (D) Left from a submenu row reached the back button because
the content group's `left = categoryEntry` does not cross the LazyColumn's scroll
anchor; an explicit `left = categoryEntry` was added to `SettingsNavigationRow`
and `SettingsSwitchRow` (row-level, avoiding a collision with `focusRestorer`'s
`onExit`). All 7 headless tests green; built `cqa30kaq…`, deployed generation
`azs7cmg…`.

**E/F fixes + session closeout (2026-10-09; 16th patch
`nuvio-focus-ef-fixes.patch`).** The couch pass on the 15th build passed A–D
with two follow-ups. (E) Left from the Settings subpage back button landed on
the rail avatar; an explicit `left = categoryEntry` was added on the
`TabletPageHeader` back button. (F) the Home folder category arrows open
`CatalogScreen` ("View all"), whose entry focus parked on the back button; a
`focusGroup()` + `FocusRequester` on the grid-wrapping Box is requested across
frames when items load, so focus enters the first tile. Chain-fidelity verified;
built `k9c0zhw1…`, deployed generation `zb2mbjgv…`; **E/F couch-verified PASS**,
regression PASS. Session closeout and a fresh-session resume point are in
[docs/plans/nuvio-focus-consolidation.md](../plans/nuvio-focus-consolidation.md).
