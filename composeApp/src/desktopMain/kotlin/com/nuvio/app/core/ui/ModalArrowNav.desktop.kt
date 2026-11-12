package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Desktop actual: moves focus inside the dialog's own focus owner on arrow
 * keys. Text fields and sliders keep their arrows (caret/thumb semantics) via
 * the same decision function the app-wide dispatcher uses.
 */
internal actual fun Modifier.nuvioModalArrowNav(): Modifier = composed {
    // Resolved inside the dialog's content composition so it targets the
    // dialog's focus owner rather than the main window composition.
    val focusManager = LocalFocusManager.current
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val navKey = event.toNuvioNavKey() ?: return@onPreviewKeyEvent false
        if (!shouldConsumeArrowForFocusNav(
                navKey,
                DesktopTextInputState.isActive,
                DesktopTextInputState.isMultilineActive,
                DesktopSliderState.isActive,
            )
        ) {
            return@onPreviewKeyEvent false
        }
        focusManager.moveFocus(navKey.toFocusDirection()).let { moved ->
            NuvioFocusDiagnostics.log("nuvio-modal-nav: key=$navKey moved=$moved")
        }
        // Consume even when no candidate moved: same swallow-on-fail choice as
        // the app-wide dispatcher, so a failed move cannot fall through to
        // other handlers or scroll containers.
        true
    }
}

internal actual fun Modifier.nuvioModalFocusTarget(
    layerFocus: FocusRequester,
    directionalTargetEnabled: Boolean,
): Modifier = composed {
    this
        .focusRequester(layerFocus)
        .focusGroup()
        .focusProperties {
            // Once seeded, the layer container must never be a directional
            // candidate (it spans the whole modal and would swallow moves);
            // the NaN rect is the same mechanism as the app's recovery anchor.
            if (!directionalTargetEnabled) {
                focusRect = Rect(Float.NaN, Float.NaN, Float.NaN, Float.NaN)
            }
        }
        .focusable()
}

@Composable
internal actual fun NuvioModalFocusSeed(layerFocus: FocusRequester, onSeeded: () -> Unit) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        val seeded = layerFocus.requestFocusWhenPlaced()
        // Hand focus to the layer's first control so the focus ring is visible
        // and arrows work immediately; if Enter finds nothing, focus stays on
        // the (invisible) layer target and the first arrow press hands off.
        val moved = if (seeded) focusManager.moveFocus(FocusDirection.Enter) else false
        NuvioFocusDiagnostics.log("nuvio-modal-seed: seeded=$seeded moved=$moved")
        if (moved) onSeeded()
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.toNuvioNavKey(): NuvioNavKey? = when (key) {
    Key.DirectionLeft -> NuvioNavKey.Left
    Key.DirectionRight -> NuvioNavKey.Right
    Key.DirectionUp -> NuvioNavKey.Up
    Key.DirectionDown -> NuvioNavKey.Down
    else -> null
}
