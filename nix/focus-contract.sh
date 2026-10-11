#!/usr/bin/env bash
# cwrneirOS focus-contract checker.
#
# Turns a couch/navigation journal into a pass/fail verdict instead of a human
# eyeball. It consumes the app's `nuvio-nav:` / `nuvio-focus:` diagnostic lines
# (see packages/nuvio-src `NuvioFocusDiagnostics`) and enforces the focus
# invariants the couch passes were manually checking.
#
# Usage:
#   focus-contract <journal-file>
#   ssh brook 'journalctl --user -u cwr-app-nuvio --no-pager -n 30000' \
#     | focus-contract -
#
# Hard failures (exit 1):
#   * any `anchor=true` line  (focus landed on the invisible recovery anchor)
#   * any landing tag outside ALLOWED_TAGS
# Warnings (exit 0):
#   * runs of >= DEADEND_RUN consecutive `moved=false` (possible dead-end / lost
#     focus); raise DEADEND_RUN if a test intentionally presses at an edge.
set -uo pipefail

INPUT="${1:-}"
if [[ -z "$INPUT" ]]; then
  echo "usage: $0 <journal-file|->" >&2
  exit 2
fi

ALLOWED_TAGS='^(rail-(avatar|home|search|library|settings)|category-[A-Za-z]+|back-button|content-group|home-hero|library-source-chip|stack-.*|topbar-tab)$'
DEADEND_RUN="${DEADEND_RUN:-5}"

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
if [[ "$INPUT" == "-" ]]; then
  cat > "$tmp"
else
  if [[ ! -f "$INPUT" ]]; then
    echo "focus-contract: no such file: $INPUT" >&2
    exit 2
  fi
  cp "$INPUT" "$tmp"
fi

# Keep only diagnostic lines, strip journal/prefix noise.
grep -aE 'nuvio-nav:|nuvio-focus:' "$tmp" \
  | sed -E 's/^.*nuvio-(nav|focus): /\1: /' > "$tmp.clean" || true

total_lines=$(wc -l < "$tmp.clean" | tr -d ' ')
nav_lines=$(grep -c '^nav: ' "$tmp.clean" || true)
focus_lines=$(grep -c '^focus: ' "$tmp.clean" || true)
anchor_true=$(grep -c 'anchor=true' "$tmp.clean" || true)

echo "focus-contract: $total_lines diagnostic lines (nav=$nav_lines focus=$focus_lines)"
echo

echo "== landings by tag =="
grep -aE '^focus: ' "$tmp.clean" | sed -E 's/^focus: //' | sort | uniq -c | sort -rn

echo
echo "== violations =="
violations=0

if [[ "$anchor_true" -ne 0 ]]; then
  echo "FAIL: $anchor_true line(s) with anchor=true (invisible focus landing)"
  violations=$((violations + 1))
else
  echo "ok: no anchor=true landings"
fi

# Landing tags outside the allow-list. `focus: X` from old Oct-05 sessions is a
# known non-landing format; ignore it.
unexpected="$(grep -aE '^focus: ' "$tmp.clean" | sed -E 's/^focus: //' \
  | sed -E 's/ \*$//' | sort -u | grep -avE "$ALLOWED_TAGS" | grep -av '^X$' || true)"
if [[ -n "$unexpected" ]]; then
  echo "FAIL: unexpected landing tag(s):"
  echo "$unexpected" | sed 's/^/  - /'
  violations=$((violations + 1))
else
  echo "ok: all landing tags are known"
fi

# Dead-end detector: runs of consecutive moved=false.
deadend_max="$(awk '
  /^nav:/ {
    if ($0 ~ /moved=false/) { run++; if (run > max) max = run }
    else { run = 0 }
  }
  END { print (max + 0) }
' "$tmp.clean")"
if [[ "$deadend_max" -ge "$DEADEND_RUN" ]]; then
  echo "WARN: longest consecutive moved=false run = $deadend_max (>= $DEADEND_RUN; possible dead-end — verify it was at a documented edge)"
else
  echo "ok: longest consecutive moved=false run = $deadend_max (< $DEADEND_RUN)"
fi

echo
if [[ "$violations" -eq 0 ]]; then
  echo "focus-contract: PASS"
  exit 0
else
  echo "focus-contract: FAIL ($violations hard violation(s))"
  exit 1
fi
