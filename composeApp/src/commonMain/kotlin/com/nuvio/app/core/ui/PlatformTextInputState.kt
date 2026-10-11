package com.nuvio.app.core.ui

/**
 * cwrneirOS: commonMain-readable mirror of the desktop text-field focus gate.
 * Written by the desktop tracker on the AWT EDT and read lazily at key-event
 * time (same thread), so plain vars suffice. Stays false on Android/iOS.
 */
internal object PlatformTextInputState {
    var isActive: Boolean = false
    var isMultilineActive: Boolean = false
}
