# Nuvio — session state (extracted from cwrneirOS STATE.md)

*Provenance: extracted verbatim from `STATE.md` in cwrneirOS. Original section and addendum numbering is preserved for traceability. OS-side session/appliance state (compositor, units, controller, gate status) lives in cwrneirOS; only the Nuvio-relevant material is reproduced here.*

[STATE lines 3–12 — header: focus-nav work, deployed patch chain, headless suite, focus-contract.sh:]

Last updated: 2026-10-09 (session 13 continued: couch-driven focus iterations +
autonomous headless focus-contract harness). The focus-navigation work is tracked
in
[docs/plans/nuvio-focus-consolidation.md](plans/nuvio-focus-consolidation.md)
(implementation log + fresh-session resume point). **Deployed through the 16th
patch `nuvio-focus-ef-fixes.patch`** (package `k9c0zhw1…`, generation
`zb2mbjgv…`, 2026-10-09). The 15th patch
(`nuvio-focus-behavior-fixes.patch`, `cqa30kaq…`) was couch-verified A/B/C/D;
**E/F and the regression pass are couch-verified PASS.** **Headless Compose focus
tests run on brook** (Xvfb + software Skiko), plus `scripts/focus-contract.sh`;

[STATE lines 18–27 — prior-session pointer recap:]

Previous: 2026-10-07 (session 13, continued: controller-navigation focus
work — rounds 2–4d deployed on the upstream **Dev** base. Retest 6 surfaced a
non-Settings D-pad blocker (ringless hero/library content surfaces) plus three
polish items; round 4d adds the missing rings, fixes switch-row focus, smooths
subpage entry and cancels the Root Up leak (10th patch
`nuvio-focus-round4d.patch`; package `ygicp2m4…`, generation `pgssrv1l…`) —
see STATE addendum 7. Retest 7 is the gate. Session 12: source build + nav
fixes. Session 11: AppImage wrap + AWT focus fix. Session 10: planning
alignment — V1/V2/V3 scope and the development-flow agreement are canonical
in `ROADMAP.md` (cwrneirOS).)

[STATE lines 39–79 — Session-11 outcome: Nuvio wrap/focus verified, controller UX re-opened:]

## Session-11 outcome (2026-10-05): Nuvio — wrap/focus verified, controller UX RE-OPENED

**Status (late 2026-10-05): V1.2 re-opened.** The wrap, AWT focus fix,
Guide overlay and close path are verified, but in-app controller
interaction is limited to A + SELECT (upstream has no arrow navigation).
Not accepted; alternatives under evaluation — see ROADMAP V1.2.

Nuvio Desktop 0.1.27-alpha wrapped from the upstream AppImage
(`packages/nuvio.nix`; pinned tag + enforced release sha256) and taken
end-to-end on brook. Long debugging session — the punchline findings:

- **AWT "focused but deaf" bug (fixed).** Java AWT is ICCCM *Globally
  Active* (`WM_HINTS input=False` + `WM_TAKE_FOCUS`); wlroots sends that
  message once at first map and never `XSetInputFocus`es. When missed, X
  focus stays on the **toplevel**, which selects no KeyPress mask — all
  keys (gamepad and physical keyboard) are discarded while EWMH still
  reports focused. Fix: `packages/nuvio-focus.c`, started by the nuvio
  launcher, re-sends `WM_TAKE_FOCUS` until focus reaches the AWT
  `FocusProxy` child. Two hardening lessons: "focus != toplevel" is NOT
  success (transient `PointerRoot`, 0x1), and `XQueryTree(PointerRoot)`
  raises BadWindow that kills a default Xlib client — guard + non-fatal
  error handler required. Full evidence trail: ADR finding 20.
