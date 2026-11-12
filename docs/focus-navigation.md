# Keyboard / Remote (D-pad) Navigation

Desktop builds support full arrow-key navigation aimed at keyboards, gamepads
and TV-style remotes. This document records the architecture and the invariants
that surfaces must follow when they add interactive elements.

## Dispatch order

Arrow keys are intercepted by an AWT `KeyEventDispatcher` installed in
`PlatformKeyboardNavigation.desktop.kt`. On each `KEY_PRESSED` it applies these
gates in order, returning early when a gate applies:

1. **Modal gate** — while any modal layer is open, the dispatcher stands down
   (`PlatformModalState`); the layer moves focus itself (see below). Without
   this gate the dispatcher would move focus *behind* the modal, because its
   focus manager belongs to the main window composition.
2. **Window scope** — only events belonging to the main window are handled
   (`LocalNuvioMainWindow`, provided by `Main.kt`). Dialogs, popups and the PiP
   player keep their own keys.
3. **AWT arrow consumers** — Swing text components and sliders keep arrows.
4. **Text / slider semantics** — `shouldConsumeArrowForFocusNav` keeps
   Left/Right with the text caret or slider thumb; Up/Down traverse unless a
   multiline text field owns them.
5. Otherwise `FocusManager.moveFocus(direction)` runs — Compose desktop does
   **not** traverse focus on arrow keys by default.

Directional edges between sections are declared with `focusProperties` corridor
helpers (`FocusCorridor.kt`, `SettingsFocusLinks.kt`, per-screen requesters).
Inside lists and grids, default 2D search is used.

## Modal layers

A modal layer must do three things, or arrows either leak to the background or
do nothing inside the modal:

1. **Register**: `NuvioModalRegistration()` for the lifetime of the layer's
   content. `DialogSurface`/`NuvioModalDialog` do this for dialogs; sheets,
   menus and focusable popups add it themselves (see `Menu.kt`,
   `BottomSheet.kt`, `StreamsScreen.kt`, `ProfileSwitcherTab.kt`).
2. **Handle arrows**: `Modifier.nuvioModalArrowNav()` on the layer content root,
   desktop-only via expect/actual (Android/iOS keep native D-pad behavior).
3. **Seed focus**: `nuvioModalFocusTarget(layerFocus, ...)` +
   `NuvioModalFocusSeed(layerFocus)` on open. Opening a modal does not move
   focus by itself; until focus enters the layer, the layer's key handler is not
   on the dispatch path and arrows appear dead.

Once seeded, the layer container must stop being a directional candidate
(NaN focus rect, the same mechanism as the recovery anchor in
`MainAppContent.kt`). A plain focusable container spans the whole modal and
swallows every directional move.

## Testing

- Contract tests (headless): `FocusContractTest`, `SettingsSubpageEntryTest`,
  `FocusAnchorContractTest`, `FocusTrackerWiringTest`, and the
  `ArrowKeyNavTest` truth table.
- Hardware suite (real windows, real AWT key events, needs a display):

  ```bash
  NUVIO_FOCUS_HW_TEST=1 ./gradlew :composeApp:desktopTest --tests "*AwtDispatcherModalFocusTest*"
  ```

  It is skipped without the env var (safe in CI). Run it for any change to the
  AWT dispatcher, modal layers or key routing.

## Pitfalls learned the hard way

- Compose desktop layer topology is **version-specific**: on the current CMP
  version `BasicAlertDialog`, `DropdownMenu` and `ModalBottomSheet` render
  in-scene rather than as separate AWT windows. Window-based scoping alone
  cannot isolate them — hence the modal gate. Verify on hardware instead of
  inferring from changelogs.
- The NaN-focus-rect anchor relies on undocumented search semantics; if
  `FocusAnchorContractTest` fails after a Compose upgrade, the anchor needs
  revisiting rather than the test.
- The dispatcher consumes arrows even when no move is possible (documented
  swallow-on-fail choice); failing moves must not fall through to scroll or
  other handlers inconsistently.
