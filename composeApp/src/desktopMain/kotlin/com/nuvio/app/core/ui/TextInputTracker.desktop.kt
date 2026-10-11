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
actual fun Modifier.nuvioTracksTextInput(singleLine: Boolean): Modifier = composed {
    val tracker = remember { TextInputFocusTracker(multiline = !singleLine) }
    DisposableEffect(Unit) {
        onDispose { tracker.onDetached() }
    }
    // isFocused (not hasFocus): a focused trailing icon/clear button inside the
    // field must not keep the text-input gate on.
    this.onFocusChanged { tracker.onFocusChanged(it.isFocused) }
}

/**
 * Per-field edge detector keeping the global counters balanced:
 * - a gain fires only on the inactive -> active edge (the initial inactive
 *   callback `onFocusChanged` delivers on attach is ignored);
 * - a loss fires exactly once, either from the focus callback or from
 *   [onDetached] if the field leaves composition while still focused;
 * - [onDetached] is idempotent, so disposal after a loss does not decrement
 *   twice and disposal from a focused state decrements exactly once.
 */
private class TextInputFocusTracker(
    private val multiline: Boolean,
) {
    private var focused = false

    fun onFocusChanged(isFocused: Boolean) {
        if (isFocused == focused) return
        focused = isFocused
        if (isFocused) {
            DesktopTextInputState.onFieldFocusGained(multiline)
        } else {
            DesktopTextInputState.onFieldFocusLost(multiline)
        }
    }

    fun onDetached() {
        if (!focused) return
        focused = false
        DesktopTextInputState.onFieldFocusLost(multiline)
    }
}
