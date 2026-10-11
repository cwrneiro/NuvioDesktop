package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier

/**
 * cwrneirOS fix (text-field arrow tracker): marks a text field so the desktop
 * AWT key dispatcher can stop consuming arrow keys while its caret is active
 * (see PlatformKeyboardNavigation.desktop.kt). Apply to the field's own
 * modifier chain; no-op on Android and iOS.
 *
 * [singleLine] must mirror the field's own `singleLine` flag: in a multiline
 * field (singleLine = false) Up/Down move the caret, so the dispatcher must
 * also preserve those keys; in a single-line field they move nothing and stay
 * available for focus traversal.
 */
expect fun Modifier.nuvioTracksTextInput(singleLine: Boolean = true): Modifier
