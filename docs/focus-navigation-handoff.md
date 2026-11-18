# Focus navigation handoff

Last updated: 2026-11-17

This document is the entry point for anyone continuing the desktop keyboard/remote
focus-navigation work. Read it before touching focus, arrow-key routing, modal
layers, or controller navigation.

## Read this first

- Work from the persistent worktree, never `/tmp`:
  `/home/carneiro/Documents/nuvio-worktrees/settings-focus-handoff`
- Current target is upstream `Dev`, not our old `pr/focus-nav` branch.
- Do not merge the old Compose-only focus implementation wholesale into the current
  `ControllerFocusEngine` architecture. Port individual correctness fixes.
- Build with the memory-capped command in [Testing runbook](#testing-runbook).
- Commit every patch immediately. `/tmp` can disappear on reboot.

## Current state

### Upstream

- Upstream repo: `NuvioMedia/NuvioDesktop`
- `Dev` head at last fetch: `c0eb7bfa7`
  - `Merge remote-tracking branch 'mobile/cmp-rewrite' into Dev`
  - 2026-11-17 18:01 +0300
- PR #945 (`feat(desktop): keyboard/gamepad navigation with focus rings`) merged as
  `45e5ec91a` on 2026-11-17.
- PR #180 (`Keyboard Arrow Navigation`) is closed and unmerged. #945 superseded it.

### Local branches

| Branch | Base | State | Purpose |
|---|---|---|---|
| `pr/focus-nav` | `d4bdf258` | 15 commits ahead; 992 behind `upstream/Dev` | Old Compose-native implementation and original findings |
| `cwrn/packaging` | old `Dev` | 20 commits ahead; 992 behind `upstream/Dev` | Fork packaging + PR #180 rebase + earlier focus work |
| `fix/settings-focus-handoff` | `c0eb7bfa7` | 2 commits ahead, 0 behind | Current verified fixes on latest `Dev` |

Main working tree: `/home/carneiro/Documents/NuvioDesktop` (`pr/focus-nav`)

Fix worktree: `/home/carneiro/Documents/nuvio-worktrees/settings-focus-handoff`

### Fix branch commits

```text
6d31b3d35 fix(desktop): route Escape through Compose back handlers
c95e70356 fix(settings): hand off focus from categories to content
c0eb7bfa7 Merge remote-tracking branch 'mobile/cmp-rewrite' into Dev
```

Changed files (6 files, +70/−13):

- `SettingsScreen.kt`
- `SettingsComponents.kt`
- `DesktopEscapeShortcut.kt`
- `DesktopKeyboardShortcutHost.kt`
- `PlatformEscapeShortcut.desktop.kt`
- `PlatformKeyboardNavigation.desktop.kt`

## Upstream timeline

- 2026-06-29: #180 opened by `aelrased` with an AWT arrow dispatcher and
  `nuvioFocusBorder`.
- 2026-08-31–09-07: review asked for smart arrow consumption, desktop-only
  behavior, and a rebase. Rewrite added AWT component checks and more focus rings.
- 2026-09-12: HTPC testing found seven navigation gaps.
- 2026-10-06–10-12: fixes for shelf wrappers, Continue Watching, Enter activation,
  Esc-as-back, initial focus, and a failed-move fallback.
- 2026-10-19: branch head became a merge of current `Dev` plus personal WIP.
- 2026-10-20: #180 closed; #945 opened as a clean rewrite plus gamepad support.
- 2026-10-27–11-09: #945 review/testing fixes for wrap-around, sidebar/top-bar
  exits, initial focus, back behavior, spatial/dual-pad navigation.
- 2026-11-17: #945 merged into `Dev`.

## Architecture comparison

### Current `Dev` / #945

- Custom `ControllerFocusEngine` (~2,650 lines, ported from NuvioDeck, GPLv3).
- Instrumented screens use `Modifier.controllerFocusable`; other screens fall back
  to Compose focus.
- Full controller stack: XInput on Windows, SDL2 via JNA on Linux/macOS, repeat,
  deadzones, multiple pads, hot-plug.
- Controller sounds, hint toolbar, virtual keyboard.
- Custom visual focus treatment: scale, sheen, sibling dimming, z-order.
- Sophisticated scroll centering and route focus memory.

### Our old implementation (`pr/focus-nav`)

- Compose focus everywhere, driven by an app-level AWT `KeyEventDispatcher`.
- `FocusCorridor` + explicit `focusProperties` edges instead of geometric search.
- `PlatformModalState`, `NuvioModalDialog`, seeded modal focus, modal-internal
  arrow handling, and NaN-rect recovery anchors.
- Compose text-field and slider trackers so arrows keep caret/thumb semantics.
- Contract tests and a real-window AWT modal test.
- No gamepad, sounds, hint toolbar, or route focus memory.

### Practical conclusion

They cannot be merged blindly. Both claim the app-level arrow dispatcher.
The sane path is:

1. Keep #945's engine/input/scroll/hints.
2. Port our correctness layer on top:
   - modal ownership
   - text/slider arrow semantics
   - recovery anchors and focus-presence tracking
   - explicit Settings/rail/content edges
   - regression tests

## Verified gaps in current `Dev` (pre-fix)

### Bug 1: Settings category → content focus handoff

Reproduced on `c0eb7bfa7`, Linux Hyprland/Wayland, fullscreen:

1. Open Settings.
2. Tab to focus `You`.
3. Right.

Observed:

- Right moved to `Look` inside the category rail, or cleared the ring entirely.
- No content row (`Appearance` / `Layout`) received focus.
- Enter did nothing while focus was invisible.
- Further arrows no-oped or wrapped unexpectedly (e.g. back to `You`).
- From `System`/`Media`, Right cleared the ring and Up produced zero visual change.

Root cause:

- `nuvioContentEntry()` exists in `NuvioFocusable.kt` but is unused in `Dev`.
- Settings category items have no explicit `Right` target into the content pane.
- Compose falls back to geometric search and can land on an invisible/non-actionable
  target.
- There is no recovery anchor equivalent in current `Dev`.

Fix: `c95e70356`

- Add `ContentEntry.requester` as the category `Right` target.
- Add `nuvioContentEntry()` to the Settings content root.
- Stop category wrap with `up = Cancel` on the first item and `down = Cancel` on
  the last.
- Let `SettingsSidebarItem` accept a modifier.

Verified after fix:

- Tab → `You` ring
- Right → `Appearance` ring
- Down → `Layout` ring
- Left → `Look` category ring
- No dead focus, no wrap

### Bug 2: Escape from a Settings subpage

Reproduced on `c0eb7bfa7`:

1. Settings → Look
2. Focus `Appearance`, press Enter
3. Press Esc once

Observed:

- Returned to Home instead of `Settings → Look`.
- With a naive back-handler stack, Escape did nothing at all.

Root cause:

- The AWT Escape shortcut bypassed Compose's `NavigationBackHandler` ordering and
  called app-level navigation back directly.
- Settings subpage handlers never ran.

Fix: `6d31b3d35`

- Route Escape through Compose's navigation back dispatcher using
  `LocalNavigationEventDispatcherOwner` and `DirectNavigationEventInput`.
- Only fall back to the app-level back handler when no Compose handler is enabled.
- Gamepad B uses the same `DesktopEscapeShortcut.dispatch()` path.

Verified after fix:

- Enter on Appearance → subpage
- Esc → `Settings → Look`, not Home

## Evidence

Persistent local evidence:

- `/home/carneiro/Documents/nuvio-worktrees/logs/focus-seq.png`
- `/home/carneiro/Documents/nuvio-worktrees/logs/07-esc.png`
- `/home/carneiro/Documents/nuvio-worktrees/logs/compile.log`
- `/home/carneiro/Documents/nuvio-worktrees/logs/run-focus.log`

Baseline screenshots from the pre-fix build were lost when `/tmp` was wiped by a
reboot. Re-capture against `c0eb7bfa7` if needed.

## High-value work still to port

Priority order:

1. **Modal focus ownership**
   - Old files: `PlatformModalState.kt`, `NuvioModalDialog.kt`,
     `ModalArrowNav.desktop.kt`, `AwtDispatcherModalFocusTest.kt`.
   - Gate both keyboard dispatcher and controller events while a modal is open.
   - Seed dialog focus and let the dialog own arrows.
   - Use a NaN focus rect on the seeded layer container so it stops being a
     directional candidate.
   - Gate controller events too; keyboard-only gating is not enough.

2. **Compose text-field and slider arrow semantics**
   - Old files: `ArrowKeyNav.kt`, `DesktopTextInputState.kt`,
     `TextInputTracker.desktop.kt`, `DesktopSliderState.kt`,
     `SliderTracker.desktop.kt`, `FocusTrackerWiringTest.kt`.
   - `Left`/`Right` belong to caret/thumb; `Up`/`Down` stay with multiline fields
     but traverse in single-line fields.
   - #945 still documents this as a known limitation.
   - If an engine text-input node is focused, hand focus to the real Compose field
     before applying caret semantics.

3. **Recovery anchor / focus presence**
   - Old files: `PlatformFocusPresence.kt`, `FocusRetry.kt`.
   - Root anchor has an all-NaN `focusRect`; when nothing else holds focus, park
     there so hardware Back/Esc still works.
   - Before retrying a failed directional move, clear the anchor so 2D search is
     not poisoned.
   - `FocusAnchorContractTest.kt` pins this CMP behavior.

4. **Explicit focus graph edges**
   - Old files: `FocusCorridor.kt`, `SettingsFocusLinks.kt`.
   - Rail ↔ content, Settings categories ↔ rows, subpage back button ↔ content.
   - Do not port the whole corridor system blindly; use it where current `Dev`
     still delegates to Compose focus.

5. **Tests**
   - `FocusContractTest.kt`: ringed + actionable landing, rail corridor walk.
   - `FocusAnchorContractTest.kt`: NaN anchor semantics.
   - `SettingsSubpageEntryTest.kt`: subpage entry lands on a real control.
   - `AwtDispatcherModalFocusTest.kt`: real windows + real AWT events.
   - Tracker unit tests.
   - Adapt to the current engine where needed. #945 added no tests.

6. **Only after the above**: consider unifying visual focus rings or controller
   behavior. Do not mix the custom engine and our old dispatcher without a single
   owner for arrow events.

## #945 pitfalls / dead code

- `nuvioContentEntry()` is defined but unused.
- `FocusPresence`, `NavRailFocus`, `TopBarFocus`, `ContentEntry`, `RailEntry` are
  partially or entirely unwired.
- Settings has no explicit focus edges between categories and content.
- `DesktopEscapeShortcut` bypasses Compose `NavigationBackHandler` ordering.
- No regression tests were added by #945.
- Text-field caret behavior remains a documented known limitation.

## Testing runbook

### Environment

- Arch Linux, Hyprland/Wayland, 1920x1080
- JDK 17: `/usr/lib/jvm/java-17-openjdk`
- System `mpv` package already includes `/usr/include/mpv/client.h`
- `local.properties` must exist in the worktree; empty file is sufficient

### Why the native bridge failed once

The shell resolves `c++` to Nix's GCC wrapper, which does not see `/usr/include`.
Prefix `PATH` with system directories so `c++` and `pkg-config` are Arch's:

```bash
PATH=/usr/bin:/bin:/usr/local/bin:$PATH
```

### Build with memory caps

Do not use the default 12 GiB Gradle heap; it can exhaust this 15 GiB machine
when combined with Kotlin and the app. Use:

```bash
cd /home/carneiro/Documents/nuvio-worktrees/settings-focus-handoff
PATH=/usr/bin:/bin:/usr/local/bin:$PATH \
JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
GRADLE_OPTS='-Xmx1g' \
./gradlew --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs='-Xmx4g -XX:MaxMetaspaceSize=1g' \
  -Pkotlin.compiler.execution.strategy=in-process \
  :composeApp:compileKotlinDesktop
```

For running:

```bash
PATH=/usr/bin:/bin:/usr/local/bin:$PATH \
JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
GRADLE_OPTS='-Xmx1g' \
./gradlew --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs='-Xmx4g -XX:MaxMetaspaceSize=1g' \
  -Pkotlin.compiler.execution.strategy=in-process \
  :composeApp:run
```

### UI automation

Tools installed: `hyprctl`, `ydotool` (daemon running), `grim`, ImageMagick,
`tesseract`, `ffmpeg`.

Useful key codes for `ydotool key`:

| Key | Code |
|---|---|
| Esc | 1 |
| Tab | 15 |
| Enter | 28 |
| F | 33 |
| Left | 105 |
| Right | 106 |
| Up | 103 |
| Down | 108 |
| Left Meta/Super | 125 |

Examples:

```bash
# Super+F fullscreen
ydotool key 125:1 33:1 33:0 125:0

# Press Right
ydotool key 106:1 106:0
```

Window:

- class: `com-nuvio-app-MainKt`
- title: `Nuvio`
- xwayland: true

Hyprland notes:

- `hyprctl dispatch` on this version is Lua-based and the old syntax fails.
- Use `ydotool` keybinds to switch workspaces/fullscreen.
- `ydotool mousemove -a` was unreliable; use relative movement and verify with
  `hyprctl cursorpos` after each step.
- Clicking the app may move it to the active workspace; verify the active window.
- Stop the app with a bracket pattern so the kill command does not match itself:

```bash
for p in $(pgrep -f 'com[.]nuvio[.]app[.]MainKt'); do kill "$p"; done
```

Screenshots:

```bash
grim /home/carneiro/Documents/nuvio-worktrees/logs/shot.png
```

Compare frames with:

```bash
magick compare -metric AE before.png after.png diff.png
```

### Manual acceptance checks

1. Home opens with content focus or a usable first target.
2. Tab reaches a ringed category.
3. Right from category lands on a ringed, actionable settings row.
4. Down/Up move between rows; no wrap at edges.
5. Left returns to the category rail.
6. Enter opens a subpage.
7. Esc returns to the parent Settings page, not Home.
8. Open a dialog: arrows stay inside; background focus does not move.
9. Focus a search/text field: Left/Right move the caret, not focus.
10. Focus a slider: Left/Right adjust the value.
11. Controller D-pad/A/B follow the same paths.
12. Player: Enter/Space toggle, arrows seek/volume as designed.

## Branch / remote policy

- `origin` is `cwrneiro/NuvioDesktop`.
- `upstream` is `NuvioMedia/NuvioDesktop`.
- Never push local work to `upstream`.
- Keep `fix/settings-focus-handoff` based on current `upstream/Dev`.
- Do not delete `pr/focus-nav` or `cwrn/packaging`; they are evidence/backups.
- Before opening a PR, split documentation and issue drafts out of the fix branch
  if they were committed there.
- Prefer one PR per bug issue.

## Suggested next steps

1. Confirm upstream `Dev` is still `c0eb7bfa7` or rebase.
2. Push `fix/settings-focus-handoff` to `origin`.
3. Open the two issue drafts in `docs/issues/`.
4. Open focused PRs against `NuvioMedia:Dev` linking those issues.
5. Then port modal + text/slider + anchor fixes as separate PRs.
6. Add automated tests as part of each port; do not ship another large untested
   navigation rewrite.

## Issue and PR drafts

- `docs/issues/settings-category-focus-handoff.md`
- `docs/issues/settings-escape-hierarchy.md`
- `docs/pr/settings-focus-fixes.md`

## Reference commits

- Upstream #945 merge: `45e5ec91a`
- Fix branch base: `c0eb7bfa7`
- Settings focus handoff fix: `c95e70356`
- Escape hierarchy fix: `6d31b3d35`
