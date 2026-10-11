package com.nuvio.app.core.ui

internal enum class NuvioNavKey { Left, Right, Up, Down }

/**
 * True when an arrow press should be consumed for focus navigation.
 *
 * Left/Right always belong to the caret while any text field is focused.
 * Up/Down belong to the caret only in a multiline field; in a single-line
 * field they move nothing and must still traverse focus.
 */
internal fun shouldConsumeArrowForFocusNav(
    key: NuvioNavKey,
    textFieldActive: Boolean,
    multilineFieldActive: Boolean,
): Boolean = when (key) {
    NuvioNavKey.Left, NuvioNavKey.Right -> !textFieldActive
    NuvioNavKey.Up, NuvioNavKey.Down -> !multilineFieldActive
}
