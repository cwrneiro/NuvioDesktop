package com.nuvio.app.core.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

/**
 * cwrneirOS fix (slider arrow tracker): desktop actual. Registers a focus
 * tracker on the slider's modifier chain so [DesktopSliderState.isActive] is
 * true while the slider owns Left/Right for value adjustments.
 */
actual fun Modifier.nuvioTracksSlider(): Modifier = composed {
    val tracker = remember { SliderFocusTracker() }
    DisposableEffect(Unit) {
        onDispose { tracker.onDetached() }
    }
    this.onFocusChanged { tracker.onFocusChanged(it.isFocused) }
}

/**
 * Per-slider edge detector keeping the global counter balanced: same
 * idempotent gain/loss/detach contract as the text tracker (see the
 * invariant note in DesktopTextInputState.kt).
 */
private class SliderFocusTracker {
    private var focused = false

    fun onFocusChanged(isFocused: Boolean) {
        if (isFocused == focused) return
        focused = isFocused
        if (isFocused) {
            DesktopSliderState.onSliderFocusGained()
        } else {
            DesktopSliderState.onSliderFocusLost()
        }
    }

    fun onDetached() {
        if (!focused) return
        focused = false
        DesktopSliderState.onSliderFocusLost()
    }
}
