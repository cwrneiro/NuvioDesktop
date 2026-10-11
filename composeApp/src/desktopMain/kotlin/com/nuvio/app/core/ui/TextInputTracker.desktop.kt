package com.nuvio.app.core.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

/**
 * cwrneirOS fix (text-field arrow tracker): desktop actual. Registers a focus
 * tracker on the field's modifier chain so [DesktopTextInputState.isActive] is
 * true while the field can move a caret.
 */
actual fun Modifier.nuvioTracksTextInput(): Modifier = composed {
    val tracker = remember { TextInputFocusTracker() }
    DisposableEffect(Unit) {
        onDispose { tracker.onDetached() }
    }
    this.onFocusChanged { tracker.onFocusChanged(it.hasFocus) }
}

/**
 * Per-field edge detector keeping the global counter balanced:
 * - a gain fires only on the inactive -> active edge (the initial inactive
 *   callback `onFocusChanged` delivers on attach is ignored);
 * - a loss fires exactly once, either from the focus callback or from
 *   [onDetached] if the field leaves composition while still focused;
 * - [onDetached] is idempotent, so disposal after a loss does not decrement
 *   twice and disposal from a focused state decrements exactly once.
 */
private class TextInputFocusTracker {
    private var focused = false

    fun onFocusChanged(hasFocus: Boolean) {
        if (hasFocus == focused) return
        focused = hasFocus
        if (hasFocus) {
            DesktopTextInputState.onFieldFocusGained()
        } else {
            DesktopTextInputState.onFieldFocusLost()
        }
    }

    fun onDetached() {
        if (!focused) return
        focused = false
        DesktopTextInputState.onFieldFocusLost()
    }
}
