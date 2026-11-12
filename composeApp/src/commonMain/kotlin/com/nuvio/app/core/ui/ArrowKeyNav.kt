package com.nuvio.app.core.ui

import androidx.compose.ui.focus.FocusDirection

internal enum class NuvioNavKey { Left, Right, Up, Down }

internal fun NuvioNavKey.toFocusDirection(): FocusDirection = when (this) {
    NuvioNavKey.Left -> FocusDirection.Left
    NuvioNavKey.Right -> FocusDirection.Right
    NuvioNavKey.Up -> FocusDirection.Up
    NuvioNavKey.Down -> FocusDirection.Down
}

/**
 * True when an arrow press should be consumed for focus navigation.
 *
 * Left/Right always belong to the caret while any text field is focused, and
 * to the thumb while a slider is focused. Up/Down belong to the caret only in
 * a multiline field; in a single-line field — and for a slider, which shares
 * single-line semantics — they move nothing and must still traverse focus.
 */
internal fun shouldConsumeArrowForFocusNav(
    key: NuvioNavKey,
    textFieldActive: Boolean,
    multilineFieldActive: Boolean,
    sliderActive: Boolean = false,
): Boolean = when (key) {
    NuvioNavKey.Left, NuvioNavKey.Right -> !textFieldActive && !sliderActive
    NuvioNavKey.Up, NuvioNavKey.Down -> !multilineFieldActive
}
