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
 * currently in the focused state. Every tracker pairs each increment with
 * exactly one decrement (focus loss or disposal, whichever comes first), so
 * the count never goes negative and overlapping focus changes between fields
 * leave it at the number of fields actually focused.
 *
 * Thread model: ComposeWindow composition/apply and AWT key dispatch both run
 * on the AWT event dispatch thread, so reads and writes are already
 * serialized. The atomic keeps the counter correct (and visible) if a popup
 * composition or a future non-EDT caller ever reports state from another
 * thread.
 */
internal object DesktopTextInputState {
    private val focusedFieldCount = AtomicInteger(0)

    /** True while at least one tracked Compose text field holds focus. */
    val isActive: Boolean
        get() = focusedFieldCount.get() > 0

    fun onFieldFocusGained() {
        focusedFieldCount.incrementAndGet()
    }

    fun onFieldFocusLost() {
        focusedFieldCount.decrementAndGet()
    }
}
