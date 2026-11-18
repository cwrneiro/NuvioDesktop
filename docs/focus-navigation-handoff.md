# Focus navigation handoff

Last updated: 2026-11-18

This document is the entry point for anyone continuing the desktop keyboard and
remote focus-navigation work. Read it before touching focus, arrow-key routing,
modal layers, or controller navigation.

## Read this first

- Main worktree: `/home/carneiro/Documents/NuvioDesktop` on `Dev`.
- Fix worktree for the open Escape PR:
  `/home/carneiro/Documents/nuvio-worktrees/settings-focus-handoff` on
  `fix/settings-escape-hierarchy`.
- Docs worktree: `/home/carneiro/Documents/nuvio-worktrees/focus-handoff-docs`
  on `docs/focus-nav-handoff`.
- Evidence lives outside git in
  `/home/carneiro/Documents/NuvioDesktop/.local/focus-nav`.
- Never use `/tmp` for worktrees or evidence.
- Current target is upstream `Dev` with #945's `ControllerFocusEngine`, not the
  old `pr/focus-nav` branch.
- Do not merge the old Compose-only focus implementation wholesale. Port
  individual correctness fixes.
- Build with the memory-capped command in [Testing runbook](#testing-runbook).
- Commit every patch immediately.

## Current state

### Upstream

- Upstream repo: `NuvioMedia/NuvioDesktop`
- `Dev` head at last fetch: `029fcf0de`
  - `Merge pull request #1160 from shinjuku712/perf/linux-async-overlay-add`
  - 2026-11-18
- PR #945 (`feat(desktop): keyboard/gamepad navigation with focus rings`) merged
  as `45e5ec91a` on 2026-11-17.
- PR #1164 (`fix(settings): hand off focus from categories to content`) merged as
  `24cd30eda` on 2026-11-18.
- PR #1165 (`fix(desktop): route Escape through Compose back handlers`) is open
  on head `7d8bbb0c1`.
- PR #180 (`Keyboard Arrow Navigation`) is closed and unmerged. #945 superseded
  it.

### Local branches

| Branch | State | Purpose |
|---|---|---|
| `Dev` | tracks `upstream/Dev`, currently `029fcf0de` | Main development branch |
| `docs/focus-nav-handoff` | rebased onto `Dev` | Canonical handoff and subtitle docs |
| `fix/settings-escape-hierarchy` | frozen at `7d8bbb0c1` | Open PR #1165 |

Old branches were archived as tags before deletion. They are not checked out.

| Archived branch | Tip | Tag |
|---|---|---|
| `pr/focus-nav` | `427647d1b` | `archive/pr-focus-nav-427647d1b` |
| `cwrn/packaging` | `1b20e8afb` | `archive/cwrn-packaging-1b20e8afb` |
| `fix/settings-focus-handoff` | `6d31b3d35` | `archive/fix-settings-focus-handoff-6d31b3d35` |
| `docs/focus-nav-handoff` before rebase | `a6b9d7e4f` | `archive/docs-focus-nav-handoff-a6b9d7e4f` |

`fix/settings-category-focus-handoff` was merged through #1164. Its local branch
was deleted, and GitHub had already removed the remote branch.

### Worktrees

| Path | Branch | Purpose |
|---|---|---|
| `/home/carneiro/Documents/NuvioDesktop` | `Dev` | Main development |
| `/home/carneiro/Documents/nuvio-worktrees/settings-focus-handoff` | `fix/settings-escape-hierarchy` | Open PR #1165 |
| `/home/carneiro/Documents/nuvio-worktrees/focus-handoff-docs` | `docs/focus-nav-handoff` | This doc |

The detached `before-focus-c0eb7bfa7` worktree was removed after the pre-fix
evidence was captured.

### Focus work status

- #1164 and #1171: Settings category and content focus handoff. Merged and
  closed.
- #1165 and #1172: Escape from a Settings subpage. Open.
- #1162 and #1163 were closed by the unlabeled-issue workflow before the bug form
  was used correctly. They were replaced by #1171 and #1172.

## Architecture comparison

### Current `Dev` / #945

- Custom `ControllerFocusEngine` (~2,650 lines, ported from NuvioDeck, GPLv3).
- Instrumented screens use `Modifier.controllerFocusable`; other screens fall
  back to Compose focus.
- Full controller stack: XInput on Windows, SDL2 via JNA on Linux/macOS, repeat,
  deadzones, multiple pads, hot-plug.
- Controller sounds, hint toolbar, virtual keyboard.
- Custom visual focus treatment: scale, sheen, sibling dimming, z-order.
- Sophisticated scroll centering and route focus memory.

### Old implementation, archived

- Compose focus everywhere, driven by an app-level AWT `KeyEventDispatcher`.
- `FocusCorridor` plus explicit `focusProperties` edges instead of geometric
  search.
- `PlatformModalState`, `NuvioModalDialog`, seeded modal focus, modal-internal
  arrow handling, and NaN-rect recovery anchors.
- Compose text-field and slider trackers so arrows keep caret and thumb
  semantics.
- Contract tests and a real-window AWT modal test.
- No gamepad, sounds, hint toolbar, or route focus memory.

### Practical conclusion

They cannot be merged blindly. Both claim the app-level arrow dispatcher. The
sane path is:

1. Keep #945's engine, input, scroll, and hints.
2. Port our correctness layer on top:
   - modal ownership
   - text and slider arrow semantics
   - recovery anchors and focus-presence tracking
   - remaining explicit rail and content edges
   - regression tests

## Verified gaps

### Bug 1: Settings category and content focus handoff

Reproduced on `c0eb7bfa7`, Linux Hyprland/Wayland, fullscreen:

1. Open Settings.
2. Tab to focus `You`.
3. Right.

Observed:

- Right moved to `Look` inside the category rail, or cleared the ring entirely.
- No content row (`Appearance` or `Layout`) received focus.
- Enter did nothing while focus was invisible.
- Further arrows no-oped or wrapped unexpectedly, for example back to `You`.
- From `System` or `Media`, Right cleared the ring and Up produced zero visual
  change.

Root cause:

- `nuvioContentEntry()` existed in `NuvioFocusable.kt` but was unused in `Dev`.
- Settings category items had no explicit `Right` target into the content pane.
- Compose fell back to geometric search and could land on an invisible or
  non-actionable target.
- There was no recovery anchor equivalent in current `Dev`.

Fix: `c95e70356`, merged as `24cd30eda` through #1164.

- Add `ContentEntry.requester` as the category `Right` target.
- Add `nuvioContentEntry()` to the Settings content root.
- Stop category wrap with `up = Cancel` on the first item and `down = Cancel` on
  the last.
- Let `SettingsSidebarItem` accept a modifier.

Verified after fix:

- Tab focuses `You`.
- Right focuses `Appearance`.
- Down focuses `Layout`.
- Left returns to the `Look` category.
- No dead focus, no wrap.

### Bug 2: Escape from a Settings subpage

Reproduced on `c0eb7bfa7`:

1. Open Settings and select Look.
2. Focus `Appearance`, press Enter.
3. Press Esc once.

Observed:

- Returned to Home instead of the Look page in Settings.
- With a naive back-handler stack, Escape did nothing at all.

Root cause:

- The AWT Escape shortcut bypassed Compose's `NavigationBackHandler` ordering and
  called app-level navigation back directly.
- Settings subpage handlers never ran.

Fix: `6d31b3d35`, open as #1165.

- Route Escape through Compose's navigation back dispatcher using
  `LocalNavigationEventDispatcherOwner` and `DirectNavigationEventInput`.
- Only fall back to the app-level back handler when no Compose handler is enabled.
- Gamepad B uses the same `DesktopEscapeShortcut.dispatch()` path.

Verified after fix:

- Enter on Appearance opens the subpage.
- Esc returns to the Look page in Settings, not Home.

## Evidence

Persistent local evidence lives in `.local/focus-nav`, which is ignored through
`.git/info/exclude`.

- `screenshots/`: `focus-seq.png`, `before-04-right.png`, `after-04-right.png`,
  `06-appearance.png`, `07-esc.png`, and the rest of the before and after
  sequence.
- `logs/`: `compile.log`, `run-before.log`, `run-after.log`, `run-focus.log`.
- `drafts/`: issue and PR body drafts, including the final PR text.
- `archive/`: old `docs/focus-navigation.md` and the superseded issue and PR
  drafts.

The pre-fix baseline was recaptured on `c0eb7bfa7` in fullscreen at 1920x1080.

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
   - `Left` and `Right` belong to the caret or thumb. `Up` and `Down` stay with
     multiline fields but traverse in single-line fields.
   - #945 still documents this as a known limitation.
   - If an engine text-input node is focused, hand focus to the real Compose
     field before applying caret semantics.

3. **Recovery anchor and focus presence**
   - Old files: `PlatformFocusPresence.kt`, `FocusRetry.kt`.
   - Root anchor has an all-NaN `focusRect`. When nothing else holds focus, park
     there so hardware Back and Esc still work.
   - Before retrying a failed directional move, clear the anchor so 2D search is
     not poisoned.
   - `FocusAnchorContractTest.kt` pins this CMP behavior.

4. **Remaining explicit focus graph edges**
   - Old files: `FocusCorridor.kt`, `SettingsFocusLinks.kt`.
   - Settings category and content is done in #1164.
   - Still needed: rail and content, subpage back button and content.
   - Do not port the whole corridor system blindly. Use it where current `Dev`
     still delegates to Compose focus.

5. **Tests**
   - `FocusContractTest.kt`: ringed and actionable landing, rail corridor walk.
   - `FocusAnchorContractTest.kt`: NaN anchor semantics.
   - `SettingsSubpageEntryTest.kt`: subpage entry lands on a real control.
   - `AwtDispatcherModalFocusTest.kt`: real windows and real AWT events.
   - Tracker unit tests.
   - Adapt to the current engine where needed. #945 added no tests.

6. **Only after the above**: consider unifying visual focus rings or controller
   behavior. Do not mix the custom engine and our old dispatcher without a single
   owner for arrow events.

## #945 pitfalls and dead code

- `nuvioContentEntry()` is now used by #1164.
- `FocusPresence`, `NavRailFocus`, `TopBarFocus`, `ContentEntry`, and `RailEntry`
  are still partially or entirely unwired.
- Settings now has explicit category and content edges. Rail and content still
  need the same treatment.
- `DesktopEscapeShortcut` ordering is fixed by #1165, pending merge.
- No regression tests were added by #945.
- Text-field caret behavior remains a documented known limitation.

## Testing runbook

### Environment

- Arch Linux, Hyprland/Wayland, 1920x1080
- Hyprland 0.57.2
- JDK 17: `/usr/lib/jvm/java-17-openjdk`
- System `mpv` package already includes `/usr/include/mpv/client.h`
- `local.properties` must exist in the worktree. An empty file is sufficient.

### Why the native bridge failed once

The shell resolves `c++` to Nix's GCC wrapper, which does not see `/usr/include`.
Prefix `PATH` with system directories so `c++` and `pkg-config` are Arch's:

```bash
PATH=/usr/bin:/bin:/usr/local/bin:$PATH
```

### Build with memory caps

Do not use the default 12 GiB Gradle heap. It can exhaust this 15 GiB machine
when combined with Kotlin and the app. From the main worktree:

```bash
cd /home/carneiro/Documents/NuvioDesktop
PATH=/usr/bin:/bin:/usr/local/bin:$PATH \
JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
GRADLE_OPTS='-Xmx1g' \
./gradlew --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs='-Xmx4g -XX:MaxMetaspaceSize=1g' \
  -Pkotlin.compiler.execution.strategy=in-process \
  :composeApp:compileKotlinDesktop
```

Use the same command in
`/home/carneiro/Documents/nuvio-worktrees/settings-focus-handoff` when working
on #1165.

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
- Use `ydotool` keybinds to switch workspaces or fullscreen.
- `ydotool mousemove -a` was unreliable. Use relative movement and verify with
  `hyprctl cursorpos` after each step.
- Clicking the app may move it to the active workspace. Verify the active window.
- Stop the app with a bracket pattern so the kill command does not match itself:

```bash
for p in $(pgrep -f 'com[.]nuvio[.]app[.]MainKt'); do kill "$p"; done
```

Screenshots:

```bash
grim /home/carneiro/Documents/NuvioDesktop/.local/focus-nav/screenshots/shot.png
```

Compare frames with:

```bash
magick compare -metric AE before.png after.png diff.png
```

### Manual acceptance checks

1. Home opens with content focus or a usable first target.
2. Tab reaches a ringed category.
3. Right from category lands on a ringed, actionable settings row.
4. Down and Up move between rows. No wrap at edges.
5. Left returns to the category rail.
6. Enter opens a subpage.
7. Esc returns to the parent Settings page, not Home.
8. Open a dialog: arrows stay inside and background focus does not move.
9. Focus a search or text field: Left and Right move the caret, not focus.
10. Focus a slider: Left and Right adjust the value.
11. Controller D-pad, A, and B follow the same paths.
12. Player: Enter or Space toggles, arrows seek and adjust volume as designed.

## Branch and remote policy

- `origin` is `cwrneiro/NuvioDesktop`.
- `upstream` is `NuvioMedia/NuvioDesktop`.
- Never push local work to `upstream`.
- `Dev` tracks `upstream/Dev`. `origin/Dev` was fast-forwarded to the same head.
- Archive tags are the backup for deleted branches. Do not recreate the old
  branches.
- Keep `fix/settings-escape-hierarchy` frozen until #1165 merges.
- After #1165 merges, delete its local and remote branch and remove the settings
  worktree.
- Split documentation and issue drafts out of fix branches before opening PRs.
- Prefer one PR per bug issue.

## Suggested next steps

1. Wait for review and merge of #1165.
2. After #1165 merges, delete `fix/settings-escape-hierarchy` locally and on
   origin, remove the settings worktree, and confirm #1172 closes.
3. Port modal ownership, text and slider semantics, the recovery anchor, and the
   remaining focus edges as separate PRs.
4. Add automated tests with each port. Do not ship another large untested
   navigation rewrite.
5. Keep this handoff and the `.local/focus-nav` evidence current.

## Published issues and PRs

- Issue #1171 (category and content focus handoff), closed as completed by
  #1164:
  https://github.com/NuvioMedia/NuvioDesktop/issues/1171
- Issue #1172 (Escape subpage hierarchy), open, fixed by #1165:
  https://github.com/NuvioMedia/NuvioDesktop/issues/1172
- PR #1164 (category handoff), merged as `24cd30eda`:
  https://github.com/NuvioMedia/NuvioDesktop/pull/1164
- PR #1165 (Escape hierarchy), open on `7d8bbb0c1`:
  https://github.com/NuvioMedia/NuvioDesktop/pull/1165

Earlier issues #1162 and #1163 were closed by the unlabeled-issue workflow
because they were created through the API and the `bug` label did not apply.
They are kept for history and point at #1171 and #1172 in their comments.

### Creating a bug issue with the form

`gh issue create --template` does not see YAML issue forms, and `--label bug` is
silently dropped for accounts without triage access. Use GraphQL `createIssue`
with `issueTemplate: "Desktop bug report"` in the input. GitHub then applies the
form's `bug` label. The mutation needs `repositoryId`, `title`, `body`, and
`issueTemplate`.

## Reference commits

- Upstream `Dev` snapshot: `029fcf0de`
- Upstream #945 merge: `45e5ec91a`
- Upstream #1164 merge: `24cd30eda`
- Settings focus handoff fix: `c95e70356`
- Escape hierarchy fix: `6d31b3d35`
- Docs backup before rebase: `a6b9d7e4f`
