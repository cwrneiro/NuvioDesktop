package com.nuvio.app.core.ui

import java.util.concurrent.atomic.AtomicInteger

/**
 * cwrneirOS fix (text-field arrow tracker): shared flag consulted by the AWT
 * [java.awt.KeyEventDispatcher] in PlatformKeyboardNavigation.desktop.kt.
 *
 * Compose text fields are not AWT/Swing components, so the dispatcher's
 * `isArrowKeyConsumer()` check cannot see them and used to consume arrow keys
 * before Compose could move the caret. [nuvioTracksTextInput] increments this
 * counter while a field is focused and decrements it on focus loss or disposal.
 *
 * Invariant: `focusedFieldCount` equals the number of live tracker instances
 * currently in the focused state, and `focusedMultilineFieldCount` is the
 * subset of those tracking a multiline field. Every tracker pairs each
 * increment with exactly one decrement (focus loss or disposal, whichever
 * comes first), so neither count goes negative and overlapping focus changes
 * between fields leave them at the number of fields actually focused.
 *
 * Thread model: ComposeWindow composition/apply and AWT key dispatch both run
 * on the AWT event dispatch thread, so reads and writes are already
 * serialized. The atomics keep the counters correct (and visible) if a popup
 * composition or a future non-EDT caller ever reports state from another
 * thread. Both counters are mirrored into the commonMain-readable
 * [PlatformTextInputState] on every change.
 */
internal object DesktopTextInputState {
    private val focusedFieldCount = AtomicInteger(0)
    private val focusedMultilineFieldCount = AtomicInteger(0)

    /** True while at least one tracked Compose text field holds focus. */
    val isActive: Boolean
        get() = focusedFieldCount.get() > 0

    /** True while at least one tracked multiline Compose text field holds focus. */
    val isMultilineActive: Boolean
        get() = focusedMultilineFieldCount.get() > 0

    fun onFieldFocusGained(multiline: Boolean) {
        focusedFieldCount.incrementAndGet()
        if (multiline) focusedMultilineFieldCount.incrementAndGet()
        syncMirror()
    }

    fun onFieldFocusLost(multiline: Boolean) {
        focusedFieldCount.decrementAndGet()
        if (multiline) focusedMultilineFieldCount.decrementAndGet()
        syncMirror()
    }

    private fun syncMirror() {
        PlatformTextInputState.isActive = isActive
        PlatformTextInputState.isMultilineActive = isMultilineActive
    }
}
