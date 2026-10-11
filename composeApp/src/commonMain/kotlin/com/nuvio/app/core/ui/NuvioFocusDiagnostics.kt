package com.nuvio.app.core.ui

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/** cwrneirOS: single switch for focus diagnostics. Set enabled = false at closeout. */
internal object NuvioFocusDiagnostics {
    var enabled: Boolean = true
    fun log(message: String) {
        if (enabled) println(message)
    }
}

/** cwrneirOS: semantics marker that a node draws a focus ring (headless-testable). */
val FocusRingSemantics = SemanticsPropertyKey<Boolean>("NuvioFocusRing")
var SemanticsPropertyReceiver.nuvioHasFocusRing by FocusRingSemantics
