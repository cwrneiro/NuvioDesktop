package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
        focusManager.moveFocus(navKey.toFocusDirection())
        // Consume even when no candidate moved: same swallow-on-fail choice as
        // the app-wide dispatcher, so a failed move cannot fall through to
        // other handlers or scroll containers.
        true
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.toNuvioNavKey(): NuvioNavKey? = when (key) {
    Key.DirectionLeft -> NuvioNavKey.Left
    Key.DirectionRight -> NuvioNavKey.Right
    Key.DirectionUp -> NuvioNavKey.Up
    Key.DirectionDown -> NuvioNavKey.Down
    else -> null
}
