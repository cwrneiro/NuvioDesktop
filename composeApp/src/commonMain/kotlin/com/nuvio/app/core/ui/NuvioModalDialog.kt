package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

/**
 * Shared modal dialog container. Besides hosting content it:
 *  - marks [PlatformModalState] so the desktop arrow dispatcher stands down
 *    while a dialog owns the keyboard (the dispatcher's focus manager belongs
 *    to the main window, so acting there would move focus behind the modal);
 *  - delegates arrow handling to [nuvioModalArrowNav], which moves focus inside
 *    the dialog's own focus owner on desktop and is a no-op elsewhere so
 *    native D-pad behavior is untouched on Android/iOS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NuvioModalDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    NuvioModalRegistration()
    BasicAlertDialog(onDismissRequest = onDismissRequest, properties = properties) {
        val layerFocus = remember { FocusRequester() }
        val layerSeeded = remember { mutableStateOf(false) }
        NuvioModalFocusSeed(layerFocus) { layerSeeded.value = true }
        Box(
            modifier = Modifier
                .nuvioModalFocusTarget(layerFocus, directionalTargetEnabled = !layerSeeded.value)
                .nuvioModalArrowNav(),
        ) {
            content()
        }
    }
}

/**
 * Marks a modal layer (dialog, sheet, focusable popup/menu) as open for the
 * lifetime of its composition. The desktop arrow dispatcher stands down while
 * any modal layer is registered; the layer's own composition handles arrows via
 * [nuvioModalArrowNav].
 */
@Composable
internal fun NuvioModalRegistration() {
    DisposableEffect(Unit) {
        PlatformModalState.openDialogs += 1
        NuvioFocusDiagnostics.log("nuvio-modal: open count=${PlatformModalState.openDialogs}")
        onDispose {
            PlatformModalState.openDialogs -= 1
            NuvioFocusDiagnostics.log("nuvio-modal: close count=${PlatformModalState.openDialogs}")
        }
    }
}

/**
 * Makes the layer's content root focusable on desktop so the layer's own focus
 * owner can receive focus when it opens. No-op on Android/iOS, which keep their
 * native behavior.
 */
internal expect fun Modifier.nuvioModalFocusTarget(
    layerFocus: androidx.compose.ui.focus.FocusRequester,
    directionalTargetEnabled: Boolean,
): Modifier

/**
 * Desktop: requests focus for the just-opened modal layer and hands it to the
 * layer's first control. Without this the previously focused node (behind the
 * modal) keeps the scene's focus, so the layer's arrow handler never sees key
 * events. No-op on Android/iOS.
 */
@Composable
internal expect fun NuvioModalFocusSeed(
    layerFocus: androidx.compose.ui.focus.FocusRequester,
    onSeeded: () -> Unit,
)

/**
 * Arrow-key focus traversal for dialog content, scoped to the dialog's focus
 * owner. Desktop implements it; other platforms keep their native behavior.
 */
internal expect fun Modifier.nuvioModalArrowNav(): Modifier
