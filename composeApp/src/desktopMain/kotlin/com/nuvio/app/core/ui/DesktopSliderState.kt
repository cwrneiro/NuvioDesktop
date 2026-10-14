package com.nuvio.app.core.ui

import java.util.concurrent.atomic.AtomicInteger

/**
 * cwrneirOS fix (slider arrow tracker): shared flag consulted by the AWT
 * [java.awt.KeyEventDispatcher] in PlatformKeyboardNavigation.desktop.kt.
 *
 * Compose sliders are not AWT/Swing components, so the dispatcher's
 * `isArrowKeyConsumer()` check cannot see them and used to consume Left/
 * Right before Compose could adjust the value. [nuvioTracksSlider] increments
 * this counter while a slider is focused and decrements it on focus loss or
 * disposal, with the same idempotent pairing invariant as
 * [DesktopTextInputState].
 *
 * Thread model: same as [DesktopTextInputState] — composition/apply and key
 * dispatch share the AWT EDT; the atomic keeps the counter correct if a
 * non-EDT caller ever reports state from another thread.
 */
internal object DesktopSliderState {
    private val focusedSliderCount = AtomicInteger(0)

    /** True while at least one tracked Compose slider holds focus. */
    val isActive: Boolean
        get() = focusedSliderCount.get() > 0

    fun onSliderFocusGained() {
        focusedSliderCount.incrementAndGet()
    }

    fun onSliderFocusLost() {
        focusedSliderCount.decrementAndGet()
    }
}
