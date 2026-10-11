package com.nuvio.app.core.ui

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// cwrneirOS fix (2026-10-05, hardware feedback on the rebased PR #180): do NOT
// add Modifier.focusable() here. Every element this modifier is applied to is
// already clickable, and clickable() provides its own focusable node — adding
// a second one meant each item had TWO focus targets: arrows stepped through
// both (every other press looked dead / "janky"), focus sometimes landed on
// the bare focusable node where Enter/A has no click semantics ("A doesn't
// always select"), and the visible indication alternated between this ring
// and the item's own subtle focused/hover tint. Observing focus and drawing
// the ring is this modifier's whole job; focusability comes from clickable().
fun Modifier.nuvioFocusBorder(
    shape: Shape,
    borderWidth: Dp = 3.dp,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val tokens = MaterialTheme.nuvio
    this
        .onFocusChanged { focused = it.isFocused }
        .then(
            if (focused) Modifier.border(
                width = borderWidth,
                color = tokens.colors.focusRing,
                shape = shape,
            ) else Modifier,
        )
}
