package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester

/** iOS keeps its native focus behavior inside dialogs. */
internal actual fun Modifier.nuvioModalArrowNav(): Modifier = this

/** Native platforms keep their own focus behavior; no seeding. */
internal actual fun Modifier.nuvioModalFocusTarget(
    layerFocus: FocusRequester,
    directionalTargetEnabled: Boolean,
): Modifier = this

@Composable
internal actual fun NuvioModalFocusSeed(layerFocus: FocusRequester, onSeeded: () -> Unit) {
}
