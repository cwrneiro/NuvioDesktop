# NuvioDesktop — cwrneirOS fork

Fork of [NuvioMedia/NuvioDesktop](https://github.com/NuvioMedia/NuvioDesktop)
carrying the **controller / focus-navigation** work for the
[cwrneirOS](../../) TV-shell appliance. It is used in two ways:

- **`cwrn/packaging`** — the cwrneirOS integration branch (app changes + Nix
  packaging + cwrneirOS-side docs), consumed by cwrneirOS as a pinned flake
  input.
- **`pr/focus-nav`** — a clean, app-only branch intended to open the upstream
  PR from. It contains **no** packaging, cwrneirOS docs, or cwrneirOS-specific
  tweaks — only generalizable Compose focus/navigation changes.

The app-side focus/arrow-navigation implementation, its packaging, the
focus-contract tooling, the design plans and the field findings all live here.
The OS-side integration (session, input bridge, shell, app-launcher/exit-to-home
contract) lives in cwrneirOS.

## Branch model

- `Dev`, tags — upstream, untouched (the fork's default branch). `upstream`
  remote points at `NuvioMedia/NuvioDesktop`; `origin` is this fork.
- `cwrn/packaging` — cwrneirOS integration. Base: upstream `Dev` @
  **`d4bdf258`** (post-`0.1.27-alpha`; includes the merged upstream PR #791
  desktop back handling). On top: the focus work as **17 commits** (see below),
  plus the Nix packaging (`nix/`, `flake.nix`) and the cwrneirOS-side docs
  (`docs/adr-nuvio.md`, `docs/state-nuvio.md`, `docs/plans/`,
  `docs/patch-history/`). cwrneirOS pins this branch's commit as `inputs.nuvio`.
- `pr/focus-nav` — clean upstream-PR branch off `Dev`: the same focus work
  consolidated into a few upstream-oriented commits, app-only.

## What the packaging branch adds

The upstream desktop build has no in-app controller navigation: D-pad/arrow
keys do nothing. The work adds arrow-key focus navigation (upstream PR #180,
rebased), then iterates on the focus graph and focus-ring visibility across the
desktop surfaces. The packaging branch carries 17 commits, in order:

| # | commit subject | source patch (archived) |
|---|---|---|
| 1 | nav: rebase upstream PR #180 arrow navigation onto Dev | `nuvio-arrow-nav-0.1.27.patch` |
| 2 | nav: drop redundant focusable() double focus nodes | `nuvio-arrow-nav-fixes.patch` |
| 3 | focus: outset hasFocus rings on desktop surfaces | `nuvio-focus-ring.patch` |
| 4 | focus: never consume arrows while a text field has focus | `nuvio-text-input-tracker.patch` |
| 5 | focus: tab retention, isFocused tracker, primitive rings, diagnostics | `nuvio-focus-hardening.patch` |
| 6 | focus: explicit focus graph + recovery anchor | `nuvio-focus-round3.patch` |
| 7 | focus: rail corridor + concrete content targets | `nuvio-focus-round4.patch` |
| 8 | focus: profile close, avatar edges, per-tab rail entries | `nuvio-focus-round4b.patch` |
| 9 | focus: subpage entry, Settings-tab rail isolation, ring polish | `nuvio-focus-round4c.patch` |
| 10 | focus: hero/library rings, switch single-stop, same-frame entry | `nuvio-focus-round4d.patch` |
| 11 | refactor: corridorEdges + diagnostics sink (behavior-neutral) | `nuvio-focus-p0.patch` |
| 12 | focus: inactive-pane opt-out, row rings, single-line URL field | `nuvio-focus-retest7-fixes.patch` |
| 13 | focus: contentEntry fallback, hero dots, header rings, FocusContractTest | `nuvio-focus-item-fixes.patch` |
| 14 | test: rail-corridor walk + Settings subpage-entry test | `nuvio-focus-contract-tests.patch` |
| 15 | focus: rings on ~26 surfaces; Q1 scroll-top, Q2 focusRestorer | `nuvio-focus-ring-coverage.patch` |
| 16 | focus: shelf de-focus, details entry, scroll policy, left=categoryEntry | `nuvio-focus-behavior-fixes.patch` |
| 17 | focus: subpage back Left->categories, CatalogScreen entry | `nuvio-focus-ef-fixes.patch` |

The original per-round patch files are archived under `docs/patch-history/`
for provenance; they are now commits. Prose elsewhere may number patches
"6th/12th" etc. — that count included a since-dropped `nuvio-back-handling-791.patch`,
which upstream absorbed as PR #791 (present in the base commit).

## Build

```sh
nix build .#nuvio-src        # Gradle source build (the one cwrneirOS ships)
nix build .#nuvio            # wrapped upstream AppImage (fallback packaging)
nix build .#nuvio-focus      # X WM_TAKE_FOCUS retrigger helper
nix build .#focus-contract   # journal -> pass/fail focus checker
nix flake check              # cheap checks only (focus/focus-contract)
```

`.#nuvio-src` is built from this working tree (the flake passes `self.outPath`
as `src`); the dependencies are pinned by `nix/deps.json`.

## Focus-contract test

`focus-contract` consumes a journal of the app's `nuvio-nav:` / `nuvio-focus:`
diagnostic lines and enforces the couch focus invariants:

```sh
ssh <target> 'journalctl --user -u cwr-app-nuvio --no-pager -n 30000' \
  | nix run .#focus-contract -- -
```

The headless Compose suite (`desktopTest`: `FocusContractTest`,
`SettingsSubpageEntryTest`, `ArrowKeyNavTest`) runs on the target under
Xvfb + software Skiko. See `docs/plans/nuvio-focus-consolidation.md`.

## Consuming from cwrneirOS

cwrneirOS pins this fork as a flake input and applies the overlay:

```nix
inputs.nuvio.url = "github:cwrneiro/NuvioDesktop/<commit-sha>";
# ...
nixpkgs.overlays = [ /* ... */ nuvio.overlays.default ];
```

The overlay adds `nuvio-src`, `nuvio`, `nuvio-focus`, and `focus-contract` to
nixpkgs (resolved against the consumer's nixpkgs). The OS-side launcher
`/etc/cwr/apps/nuvio.sh` (in cwrneirOS) binds the exit-to-home contract around
`pkgs.nuvio-src`.

## Upstreaming

Pure upstream candidates are called out in the commit bodies. The plan is to
file/rebase the generalizable deltas (arrow-nav #180, focus-ring coverage, the
`shouldConsumeArrowForFocusNav` extraction) as PRs against `Dev`. If #180
lands upstream, this fork can drop `nuvio-src` and ship the upstream package.

## Rebase workflow

```sh
git fetch upstream
git rebase --onto upstream/Dev <old-base> cwrn-focus-nav
```

Re-verify with `nix build .#nuvio-src` and the on-target focus-contract suite.

## License

GPL-3.0-only, inherited from upstream. See `LICENSE`.