- **Gamepad bridge.** SELECT→Tab added to the evsieve `action-keys` output
  as an unconditional tap map (phase-gating fails after launch-via-A;
  focus routing makes it safe — Tab is unbound in the shell). A=Enter
  works; D-pad/B remain inert inside Nuvio — upstream has no arrow nav
  (PR #180 unmerged). SELECT leaves the App-mode pad passthrough.
- **Close path.** Shell-level B/Esc on the visible shell stops the unit
  (Nuvio's own Esc only closes its player). `systemctl stop` kills the
  wrapper cgroup before its exit-notify — the shell handler clears
  `activeApp` itself; clean self-exits still notify via `appExit`.
- **First run.** Account optional — "Continue Without Account" reachable
  with SELECT+A; account sign-in needs text entry (physical keyboard until
  the V1.3 OSK).
- **Acceptance (user-run on hardware).** Launch from the tile, fullscreen,
  no chrome; SELECT=TAB cycles focus, A activates, Guide overlays home, B
  closes back to home. Nuvio alpha status accepted; updater-enabled
  AppImage note in ADR finding 20.

Residual carried-open items unchanged: real-TV standby retest · popup half
of B1 · controller wake-path observation.

[STATE lines 81–115 — Session-12 outcome: source build + navigation round 1:]

## Session-12 outcome (2026-10-05, later): source build + navigation round 1 — plan agreed, implementation pending

User rejected the A+SELECT-only state and pointer emulation (wants TV-style
navigation); chose the patched source build. What happened:

- **Rebase + packaging.** PR #180 rebased onto 0.1.27-alpha (5 small hunks;
  one structural adaptation — FilterChip now lives in ProviderFilterRow.kt)
  → `packages/nuvio-src/` (vendored nixpkgs PR #543092 expression; deps.json
  reused from 0.1.26 — no Gradle file changed between releases; the src hash
  is a fetchzip/unpacked-tree hash — a plain tarball hash fails). Built on
  brook; `modules/apps.nix` launcher + systemPackages now use
  `pkgs.nuvio-src`; `pkgs.nuvio` (AppImage) kept as fallback packaging.
- **Couch pass 1:** janky stepping, ring/tint alternation, A unreliable.
  Root cause: PR #180's `nuvioFocusBorder` added `.focusable()` to already-
  clickable items → two focus targets per item. Fix shipped as
  `nuvio-arrow-nav-fixes.patch` (keep — it explains the whole class).
- **Couch pass 2 (current state):** arrows step one item per press; A
  activates reliably; left rail shows only a tint; B/Esc does nothing in
  menus/submenus; player untested (no media yet).
- **Root causes (source-verified, see ADR finding 21):** the left rail, top
  bar and profile trigger NEVER had rings (PR #180 covered the mobile bottom
  pill bar — the real surfaces are `DesktopSidebarItem` in
  AppShellComponents.kt and JellyTabs.kt); desktop back is a stub
  (`PlatformBackHandler` desktop actual is `= Unit`; ~12 wired call sites
  wait behind it; upstream PR #791 implements exactly the needed dispatcher,
  cleanly mergeable); the AWT dispatcher eats arrow keys in Compose text
  fields. Compose facts pinned: `selectable`/`clickable` are keyboard-
  focusable and auto-bringIntoView (lazy shelves scroll for free); Enter
  activates on key-up; rings must observe `hasFocus`, not `isFocused`.
- **Plan agreed (no coding yet):**
  [docs/plans/nuvio-controller-navigation.md](plans/nuvio-controller-navigation.md)
  — outset `hasFocus` ring + desktop-surface coverage, adopt PR #791 for
  back, text-input arrow tracker, probes, verification and the upstream-PR
  plan. The scratch tree is regenerable from the repo patches
  (instructions in the plan file). Next session starts from that file.

[STATE lines 117–182 — Session-13 outcome: controller-navigation round 2 implemented/built/deployed:]

## Session-13 outcome (2026-10-06): controller-navigation round 2 — implemented, built, deployed; couch pass pending

Implemented [docs/plans/nuvio-controller-navigation.md](plans/nuvio-controller-navigation.md)
work items 1–3 as three per-topic patches in `packages/nuvio-src/` (applied
after the two existing ones; per-patch fidelity and combined apply verified
on a fresh 0.1.27 extract):

- **`nuvio-focus-ring.patch`** (item 1) — `nuvioFocusBorder` rewritten:
  `hasFocus` observation + outset `drawBehind` stroke (inflate =
  borderWidth/2 + 1dp; requires `androidx.compose.ui.graphics.drawOutline` —
  the first build caught this import); applied to the ringless desktop
  surfaces (sidebar rail items, profile trigger, profile-switcher rows,
  top-bar jelly tabs) and every call site reordered before any outer
  `.clip` (all 23; only the mobile/iOS `NavigationBar.kt` sites left as-is —
  proven desktop-unreachable via the FloatingNavigationBar actuals).
- **`nuvio-back-handling-791.patch`** (item 2) — upstream PR #791 applied
  cleanly (zero fuzz/adaptation): common `PlatformBackDispatcher` (LIFO,
  last-enabled-wins), real desktop `PlatformBackHandler` actual, Esc
  `onKeyEvent` + mouse-Back fallback in MainAppContent. Esc ownership traced:
  the AWT dispatcher only consumes arrows and returns false for VK_ESCAPE —
  the MainAppContent hook is the single owner. Traced semantics: the settings
  entry route pops as a whole (not one page; shared with the pre-PR
  external-host behavior), in-tab settings pages pop one level, home root Esc
  is a desktop no-op (`MainTabsDestination` gated `!isDesktop`), collection
  sub-screens dispatch LIFO.

[editorial: this patch was dropped at the Dev rebase — PR #791 is merged in base d4bdf258; see docs/patch-history/]

- **`nuvio-text-input-tracker.patch`** (item 3) — commonMain
  `expect fun Modifier.nuvioTracksTextInput()` + desktop actual
  (`DesktopTextInputState` AtomicInteger counter; per-field edge detector
  with idempotent disposal) + android/ios no-ops; the AWT dispatcher skips
  arrow interception while a tracked field holds focus. 19 direct field
  sites plus all `NuvioInputField`/`SettingsSecretTextField` call sites.

Build/deploy: package builds on brook
(`/nix/store/nsqcwgfn17k1m9mj4k497lz0iknkhhiz-nuvio-desktop-0.1.27-alpha`);
`nixos-rebuild switch` done 2026-10-06 00:2x (generation `d4fsplaq…`), no
reboot; app launches and renders on the new build.

Remote input verification BLOCKED (documented behavior, not a regression
signal either way): `wtype` works at shell level (it launched the app from
the tile), but inside Nuvio every injection route fails because X focus
reverts from the AWT FocusProxy (0x800019) back to the toplevel (0x800004)
within <50 ms of each `nuvio-focus` success — the documented ICCCM
Globally-Active deafness. Tried: wtype, XTEST (`xdotool key`), XSendEvent to
proxy and toplevel, and a helper loop. No key reaches AWT while focus is on
the toplevel. Physical gamepad remains the ground truth → couch pass is the
gate.

Note (not a regression): stopping the unit from SSH (`systemctl --user stop`)
leaves the shell hidden (black screen) — the exit-notify path is bypassed;
`qs -p /etc/cwr/shell ipc call tvshell toggle` re-shows it. Shell-level
B/Esc close (visible shell) is unaffected.

Couch-pass protocol (plan §Verification plan; report per item):
1. Ring: rail Down/Up through all 4 items + profile trigger + top-bar tabs —
   one visible outset ring per step, no ring/tint alternation.
2. Back: Esc in settings subpages (entry route pops whole; in-tab page pops
   one level), details page, collection editor, poster zoom.
3. Esc isolation: Guide over app → B closes app (shell path), app state
   unchanged (no double-fire).
4. Text: settings search focused → Left/Right move caret, Esc backs out,
   unfocused arrows navigate again.
5. Shelf: hold Right past viewport → row follows focus (bringIntoView).
6. Probes: first arrow after tab switch, popup windows (profile switcher /
   PIN), LazyRow auto-scroll.
Deferred: player transport (no media at hand).

[STATE lines 183–650 — Session-13 addendum through addendum 13 (all 13 addenda):]

### Session-13 addendum (2026-10-06, couch round 1): focus-topology bugs found → hardened

Couch round 1 on the round-2 build: ring/back/close-isolation/caret all
verified, but three clusters failed — (a) selecting Settings then D-pad
right made the ring vanish (left then showed it on the settings menu), (b)
in the settings category column only SELECT/Tab moved focus and A worked
only after Tab had focused an entry, (c) B/Esc was sometimes dropped until
an arrow re-established focus, and (d) Esc did nothing inside the search
text field / no way to leave it.

**Root cause (oracle consult, 2026-10-06):** `RootTabHost` cleared focus on
EVERY tab change (`DisposableEffect(selectedTab, hostActive, profileId) { onDispose { clearFocus(force) } }`).
Focus-nowhere then degenerates: arrow search anchors at the window corner
instead of the rail item, Tab is the only mapped key, and Enter/Esc cannot
reach the root hook. Secondary: the tracker gated on `hasFocus` (a focused
clear/eye IconButton inside a field kept arrows disabled) and Esc had no
field-exit semantics.

**Fix (6th patch `nuvio-focus-hardening.patch`, 15 files):**
- F1 `RootTabHost`: clear focus only on profile change / host disposal —
  `RootTabPane`'s `focusProperties { canFocus = active }` already clears
  in-pane focus.
- F4 tracker: `isFocused` (not `hasFocus`) + `singleLine` parameter;
  Up/Down traverse out of single-line fields while multiline keeps all
  arrows (6 multiline sites opted in: Streams badge dialog, Poster URL
  pattern, Debrid template/text-list dialogs, Library list description,
  Collection JSON import); dispatcher does per-key gating.
- F5 Esc exits a focused text field first (`moveFocus(Previous)`, fallback
  `clearFocus`) before route back; popup-local Esc handling untouched.
- F6 rings on shared primitives: `NuvioBackButton`, `NuvioPrimaryButton`,
  `NuvioActionLabel`, `NuvioIconActionButton`, header back IconButton.
- Diagnostics: every arrow press logs `nuvio-nav: key=… moved=… text=… ml=…`
  to the unit journal (pull after the retest; remove at closeout).

**Deployed:** package `7zh7vnbiv0a6lmsj68435jwq5g6jhykk`, generation
`8clkrbjygs2h5vlga7607s0hy72gnz4y` (2026-10-06, switch, no reboot).
Retest pending. Deferred: oracle F2/F3 root focus anchor (only if
focus-nowhere persists), FullscreenAction button ring, slider/radio/
small-icon ring coverage, popup arrow theft (pre-existing).

### Session-13 addendum 2 (2026-10-06, couch retest 2): focus graph + recovery anchor

Couch retest 2 on the hardening build: rail ring retention ✓, category
Up/Down ✓, caret Left/Right ✓, B leaves the field ✓, rings almost
everywhere. Remaining issues: (a) rail Settings + D-pad right still made
the ring vanish — the focus graph connected the rail and categories
vertically, the category column edges leaked to the avatar/rail, and A/right
did not enter the content pane (only SELECT/Tab did); (b) B was dead after
entering a subpage until an arrow/Tab (focus cleared on navigation); (c)
Up/Down did not leave the single-line field when no candidate existed; (d)
missing rings: Manage Profiles, avatar picks.

Journal diagnostics (`nuvio-nav:` lines) confirmed moveFocus works —
`moved=false` only at edges/no-candidate; focus-nowhere at launch; field
Up/Down consumed with `moved=false`.

**Round-3 fix (7th patch `nuvio-focus-round3.patch`, 9 files):**
- Recovery anchor + presence monitor (`PlatformFocusPresence` +
  `Modifier.focusPresenceMonitor()` with a `Focusability.Never` target; the
  anchor is a 0×0 focusable with a far `focusRect`, so it is never a 2D
  candidate and only the last Tab stop). MainAppContent requests it whenever
  no main-scene focus exists; the AWT dispatcher clears focus and retries the
  direction once while the anchor is focused. Subpage entry additionally
  focuses the header back button (ring visible, B immediate).
- Explicit settings focus graph (`SettingsFocusLinks`): rail Settings right
  → active category; category right → content group; category up/down edge
  cancels (no avatar/rail leak); category select moves focus into the
  content via a `focusGroup` content target (`focusGroup` is in
  `androidx.compose.foundation` — the first build caught the wrong import).
- Rings on Manage Profiles (`ProfileSelectionScreen`) and avatar picks
  (`AvatarPicker`).

**Deployed:** package `haf4853gvc2wcglq6h9889f4gx7yip20`, generation
`gc4zqj45nlpxzb7dswdppjm0wxxh9cfj` (2026-10-06, switch, no reboot).
Retest pending. Open: mixed category-click-while-on-subpage edge may land
focus on the category rather than content (flagged, non-crashing);
`focus=`/`anchor=` journal fields added for the next pass; FullscreenAction
button, slider/radio/small-icon ring coverage still deferred.

### Session-13 addendum 3 (2026-10-06, couch retest 3 + investigation): round-4 plan documented

Couch retest 3: rings on Manage Profiles/avatar picks ✓, field Up/Down ✓,
B works on first press after subpage entry ✓, rail ring retention ✓.
Remaining: (a) Up from Home / Down from avatar lead into the settings
categories (rail↔categories geometric band; the rail side has no explicit
edges — only the category-side ends were cancelled in round 3); (b) Right
from non-Settings rail items and from categories lands invisibly — the
journal shows focus ending on the recovery anchor (`moved=true …
anchor=true`; the far `focusRect` guard is ineffective); (c) two more
rights then park on the profile avatar (after the anchor, the dispatcher's
clear-and-retry searches from the window corner and picks the top-left
focusable); (d) subpage entry shows no ring (the back-button focus request
does not stick — target not attached at request time — so focus parks on
the anchor; B works).

Geometry that explains it: sidebar 68 dp overlay at the left edge; settings
category column 240 dp, top-anchored at x≈68–308 (y≈150–400); rail nav
centered x≈0–68 (y≈450–670); profile avatar x≈0–68 (y≈55) — one continuous
vertical band under geometric arrow navigation.

User decisions (2026-10-06): Right from ANY rail nav item on the Settings
tab enters the categories column; the round-4 plan is documented and
implementation proceeds in a fresh session. Plan:
[docs/plans/nuvio-focus-graph-round4.md](plans/nuvio-focus-graph-round4.md)
(anchor isolation/visible parking, rail corridor edges, category→content
target, subpage back-button focus, keep diagnostics; 8th patch
`nuvio-focus-round4.patch`).

### Session-13 addendum 4 (2026-10-06, round-4 focus graph): implemented, built, deployed

Implemented [docs/plans/nuvio-focus-graph-round4.md](plans/nuvio-focus-graph-round4.md)
as the 8th patch `nuvio-focus-round4.patch` (5 files in `packages/nuvio-src/`;
`git apply --check` against the round-3 state + chain fidelity on a fresh
0.1.27 extract, patches 1–8):

- **Anchor isolation (item 1):** NaN `focusRect` instead of the plan's far
  non-empty rect — source-verified against CMP 1.12.0: a far/empty rect still
  passes `isCandidate()` and wins when it is the only readable candidate at
  its level (the retest-3 `anchor=true` mechanism); NaN fails every
  directional comparison, so no move can land on the anchor. It stays placed
  + programmatically focusable, so the Esc/B parking and the dispatcher
  clear-and-retry are unchanged (first arrow after parking lands on a visible
  node).
- **Rail corridor (item 2):** per-item requesters; avatar `up=Cancel`,
  `down=Home` (default geometry while the profile stack is open);
  Home/Search/Library/Settings chained `up`/`down`; Settings `down=Cancel`.
- **Right from any rail item on Settings (item 3):** the `categoryEntry`
  right-override now sits on all four items (gated on selectedTab).
- **Category → concrete content (item 4):** `contentEntry` = subpage
  `backRequester` or the first ringed Root row (`firstRowModifier` threaded
  through `settingsRootContent`/`SettingsNavigationRow`); retries across
  frames on the click path.
- **Subpage back-button focus (item 5):** page effect retries
  `backRequester.requestFocus()` up to 8 frames until it returns true.
- **Diagnostics (item 6):** `nuvio-nav:` `focus=`/`anchor=`/`moved=` kept.
- **Packaging (item 7):** built on brook (one compile fix en route — missing
  `focusRequester` modifier import in `AppShellComponents.kt`); switched
  without reboot.

**Deployed:** package `zdwmf93bl6skyfm6kvrwbddhfmn427s4`, generation
`jgc7821d5gabv35mcx3ih60nki1y8awv` (2026-10-06). `/etc/cwr/apps/nuvio.sh`
verified to point at the new store path. Couch retest 4 pending (acceptance
in the plan). Residual: with a deep-scrolled Root, the category Right
redirect degrades to a no-op (focus retained, never invisible);
search-visible Root still targets the first row below the field.

### Session-13 addendum 5 (2026-10-06, round-4b): couch retest 4 results + fixes deployed

Retest 4 on the round-4 build: rail corridor ✓, category→content + Left
return ✓, text fields ✓, labels ✓. Remaining findings and the round-4b fixes
(9th patch `nuvio-focus-round4b.patch`, 9 files):

- **The anchor was never the culprit.** The session journal has **zero
  `anchor=true`** lines — the NaN anchor is never selected by a move. The
  invisible landings are (i) a geometric avatar→top-bar jump on Right and
  (ii) a race on subpage entry.
- **Profile switcher (findings a/b):** B/Esc now closes it
  (`PlatformBackHandler`, disabled while the PIN sub-view shows — the PIN
  keeps its own Esc); Down on the last row exits to Home via
  `lastRowDownRequester` (previously leaked into the categories).
- **Avatar Right on Settings → active category** (explicit edge; the vanish
  was the top-bar jump).
- **Return-to-owner Left edges:** `SettingsFocusLinks.railEntries` registry;
  categories Left → Settings rail icon; content Left → active category
  (content-group `left`, inherited by rows); `RootTabHost` panes get `left` →
  the owning tab icon (Home/Search/Library).
- **Subpage ring (finding e):** the back-button request is retried until it
  survives a settle frame (`hasFocus && !anchorActive`); the recovery-anchor
  effect now waits a frame and reads live state, so it can no longer steal
  the back-button focus.
- **Row ring (finding d):** settings rows inset 4 dp before
  `nuvioFocusBorder` — the ring draws up to 4 dp outside the node and the
  `SettingsGroup` Surface clip was shaving the left/right strokes ("two
  lines").
- **Diagnostics:** `nuvio-focus:` landing lines added (avatar, rail items,
  categories, content group, back button, stack rows, top-bar tabs) — pull
  from the unit journal after retest 5; remove at closeout.

**Deployed:** package `z42kq6vipkcvynq6fnfq1di2bnc97akq`, generation
`r3y5j1psacrbhszsblpv8kfc45mhvbqn` (2026-10-06, no reboot). Retest 5 pending.
Open: Down at the bottom of a short content pane still reaches the rail
(needs a per-page end guard or a rail-candidacy change — user call);
`SettingsSwitchRow` shares the row-ring clipping class; the 4 dp row inset
shifts nav-row text 4 dp relative to the group dividers (minor, visual).

### Session-13 addendum 6 (2026-10-06/07, Dev rebase + round 4c): retest-5 fixes deployed

**Dev rebase** (chosen after the pause-and-analyze). Upstream PR #791 merged
into `Dev` (2026-10-06 19:20 UTC, `d4bdf258`); PR #180 is still open (updated
2026-10-06) and its head now overlaps our ring/double-focusable fixes but has
no focus graph. Rebase done as a 3-way cherry-pick of our commits onto the
Dev tree:
- dropped `nuvio-back-handling-791.patch` (now upstream);
- re-rebased `nuvio-arrow-nav-0.1.27` (still needed; #180 unmerged) +
  `arrow-nav-fixes`, `focus-ring`, `text-input-tracker`, `focus-hardening`,
  `focus-round3`, `focus-round4`, `focus-round4b`;
- only one conflict (SettingsScreen's content Box — upstream
  `align/widthIn` kept alongside the focus tail);
- `default.nix` pinned to Dev `rev d4bdf258` (unpacked-tree hash);
  `deps.json` verified sufficient (only Gradle delta since 0.1.26 is `jna`
  5.19.1, already present);
- chain fidelity re-verified on a fresh Dev extract with GNU `patch -p1`.

**Retest 5** on the round-4b build: profile-stack close/exit ✓, avatar Right
→ category ✓, Left return-to-owner ✓, subpage ring + B first press ✓ (but
entry focus should be the first content entry, not the back button),
regressions ✓; row rings improved, still improvable.

**Round 4c (9th patch).**
- Subpage entry requests the back button (focus target + one-Up target) then
  `moveFocus(Down)` → first content entry.
- Down-leak closed centrally: while the Settings tab is open, the four rail
  items set a NaN `focusRect` (non-candidates for 2D search) — every
  Settings-tab rail edge is explicit (corridor, avatar Home, category Left →
  rail, Right → category), so nothing can land on the rail from a content
  pane's bottom. `onExit` was investigated and rejected (source: consulted
  only by `clearFocus`, not directional moves).
- Ring polish: `SettingsSwitchRow` inset like nav rows (content padding
  compensated); `SettingsGroup` 4 dp vertical breathing room for first/last
  row strokes; nav-row content padding reduced by the inset so
  `SettingsGroupDivider` alignment is unchanged. Residual: outer ring corners
  on first/last rows still meet the card's rounded corners diagonally.

**Deployed:** package `wxv56r842c1zhqra5ixwlvvga775y52y`, generation
`7pix028k56hxl41garhsk1sp1iwbdp9k` (2026-10-06, no reboot). Retest 6 pending.
Next (from the strategy analysis): headless focus-contract tests +
Xvfb/journal harness (repo already has `desktopTest`/`androidHostTest`
Compose infra), then upstream the unique deltas (text tracker, focus graph,
NaN recovery) against the active #180. Long tail: newly populated catalog
pages (Home shelves, details, search results, library) on retest 6.

### Session-13 addendum 7 (2026-10-07, round 4d): retest-6 fixes deployed

Retest 6 on the Dev-based round-4c build: subpage entry/B, Left returns,
regressions, and the Settings bottom-Down guard passed; remaining:
1. Subpage entry works but the back-button→first-entry handoff is visible.
2. Up on the topmost entry of back-button-less pages (Settings Root) leaks to
   the categories.
3. Switch rows show no ring.
4. **Blocker:** on any non-Settings tab, Right from a rail item makes the ring
   vanish and the page becomes non-navigable; one Left returns to the rail.

Root causes (journal + ringless-focusable audit):
- The `nuvio-focus:` tags showed only rail/category/back/content landings;
  `topbar-tab` never fired because on desktop-with-sidebar there is **no top
  bar** (`useDesktopTopBar`/`useFloatingTopBar` both false). Right from the
  rail lands directly in tab content, and the landing surfaces
  (`HomeHeroSection.kt` — 5 clickables; `LibraryScreen.kt` — 3) had **no ring
  at all**. (`nuvio-focus: X` lines in the journal are an unrelated Oct-05 log
  format, not focus landings.)
- Switch rows: the Material3 `Switch` applies its own `toggleable`, creating a
  second focus target; D-pad focus landed there and the row's ring (observing
  the row `clickable`) went missing.

Round 4d (10th patch, 4 files):
- Rings on the hero + library content surfaces (before their clips/clickables),
  with `home-hero`/`library-source-chip` landing tags for the next journal.
- `SettingsSwitchRow`: the inner `Switch` gets `focusProperties { canFocus =
  false }` — the row is a single focus stop, A still toggles, disabled rows
  stay non-focusable.
- Subpage entry moves to the first content entry in the *same frame* (no
  back-button ring flash); the back button stays one Up away.
- Settings Root first content entry gets `up = FocusRequester.Cancel` (no
  header target there) so Up no longer leaks to the categories.

**Deployed:** package `ygicp2m4frgn51cqf74rqjrwyr651729`, generation
`pgssrv1l78bjs7xnqylsbc2mb580f473` (2026-10-07, no reboot). Retest 7 pending.
Known ringless surfaces not yet covered (deeper flows): HomePosterHoverPreview
(×2), StreamsScreen filter row, CollectionEditorScreen (×6), AuthScreen (×3),
DeviceLinkAuthSection.

### Session-13 addendum 8 (2026-10-08, retrospective): path confirmed, process changed

Stepped back before retest 7 for a full retrospective (oracle **ora-1** +
librarian **lib-1**, both 2026-10-08; raw findings folded into ADR finding 21
§Retrospective). No code changed. Outcome:

**Lessons.** The seven rounds produced a source-verified CMP focus map
(`hasFocus` vs `isFocused`; `onFocusChanged` chain order; `clickable`/
`selectable` are already focusable; geometric 2D search; `focusRect`/
`isCandidate` incl. the NaN behavior; `focusGroup` redirect limits;
`requestFocus()` returns Boolean and races composition; lazy `bringIntoView`;
popups are separate windows; Enter activates on key-up). The bugs were **four
recurring classes** — invisible landings, edge leaks, composition races,
ringless/dual-target surfaces — and all are violations of one missing
invariant: *every focus stop should declare its ring and edges, and focus is
never nowhere, invisible or ringless*. Because no such model exists, each fix
was per call-site, so classes recurred (double-`.focusable()` was found twice:
PR #180 and Material3 `Switch`).

**Path.** Keep the fork as a bridge, but change the strategy: proven
architecture (OS-level key synthesis; no app-side controller code in the Nuvio
family), yet the cost driver is process — one new patch per couch finding, no
regression net, couch-only verification. Retest 7 stays the behavioral gate,
but new finding-chasing rounds stop.

**Assertivity.** Build a headless focus-contract harness **before** the next
behavior change: table-driven focus-graph spec (encodes the round-4 acceptance
list), pure `resolveArrowKey` unit test, ring semantics marker, focus-tree
invariant (exactly one focused + ringed + not the anchor), and a
`scripts/focus-contract.sh` journal contract — so a couch run is
self-verdicting. Headless covers edges/invisible-landing/ring coverage/
dispatcher gating; couch keeps feel/latency/physical mapping.

**Abstractions.** Adopt `focusCorridor` (declarative sequential edges), a
`FocusableSurface` ring-by-construction wrapper, a single diagnostics facade
(flag-gated, one closeout switch), a scoped `FocusLinks` + injectable
`FocusNavigator`, and **visible parking** to replace the NaN recovery anchor.
Replace workarounds with supported CMP 1.12 APIs (`focusProperties { canFocus =
false }` / structural de-focus instead of NaN `focusRect`; `focusGroup` +
`onEnter`/`onExit` instead of hand-wired edges; `focusRestorer`/
`saveFocusedChild` for return-to-owner). Reject YAGNI: full region model, TV
DSL, AWT→Compose-only, throttles before the player round, `navigationevent`
migration now.

**New upstream intel (lib-1).** PR #180 is **still OPEN, not merged** (stalled
on maintainer re-review since 2026-09-05; author already answered the three
review points; 5 UX bugs from a tester remain). #791 merged (adopted).
**PR #853** (macOS Esc/Settings, 2026-10-07) touches the same files — reconcile
at the next rebase. NuvioTV/NuvioWeb already own explicit focus stacks
(`FocusRestoreUtils.requestFocusAfterFrames`, `DpadThrottleModifier`,
`FocusRingStyle`, `DrawerFocusRequesters`; Web `focusEngine.js`) → the pitch is
"bring Desktop to TV/Web parity". `androidx.navigationevent` 1.2.0 is stable
(2026-10-07) and is the future blessed back API.

**Decision / next.** New plan
[docs/plans/nuvio-focus-consolidation.md](plans/nuvio-focus-consolidation.md).
**P0 implemented later the same session (11th patch `nuvio-focus-p0.patch`,
chain-fidelity verified, built on brook `aq7blsw…` and switched gen `m1gry23…`;
couch pass pending):** `resolveArrowKey` extracted
to a pure commonMain fn + `commonTest` truth table; diagnostics routed through
a single `NuvioFocusDiagnostics` sink with a headless-assertable focus-ring
semantics marker; the desktop rail migrated to the declarative
`corridorEdges(...)` helper (categories deferred — the active-only
`categoryEntryRequester` makes that migration behavior-affecting). Then P1
visible parking + `FocusGraphTest`; P2 `FocusableSurface` + ringless backlog
and scoped links/navigator; P3 text-tracker consolidation, patch-chain
consolidation (rounds 3–4d → one patch) and upstream PRs. Fork exit criteria:
retest 7 + a regression retest pass, headless suite green covering the
acceptance list, upstream PRs filed, ringless backlog covered or waived.

### Session-13 addendum 9 (2026-10-08, P0 couch pass + retest-7 fixes)

P0 (11th patch) built and deployed, then couch-tested. Subpage entry PASS,
Settings Root Up PASS, most of the regression pass PASS. Failures:

1. **Blocker (retest-6 class — round 4d did not fix it).** A or D-pad Right from
   a non-Settings rail item made focus vanish; one Left returned to the rail.
   Journal (`/tmp/nuvio-oct08.txt`, PID 336515): `anchor=true` = **0**, and
   `home-hero`/`library-source-chip` landings = **0**. Root cause (ora-2):
   `RootTabHost` forced `canFocus = active` on the pane Box, which promoted the
   `LazyColumn`'s `Focusability.Never` scroll anchor into a legal 2D candidate —
   Right landed on that invisible, dead-end pane anchor (no ring/click; every
   further arrow `moved=false`). A dropped focus for the same reason. Fix:
   `if (!active) canFocus = false` (only opt inactive panes out).
2. **Ring gaps.** `HomescreenCatalogRow` (CATALOGS & COLLECTIONS) was a
   ringless multi-stop cluster; `PosterToggleRow`'s Switch and
   `PosterStyleOptionRow`'s FilterChips were ringless. Fixed (row treatment
   mirroring `SettingsSwitchRow`; direct Switch/FilterChip rings).
