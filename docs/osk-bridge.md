# External-app OSK bridge (cwrneirOS V1.4 3b)

The cwrneirOS TV shell is a sessionless Quickshell client. It cannot observe a
client's text-field focus: Quickshell 0.3.1 has no text-input/IME service and
sway exposes only window-level focus. The app therefore asks the shell to open
its OSK when a tracked Compose text field gains focus.

## Contract

- **Trigger.** The tracker observes `onPreviewKeyEvent` on each tracked field.
  When that field is focused and the user presses A (Enter/NumPadEnter), it
  spawns `/run/current-system/sw/bin/cwr-osk-show nuvio` fire-and-forget. D-pad
  focus alone does not open the keyboard. The helper is absent on
  non-appliance desktops; the failure is ignored.
- **Shell.** `tvshell oskExternal <label>` maps a bottom-only OSK over the app
  (the main shell stays hidden, so the text field remains visible). On Done it
  unmaps the keyboard and pipes the buffer to `cwr-osk-type`
  (`ydotool type -f -`). `wtype` was rejected on hardware: its events never
  reached AWT; uinput does. Cancel returns to the app without sending. Text is
  never in argv or the journal (length only).
- **Cooldown.** The shell ignores re-open requests for a short window after a
  commit or cancel. When the shell unmaps, Nuvio may report the same field as
  a new focus edge; without the cooldown that would re-open the OSK in a loop.

## Patch

The full change is archived in
[`patch-history/nuvio-osk-bridge.patch`](patch-history/nuvio-osk-bridge.patch).

## Verification

OS-side gate and steps live in cwrneirOS
`runbooks/validation.md` (s18 follow-up b). The fork commit must be pushed and
the cwrneirOS flake pin advanced before the pinned app auto-requests the OSK.
