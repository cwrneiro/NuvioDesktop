# [Bug]: Settings keyboard navigation loses focus between categories and settings rows

## App version

0.1.37-alpha (37), current `Dev` (`c0eb7bfa7`, includes #945).

## Platform

Linux, Hyprland/Wayland, 1920x1080, fullscreen, built from source.

## Steps to reproduce

1. Open Settings.
2. Press Tab until the focus ring is on a category (for example, You).
3. Press Right.
4. Press Down, Enter, then Right again.

## Expected behavior

- Right from a category moves into the settings content and focuses/rings the first row (Appearance).
- Down then moves between rows.
- Left returns to the category list.
- Focus never disappears.

## Actual behavior

- Right from You moves to Look inside the category rail instead of into the content.
- From other categories, Right clears the focus ring and no settings row receives focus.
- Enter does nothing once focus is invisible.
- Further arrows either do nothing or wrap unexpectedly (for example, back to You).
- The category rail and content pane have no reliable directional handoff.

## Root cause

`nuvioContentEntry()` exists in `NuvioFocusable.kt` but is not used anywhere in `Dev`.
Settings category items also have no explicit `Right` focus target into the content
pane, so Compose falls back to geometric focus search and lands on an
invisible/non-actionable target.

## Evidence

Screenshots/video: category focus, Right losing the ring, dead Enter, and the
corrected handoff.

## Proposed fix

Wire `ContentEntry.requester` as the `Right` target for category items and apply
`nuvioContentEntry()` to the settings content root. I have a patch ready.
