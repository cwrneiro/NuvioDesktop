package com.nuvio.app.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Couch-legible focus ring, drawn as an OUTSET stroke around the node.
 *
 * cwrneirOS fix (2026-10-05, hardware feedback on the rebased PR #180): do NOT
 * add Modifier.focusable() here. Every element this modifier is applied to is
 * already clickable, and clickable() provides its own focusable node — adding
 * a second one meant each item had TWO focus targets: arrows stepped through
 * both (every other press looked dead / "janky"), focus sometimes landed on
 * the bare focusable node where Enter/A has no click semantics ("A doesn't
 * always select"), and the visible indication alternated between this ring
 * and the item's own subtle focused/hover tint. Observing focus and drawing
 * the ring is this modifier's whole job; focusability comes from clickable().
 * (This rationale is hardware-verified — do not regress it.)
 *
 * Focus is observed via [onFocusChanged] with `hasFocus`, NOT `isFocused`:
 * `isFocused` is true only when this exact node is the focus target, while
 * `hasFocus` is also true for ancestors containing the focused descendant
 * (ActiveParent) — wrappers around clickable/selectable children must ring too.
 *
 * Placement rule: apply this modifier BEFORE any `.clip(...)` / shape clip in
 * the chain. The ring is drawn outside the node bounds (inflated by
 * `borderWidth / 2 + 1dp`), so an ancestor clip earlier (outer) in the chain
 * would cut it — with a tight same-shape clip the whole ring disappears. It
 * must likewise sit before the `clickable`/`selectable`/`combinedClickable`
 * whose focus target it observes (onFocusChanged observes the first focus
 * target AFTER itself).
 */
fun Modifier.nuvioFocusBorder(
    shape: Shape,
    borderWidth: Dp = 3.dp,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val tokens = MaterialTheme.nuvio
    this
        .onFocusChanged { focused = it.hasFocus }
        .drawBehind {
            if (!focused) return@drawBehind
            val inflate = borderWidth.toPx() / 2f + 1.dp.toPx()
            val outline = shape.createOutline(
                Size(size.width + 2 * inflate, size.height + 2 * inflate),
                layoutDirection,
                this,
            )
            translate(-inflate, -inflate) {
                drawOutline(
                    outline = outline,
                    color = tokens.colors.focusRing,
                    style = Stroke(width = borderWidth.toPx()),
                )
            }
        }
}

/**
 * cwrneirOS retest-4b diagnostic: prints when this exact node becomes the focused
 * target. Pull `nuvio-focus:` lines from the unit journal; removed at closeout.
 */
fun Modifier.nuvioDebugFocusTag(label: String): Modifier =
    onFocusChanged { if (it.isFocused) println("nuvio-focus: $label") }

/**
 * cwrneirOS retest-4b diagnostic for focus groups (canFocus=false containers):
 * prints when focus is anywhere inside this subtree.
 */
fun Modifier.nuvioDebugFocusGroupTag(label: String): Modifier =
    onFocusChanged { if (it.hasFocus) println("nuvio-focus: $label *") }
