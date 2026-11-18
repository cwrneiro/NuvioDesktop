# PR body draft: Settings focus fixes

## Summary

Fixes two desktop keyboard-navigation bugs in the current `Dev` focus implementation:

1. Settings category and content focus handoff.
2. Escape from a Settings subpage returning to Home.

## PR type

- [x] Behavior bug/regression fix

## Why

### Settings category handoff

`nuvioContentEntry()` is defined but unused in current `Dev`. Category items have no
explicit `Right` target, so Compose falls back to geometric search and can land on
an invisible target. Result: Right from a category either stays in the category
rail or clears focus, and subsequent arrows are unreliable.

### Settings subpage Escape

The desktop AWT Escape shortcut bypassed Compose's `NavigationBackHandler` ordering
and called app-level navigation back directly, so the Settings subpage handler never
ran and Escape returned to Home.

## Desktop scope

Linux (verified), desktop-only behavior. Keyboard/remote path; gamepad B uses the
same Escape dispatch path.

## Issue or approval

Fixes the two linked issues:

- Settings category focus handoff
- Settings subpage Escape hierarchy

## UI / behavior impact

- [x] Behavior changed only to fix documented bugs/regressions
- [x] UI changed only to fix documented focus visibility bugs

## Scope boundaries

- No controller input changes.
- No focus-ring redesign.
- No text-field/slider changes in this PR.
- No modal focus changes in this PR.
- No changes to the old `pr/focus-nav` architecture.

## Testing

- Built current `Dev` + these two commits.
- Manually tested on Linux Hyprland/Wayland, fullscreen:
  - Tab focused You.
  - Right moved to Appearance.
  - Down moved to Layout.
  - Left returned to Look.
  - Enter on Appearance opened the subpage.
  - Esc returned to Look in Settings, not Home.
- Native player bridge built successfully.
- Build used memory-capped Gradle settings; no OOM.

## Screenshots / Video

Attach the focus sequence and Escape-return evidence.

## Breaking changes

None.

## Linked issues

- Fixes #<settings-category-focus-handoff-issue>
- Fixes #<settings-escape-hierarchy-issue>
