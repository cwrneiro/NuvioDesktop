package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier

/**
 * cwrneirOS fix (text-field arrow tracker): marks a text field so the desktop
 * AWT key dispatcher can stop consuming arrow keys while its caret is active
 * (see PlatformKeyboardNavigation.desktop.kt). Apply to the field's own
 * modifier chain; no-op on Android and iOS.
 */
expect fun Modifier.nuvioTracksTextInput(): Modifier
