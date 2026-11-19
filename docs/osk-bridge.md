# External-app OSK bridge (cwrneirOS V1.4 3b)

The cwrneirOS TV shell is a sessionless Quickshell client. It cannot observe a
client's text-field focus: Quickshell 0.3.1 has no text-input/IME service and
sway exposes only window-level focus. The app therefore asks the shell to open
its OSK when a tracked Compose text field gains focus.

## Contract

- **Trigger.** `DesktopTextInputState.syncMirror` runs on the desktop text
  tracker's focus edges. On the 0 → 1 edge it spawns
  `/run/current-system/sw/bin/cwr-osk-show nuvio` fire-and-forget. The helper
  is absent on non-appliance desktops; the failure is ignored.
- **Shell.** `tvshell oskExternal <label>` shows the shell's OSK over the app.
  On Done it unmaps the shell so sway returns keyboard focus to Nuvio, then
  pipes the buffer to `cwr-osk-type` (`wtype -s 250 -`). Cancel returns to the
  app without sending. Text is never in argv or the journal (length only).
- **Cooldown.** The shell ignores re-open requests for 5 s after a commit or
  cancel. When the shell unmaps, Nuvio may report the same field as a new
  focus edge; without the cooldown that would re-open the OSK in a loop.

## Patch

The full change is archived in
[`patch-history/nuvio-osk-bridge.patch`](patch-history/nuvio-osk-bridge.patch).

## Verification

OS-side gate and steps live in cwrneirOS
`runbooks/validation.md` (s18 follow-up b). The fork commit must be pushed and
the cwrneirOS flake pin advanced before the pinned app auto-requests the OSK.
