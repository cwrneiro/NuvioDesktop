package com.nuvio.app.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusTargetModifierNode
import androidx.compose.ui.focus.Focusability
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

/** cwrneirOS: main-scene focus presence + recovery-anchor state (see PlatformKeyboardNavigation). */
internal object PlatformFocusPresence {
    /** True while any focus target in the main scene holds focus (Compose state so effects react). */
    var hasFocus by mutableStateOf(false)
    /** True while the recovery anchor itself is focused (read by the AWT dispatcher). */
    var anchorActive: Boolean = false
}

internal fun Modifier.focusPresenceMonitor(): Modifier = this then FocusPresenceElement

private object FocusPresenceElement : ModifierNodeElement<FocusPresenceNode>() {
    override fun create() = FocusPresenceNode()
    override fun update(node: FocusPresenceNode) {}
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 0x5A17
    override fun InspectorInfo.inspectableProperties() { name = "focusPresenceMonitor" }
}

private class FocusPresenceNode : DelegatingNode() {
    init {
        delegate(
            FocusTargetModifierNode(focusability = Focusability.Never) { _, current ->
                PlatformFocusPresence.hasFocus = current.hasFocus
            },
        )
    }
}