3. **Poster Card Style text field** trapped Up/Down because it was declared
   multiline (`singleLine=false`, `maxLines=4`); reclassified single-line.
4. **Regression:** Right from a category into a scrolled submenu no-opped
   (`contentEntry` targeted the first Root row, uncomposed once scrolled). Root
   now targets the always-composed content-group Box.

Shipped as the **12th patch `nuvio-focus-retest7-fixes.patch`** (chain fidelity
== work tree), built `0vnvpv805…`, deployed generation `hji0k8j…` (2026-10-08,
no reboot). **Couch re-test pending.** Watch item: Left-from-content→rail on
Home/Search/Library now relies on geometry (the explicit pane `left` edge lived
on the now-non-focusable anchor).

### Session-13 addendum 10 (2026-10-08, retest-7 follow-up + autonomous test harness)

Second couch pass on the 12th-patch build: items 2/3/5 PASS, item 1 PARTIAL,
item 4 FAIL. Fixed in the **13th patch `nuvio-focus-item-fixes.patch`** (oracle
ora-2 root causes):

- **Item 4 (FAIL):** subpage re-entry restores the saved `LazyColumn` scroll
  offset, so the header back button is uncomposed and `backRequester` is
  unattached → the page-entry effect never places focus → focus-nowhere → the
  next Right lands on the profile/avatar. Fix: `contentEntry` targets the
  always-composed content group on Root AND subpages, plus a page-entry
  fallback to it (Root's auto-entry still prefers the back button + Down, no
  flash).
