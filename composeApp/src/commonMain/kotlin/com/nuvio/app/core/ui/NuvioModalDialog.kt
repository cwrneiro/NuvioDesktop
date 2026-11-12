package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    DisposableEffect(Unit) {
        PlatformModalState.openDialogs += 1
        onDispose { PlatformModalState.openDialogs -= 1 }
    }
    BasicAlertDialog(onDismissRequest = onDismissRequest, properties = properties) {
        Box(modifier = Modifier.nuvioModalArrowNav()) {
            content()
        }
    }
}

/**
 * Arrow-key focus traversal for dialog content, scoped to the dialog's focus
 * owner. Desktop implements it; other platforms keep their native behavior.
 */
internal expect fun Modifier.nuvioModalArrowNav(): Modifier
