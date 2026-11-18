# [Bug]: Esc from a Settings subpage returns to Home instead of the parent Settings page

## App version

0.1.37-alpha (37), current `Dev` (`c0eb7bfa7`, includes #945).

## Platform

Linux, Hyprland/Wayland, 1920x1080, fullscreen, built from source.

## Steps to reproduce

1. Open Settings → Look.
2. Focus Appearance and press Enter.
3. Press Esc once.

## Expected behavior

Return to Settings → Look, the parent page.

## Actual behavior

Escape exits Settings entirely and returns to Home instead of popping one Settings
level.

## Root cause

The desktop AWT Escape shortcut bypasses Compose's `NavigationBackHandler` ordering
and calls the app-level navigation back directly. Screen-level back handlers,
including the Settings subpage handler, never run.

## Proposed fix

Route Escape through Compose's navigation back dispatcher using
`LocalNavigationEventDispatcherOwner` + `DirectNavigationEventInput`, then fall
back to the existing app-level navigation only when no Compose handler is enabled.
I have a patch ready.