- **Item 1 (PARTIAL):** hero page-indicator dots were ringless neighbour-less
  stops (now `canFocus = false`, pointer-click kept); `FullscreenActionButton`
  and Library/Search header `IconButton`s ringed; Search field no longer
  re-captures focus on focus loss.

**Autonomous testing unblocked.** The headless Compose focus suite now runs on
brook outside the nix sandbox (Xvfb + software Skiko + `LD_LIBRARY_PATH` from
the app's Skiko RPATH; recipe in the consolidation plan). The first
real-component contract test `FocusContractTest` passes, and `ArrowKeyNavTest`
is 4/4. Focus-graph/ring/text classes are now verifiable without the couch;
remaining couch-only work is the physical controller/feel, the wlroots AWT
focus race, and subjective ring legibility. Current suite (green): `ArrowKeyNavTest`,
`FocusContractTest` (rail Right → ringed+actionable, corridor walk),
`SettingsSubpageEntryTest` (real SettingsScreen subpage entry), plus
`scripts/focus-contract.sh` (journal verdict; flagged a 17-press dead-end run
in the pre-13th-patch journal). Tests are durable as the 13th patch plus
`nuvio-focus-contract-tests.patch`.

Deployed: package `ijiy2xhi…`, generation `74z3pkhf…` (2026-10-08, no reboot).
Couch pass pending.

### Session-13 addendum 11 (2026-10-08, ring coverage + Settings UX)

Couch pass on the 13th build: item 4 PASS (user requests: reset subpage scroll on
close; restore the last-focused row on Right), item 1 PARTIAL (focus works for
all items but many surfaces show no ring), regression PASS (theme colors
ringless). Audit (exp-3) mapped the ringless class; oracle (ora-2) chose
targeted ring additions now, `FocusableSurface` in P2. Fixes in the **14th patch
`nuvio-focus-ring-coverage.patch`**:

- rings on ~26 remaining couch-reachable surfaces (Continue Watching cards, home
  folder/collection cards, Search Discover + Library Saved grid tiles, theme
  chips/swatches/slots, `NuvioDropdownChip`, `NuvioViewAllPill`, catalog tiles,
  recent-search rows, Classic nav, dialogs/menus/sheets, several settings rows);
- **Q1:** non-Root Settings subpages `requestScrollToItem(0)` on entry (Root
  keeps position; saver retained);
- **Q2:** `focusRestorer()` on the content-group Box AND its `LazyColumn` so
  Right from a category restores the last-focused content row.

All 7 headless tests green. Deployed package `8iriw9ka…`, generation
`jvlg25jr…` (2026-10-08, no reboot). Couch pass pending.

### Session-13 addendum 12 (2026-10-08, behavior fixes A–D)

Couch pass on the 14th build: items 1/2/3 PASS with four follow-ups; `ora-2`
root-caused each; **15th patch `nuvio-focus-behavior-fixes.patch`**:
(A) the desktop shelf **title** is no longer a 2D focus stop (it was full-width
and sat below every poster row, causing a double Up/Down to cross Home folder
rows); pointer click retained. (B) the details screen had no entry focus effect,
so after the nav push removed the focused poster, focus parked on the header
back button — added a `FocusRequester` on the primary play action (threaded
through `DetailActionButtons`/`DetailActions`/`DesktopDetailHero`) requested
across up to 8 frames on open; `detail-play`/`detail-back` tags. (C) subpage
scroll no longer restores: non-Root Settings pages use a plain
`remember(LazyListState)` (Root keeps the saver) — removes the re-entry flicker.
(D) explicit `left = categoryEntry` on `SettingsNavigationRow`/`SettingsSwitchRow`
so Left returns to the categories instead of the back button (row-level, so it
doesn't collide with `focusRestorer`'s `onExit` → Q2 intact). All 7 headless
tests green. Deployed package `cqa30kaq…`, generation `azs7cmg…`. Couch pass
pending.

### Session-13 addendum 13 (2026-10-09, E/F fixes + session closeout)

Couch pass on the 15th build: A/B/C/D all PASS; two follow-ups.

- **E:** Left from the Settings subpage back button landed on the profile; fixed
  with `left = SettingsFocusLinks.categoryEntry` on the `TabletPageHeader` back
  button.
- **F:** the Home folder category arrows open `CatalogScreen` ("View all"); its
  entry focus parked on the back button; fixed with a `focusGroup()` +
  `FocusRequester` on the grid-wrapping Box, requested across frames on item
  load (the grid is declared before the header, so the group enters the first
  tile).

Shipped as the **16th patch `nuvio-focus-ef-fixes.patch`**, chain-fidelity
verified, built `k9c0zhw1…` and deployed generation `zb2mbjgv…` (2026-10-09,
no reboot). **E/F couch-verified PASS; regression PASS.** Keep brook on AC power
(battery failing).

**Session closeout / fresh-session entry point:**
[docs/plans/nuvio-focus-consolidation.md](plans/nuvio-focus-consolidation.md)
§Resume point (deployed state, first actions, harness recipe, patch chain,
residual gaps). Next: P2 (`FocusableSurface`, scoped focus state), patch-chain
consolidation, upstream PRs.

[STATE lines 652–666 — Roadmap & next points: item 1, Nuvio controller nav:]

## Roadmap & next points (s10, 2026-10-05)

Scope stages and the working agreement live in `ROADMAP.md` (cwrneirOS).
V1 queue (order may adjust as findings land):

1. Nuvio controller navigation (V1.2 open): the focus/navigation work now lives
   in
   [docs/plans/nuvio-focus-consolidation.md](plans/nuvio-focus-consolidation.md)
   (implementation log + §Resume point). Deployed through the 15th patch
   (`nuvio-focus-behavior-fixes.patch`, 2026-10-09, generation `azs7cmg…`);
   the 16th (`nuvio-focus-ef-fixes.patch`) is installed/chain-verified but
   blocked on brook being down. The headless focus-contract suite runs on brook;
   journal verdict via `scripts/focus-contract.sh`. Next: deploy the 16th +
   couch-test E/F, then P2 (`FocusableSurface`, scoped focus state),
   patch-chain consolidation, upstream PRs.

[STATE lines 683–698 — Current deployment on brook: the nuvio deploy ledger:]

## Current deployment on brook (192.168.1.167, host alias `brook`)

- **Active config:** spike-b (sway) via `nixos-rebuild switch` from
  `~/cwrneirOS`; systemd-boot rollback generations intact. Latest deploy:
  s13 continued (2026-10-09) — nuvio focus **E/F fixes** (16th patch
  `nuvio-focus-ef-fixes.patch`: back-button Left→categories; CatalogScreen
  entry focus), switched without reboot (generation `zb2mbjgv…`, package
  `/nix/store/k9c0zhw1…`; E/F couch-verified PASS). Previous same-day: behavior fixes
  A–D (15th patch, generation `azs7cmg…`, package `/nix/store/cqa30kaq…`,
  couch-verified); ring coverage + Q1/Q2 (14th patch, generation `jvlg25jr…`,
  package `/nix/store/8iriw9ka…`); item-1/item-4 fixes (13th patch, generation
  `74z3pkhf…`, package `/nix/store/ijiy2xhi…`); retest-7 fixes (12th, generation
  `hji0k8j…`); P0 (11th, generation `m1gry23…`). Previous: s13 round-4d
  (2026-10-07, generation `pgssrv1l…`, package `/nix/store/ygicp2m4…`).
  Previous verified deploy:
  s8 (2026-10-02).

[STATE lines 799–810 — Key decisions item 8: nixpkgs-primary + Nuvio AppImage wrap:]

## Key decisions (chronological, all recorded in ADR)

1. Staged decision: stack below compositor committed; compositor by spike.
2. Spike A (gamescope) **failed structurally** — no layer-shell keyboard
   focus in 3.16.29 (source-proven by research lane).
3. KBL scanout fixed by our nixpkgs overlay patch (linear flippable images).
4. Compositor re-evaluation: **sway** selected (librarian + oracle
   convergence); labwc fallback; niri watchlist; Hyprland dropped.
5. Gamescope demoted to phase-2 nested per-game (user decision).
6. Compositor must be greetd **session child**; shell/input as user units.
7. Idle timer removed at user request (B2 idle half deferred to hardening).
8. App distribution: nixpkgs-primary + Nuvio AppImage wrap (ADR §4).
