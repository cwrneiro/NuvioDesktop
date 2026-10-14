package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier

/**
 * cwrneirOS fix (slider arrow tracker): marks a Compose Slider so the desktop
 * AWT key dispatcher can stop consuming Left/Right while the slider is
 * focused (see PlatformKeyboardNavigation.desktop.kt). Apply to the slider's
 * own modifier chain; no-op on Android and iOS.
 *
 * Slider semantics match a single-line text field: Left/Right adjust the
 * value, Up/Down move nothing and stay available for focus traversal.
 */
expect fun Modifier.nuvioTracksSlider(): Modifier
