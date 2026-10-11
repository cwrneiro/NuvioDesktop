# Plan — Nuvio controller navigation, implementation round 2

Status: **agreed 2026-10-05 (session 12), NOT started** — write no code before
re-reading this file end to end. Context: ROADMAP V1.2 (re-opened),
STATE.md session 12, ADR finding 21. Everything below is hardware-evidenced;
sources: oracle design consult (ora-1) + web research (lib-1), both 2026-10-05.

## Where things stand (do not re-derive)

- The appliance runs a **patched source build** of Nuvio 0.1.27-alpha:
  `packages/nuvio-src/` in this repo (vendored from nixpkgs PR #543092 —
  Gradle build, deps.json is 0.1.26's; 0.1.26→0.1.27 changed no Gradle file).
  Patches applied in order:
  1. `nuvio-arrow-nav-0.1.27.patch` — upstream PR #180 (aelrased) rebased
     onto 0.1.27 by us (one structural adaptation: FilterChip moved to
     `ProviderFilterRow.kt`).
  2. `nuvio-arrow-nav-fixes.patch` — OUR fix: removed the redundant
     `Modifier.focusable()` the PR added inside `nuvioFocusBorder` (items are
     already clickable/selectable ⇒ two focus targets per item caused:
     every-other-press-dead stepping, ring/tint alternation, A sometimes
     activating nothing). Hardware-verified fixed.
  Overlay exposes `pkgs.nuvio-src`; `modules/apps.nix` launcher + systemPackages
  use it; `pkgs.nuvio` (AppImage wrap) is kept as fallback packaging.
- **Working on hardware now**: launch from shell tile; `nuvio-focus` helper
  fixes AWT focus at launch; arrows step ONE item per press; A activates the
  focused item reliably; Guide overlay; shell-level B-close.
- **Open gaps (user couch feedback, root-caused)**:
  (a) Left rail (Home/Search/Library/Settings) shows only a subtle tint —
      **PR #180 never applied rings to desktop nav surfaces**. The pill-bar
      `NavigationBar.kt` items are the MOBILE bottom bar (guarded
      `!isDesktop`). The real surfaces are:
      - `DesktopSidebarItem` — `AppShellComponents.kt:937` — `Surface(onClick)`, no ring.
      - profile trigger — `AppShellComponents.kt:777` — `.clickable(indication = null)`, zero feedback.
      - top bar variant — `core/ui/DesktopNavigationBar.kt` → `JellyTabs.kt` items, no ring.
  (b) B/Esc does nothing in menus: `PlatformBackHandler` desktop actual is
      `= Unit` (stub) while Android wires a real dispatcher; ~12 call sites
      (SettingsScreen page stack, player overlays ×3, CollectionEditor ×4,
      AppGate ×2, MainTabsDestination, PosterZoomActionOverlay) wait behind it.
  (c) Player transport untested (no media yet). Overlays already register
      back handlers ⇒ gap (b) fix unblocks them for free.
  (d) AWT dispatcher eats arrow keys in Compose text fields (caret broken).
- **Facts verified from androidx/CMP source** (lib-1):
  - `onFocusChanged` observes the first focusTarget AFTER it in the chain.
    `isFocused` true only if that exact node is the focus target;
    `hasFocus` true also when a descendant holds focus (ActiveParent).
    ⇒ observe `hasFocus` for rings.
  - `selectable`/`clickable` ARE keyboard-focusable (they end in
    `focusable()`); Enter/Space activate on **KeyUp** (our press-time A tap
    is fine). `focusable` auto-`bringIntoView()`s on focus gain ⇒ LazyRow/
    LazyColumn scroll to the focused item with NO extra work (verify, don't code).
  - `Modifier.border(w, c, shape)` strokes entirely INSIDE bounds (inset by
    half stroke) — same-shape clip does not cut it. (An oracle note claimed
    half-clipping; the Border.kt source reading says inset — either way the
    design below (outset ring before clip) is immune.)
  - CMP has NO built-in arrow navigation and no BackHandler; official KMP
    back API is `androidx.navigationevent` (future path, not needed now).
  - Upstream state (2026-10-05): PR #180 open, dirty, idle since Sep 12
    review (reviewer already flagged "catalog wrapper hit before child card"
    and "no indication what is focused" — our fixes answer both). **PR #791
    (open, cleanly mergeable) implements desktop back**: common
    `PlatformBackDispatcher` (last-enabled-wins), real desktop
    `PlatformBackHandler` actual, Esc dispatch in Main.kt, mouse-Back
    fallback → `popBackStack()`. ADOPT IT, don't reinvent.
  - Nothing keyboard-nav-related landed on Dev after 0.1.27 — we're not
    duplicating upstream work (except #791, deliberately).

## Scratch tree (regenerable — repo patches are the source of truth)

If `/tmp/opencode/nuvio-spike/` is missing, recreate:
```
cd /tmp/opencode/nuvio-spike
curl -sL -o nuvio-0.1.27.tar.gz https://github.com/NuvioMedia/NuvioDesktop/archive/refs/tags/0.1.27-alpha.tar.gz
mkdir verify && tar xzf nuvio-0.1.27.tar.gz -C verify
cd verify/NuvioDesktop-0.1.27-alpha
git apply -p1 <repo>/packages/nuvio-src/nuvio-arrow-nav-0.1.27.patch
git apply -p1 <repo>/packages/nuvio-src/nuvio-arrow-nav-fixes.patch
# → this tree == the previously working "b" tree
```
Reference copies of nixpkgs PR #543092 files: `nixpkgs-ref/` (also in repo
under packages/nuvio-src/). Patch workflow: edit the tree, regenerate the
"fixes" patch (diff -ruN between patch-1-only tree and edited tree via
a/-b/ prefixed dirs), validate combined apply on a fresh extract, copy into
`packages/nuvio-src/`, bump nothing else. Consider splitting our fixes into
per-topic patch files this round (ring / back / tracker) for reviewability.

## Build & deploy loop (proven)

Dev box: `nix eval .#packages.x86_64-linux.nuvio-src.drvPath` (sanity).
Sync + build + switch on brook (build heavy only first time; ~10 min cycle):
```
tar cf - flake.nix packages overlays modules apps hosts | ssh brook 'tar xf - -C ~/cwrneirOS'
sudo nixos-rebuild switch --flake /home/carneiro/cwrneirOS#spike-b   # ask user for sudo pw
```
Remote verification kit (brook): `nix shell nixpkgs#xdotool nixpkgs#wtype`;
screenshots `grim` + scp; `qs -p /etc/cwr/shell ipc call tvshell toggle` for
shell state; AWT focus check helper binary may need recompile from
`packages/nuvio-focus.c` (or read journal `nuvio-focus: X focus moved`).
NOTE: shell must be HIDDEN when launching the app for focus to land
(toggle direction bug bit us once — restart quickshell first, then toggle
once = hidden).

## Work items

### 1. Focus ring: rewrite + cover the desktop nav surfaces (core legibility)

Rewrite `core/ui/NuvioFocusable.kt` (~50 LOC):
- observe `onFocusChanged { it.hasFocus }` (NOT isFocused);
- draw an **outset ring** via `drawBehind`: inflate the item rect by
  `borderWidth/2 + ~1dp gap`, `shape.createOutline(...)`, stroke full width
  (3dp default). Outset = drawn outside the item edge ⇒ couch-legible and
  immune to clip/focus-topology traps by construction;
- place the modifier **BEFORE any `.clip(...)` in the chain** (document the
  rule in its KDoc); drawing outside own bounds is fine in Compose (parents
  have 4–6dp padding — verify no visual clipping on the sidebar).
Apply it (chain-first discipline) to:
- `AppShellComponents.kt`: `DesktopSidebarItem` (:~948), profile trigger
  (:~777), `SidebarProfileSwitcherStack` rows;
- `core/ui/JellyTabs.kt` top-bar items (TopBar layout);
- existing applications (cards, shelves, chips, settings, profile screens):
  mechanical reorder before clip where thin (opportunistic, cosmetic).
Est. 2–4 h + one build cycle. Ship rail+trigger+topbar first.

### 2. In-app back: adopt upstream PR #791 (unblocks ~12 call sites)

- Fetch PR #791 diff (`https://github.com/NuvioMedia/NuvioDesktop/pull/791.diff`),
  review against 0.1.27 (it is mergeable-clean on Dev ⇒ likely applies), and
  add as a repo patch file `nuvio-back-handling-791.patch` after the nav
  patches. Adapt if it conflicts.
- Verify Esc routing works with OUR AWT chokepoint: #791 dispatches Esc in
  `Main.kt` (Compose-level). If both our dispatcher and theirs react to
  arrows/esc, reconcile: our `PlatformKeyboardNavigation.desktop.kt` should
  handle VK_ESCAPE by consulting the same dispatcher if #791's Main.kt hook
  proves insufficient under XWayland. One of the two routes must own Esc.
- Pitfalls checked: LIFO ordering matches Android (composition order);
  `enabled` toggling via SideEffect avoids churn; popups that handle Esc
  themselves (`PinEntryDialog.kt:128`, `ProfileSwitcherTab.kt:563`) keep
  working when the registry returns unhandled.
- Product decision to COUCH-TEST, default keep Android parity: Esc at Home
  root routes to `MainTabsDestination.kt:84` external back (exit to profile
  selection). If annoying, gate `rootRouteActive && backstack.size > 1`.
- Shell interplay already holds: our shell consumes B/Esc at the compositor
  when visible; app-level Esc only fires while the app holds focus. Confirm
  once via test 3 below.
Est. 2–3 h, same build cycle.

### 3. Text-field arrow tracker (search caret)

- ~25 LOC `desktopMain` `DesktopTextInputState` (atomic bool or
  `mutableStateOf`) + `Modifier.nuvioTracksTextInput()` (onFocusChanged
  set/clear with overlap counter, DisposableEffect cleanup; CMP composition
  and AWT EDT are the same thread for ComposeWindow).
- AWT dispatcher: skip arrow interception while the flag is set (arrows move
  the caret; Esc unaffected).
- Apply to the ~8 couch-reachable fields: `NuvioInputField`
  (`core/ui/Components.kt:358` — covers several screens), settings secret
  field, `SettingsSearch.kt:1187`, `LibraryScreen.kt:659`, AuthScreen
  BasicTextFields, `ServerConnectionDialogs.kt:181`.
Est. 1–2 h, same build cycle.

### 4. No-code verifications (probes during verification)

- Lazy shelves auto-scroll under moveFocus (expect yes — bringIntoView).
- First-arrow-after-tab-switch dead? (focus may be nowhere after tab change;
  if dead, cheapest fix = root-level fallback focus anchor; full
  `focusRestorer` per screen is Phase 2.)
- Popup windows (profile switcher, PIN dialog): do arrows work inside?
  (CMP popups are separate windows; our AWT dispatcher should still see
  events — verify.)

### 5. Deferred (explicitly out of scope this round)

`focusRestorer` screens, player controls polish (needs media), text fields
beyond the reachable set, `androidx.navigationevent` migration, PR #180's
mobile-bottom-bar surfaces.

## Reference patterns from Nuvio sibling apps (addendum 2026-10-05)

Evaluated per user request (NuvioTV = Android/Compose, NuvioTVSmart =
webOS/Tizen JS). **Neither has any gamepad/controller code** (no
`KEYCODE_BUTTON_*`, axes, `SOURCE_GAMEPAD`, Gamepad API, SDL, evdev) — no
alternative implementation path; current plan stands. Confirms **zero
app-side controller awareness** across the family ⇒ OS-level key synthesis
(current approach) is the right architecture, nothing to build on/conflict
with app-side. Clones are scratch (`/tmp/opencode/NuvioTV`, regenerable:
`git clone --depth 1 https://github.com/NuvioMedia/NuvioTV` + same pattern
for NuvioTVSmart). Keep as UX-parameter references for later rounds:

- **D-pad repeat timing (house convention)**: NuvioTV
  `ui/util/DpadThrottleModifier.kt:23-66` gates repeats at 80ms horiz /
  112ms vert; NuvioTVSmart `js/ui/navigation/dpadRepeatThrottle.js:16` has
  identical numbers. Adopt when arrow repeat/gating lands.
- **Held-key fast scroll**: `ui/util/DpadFastScrollModifier.kt:94-263` —
  repeat → frame-driven drag at 3200dp/s, focus frozen, edge/halt guards,
  landing resolver. Feeds probe §4 / future shelves.
- **Seek acceleration**: `ui/screens/player/PlayerScrubRates.kt:11-46` —
  10/20/30/60s steps at repeatCount thresholds 3/8/15. Spec for player
  round (gap c).
- **Long-press**: `ui/util/LongPressKeyTracker.kt:11-74` — generic
  repeat/isLongPress/timeout detector.
- **Player key state machine**: `ui/screens/player/PlayerScreen.kt:635-880`.
- **RTL key mirroring**: `ui/util/RtlKeyUtils.kt:12-18`.
- **Phase-2 focus patterns**: `focusRestorer`/`focusGroup` usage in 30+
  files; focus affordance `ui/components/FocusMarqueeText.kt:28` (same
  problem class as item 1's ring).

## Verification plan (per build, remote first, then couch)

1. Ring: Down/Up through rail items → screenshots show outset ring on each;
   profile trigger ringed; TopBar layout ringed; cards unregressed.
2. Back: Settings subpage → Esc pops one level, again → root; details page →
   Esc pops; collection editor exits edit mode; (player overlay when media:
   controls → Esc dismisses, second Esc leaves player).
3. Esc isolation: Guide over app → B closes app (shell path) and app state
   unchanged (no double-fire through #791).
4. Text: Search field focused → Left/Right move caret (screenshot cursor);
   arrows don't jump focus; Esc still backs out; unfocused → arrows navigate.
5. Shelf scroll: hold Right past viewport → row follows focus.
6. Probes from §4. Then the user's couch pass (the reported gaps + feel).

## Upstream contribution plan (after hardware-verified)

1. Standalone PR (or endorsement of #791): desktop `PlatformBackHandler`
   actual + dispatcher — we can offer our hardware validation + any XWayland
   findings.
2. PR #180 review feedback with patches: (a) the double-`.focusable()` bug
   (already fixed by us — attach diff), (b) outset `hasFocus` ring guidance,
   (c) missing desktop surfaces (sidebar/profile trigger/JellyTabs).
3. Issue, not PR: text-focus introspection pattern (CMP can't see Compose
   text fields from AWT) + root-Esc product question.
4. Report: popup focus findings, LazyRow auto-scroll confirmation.
Sequence: build+verify once in the fork → couch pass → split into PRs.

## Definition of done for this round

Ring visible+uniform on all couch-reachable nav surfaces; Esc/B pops every
menu/submenu; text fields usable via gamepad+OSK-later; probes recorded;
couch pass accepted by user; findings folded into ADR/STATE; upstream PRs
filed or prepared.
