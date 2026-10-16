package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import com.nuvio.app.core.ui.PlatformFocusPresence
import java.awt.Component
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.TextComponent
import java.awt.event.KeyEvent
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformKeyboardNavigation() {
    val focusManager = LocalFocusManager.current
    val window = LocalAwtWindow.current

    // Keyed on window: if the host window is ever recreated, the dispatcher is
    // reinstalled with the new reference instead of treating main-window events
    // as foreign and dropping all arrow handling.
    DisposableEffect(window) {
        val dispatcher = KeyEventDispatcher { event: KeyEvent ->
            if (event.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
            // Dialogs/menus/dropdown popups are separate ComposeWindows: when one
            // of them owns focus it must receive its own keys, so only handle
            // events that belong to the main window. A null window is a defensive
            // fallback (app entry always provides one) keeping the previous
            // unscoped behavior.
            if (window != null) {
                val component = event.component
                if (component == null || SwingUtilities.getWindowAncestor(component) !== window) {
                    return@KeyEventDispatcher false
                }
            }
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .focusOwner.isArrowKeyConsumer()
            ) return@KeyEventDispatcher false

            // Compose text fields are not AWT components, so isArrowKeyConsumer()
            // cannot see them. Gate per key instead of blanket-passing arrows:
            // Left/Right always belong to the caret while a field is focused,
            // but Up/Down belong to the caret only in a multiline field; in a
            // single-line field they move nothing and must still traverse focus.
            // Compose sliders are equally invisible to isArrowKeyConsumer();
            // while one is focused, Left/Right adjust its value instead.
            val navKey = event.toNuvioNavKey() ?: return@KeyEventDispatcher false
            if (!shouldConsumeArrowForFocusNav(
                    navKey,
                    DesktopTextInputState.isActive,
                    DesktopTextInputState.isMultilineActive,
                    DesktopSliderState.isActive,
                )
            ) return@KeyEventDispatcher false

            val direction = navKey.toFocusDirection()
            var moved = focusManager.moveFocus(direction)
            if (!moved && PlatformFocusPresence.anchorActive) {
                // The anchor's focusRect is all-NaN (see MainAppContent's
                // recovery anchor), so 2D search rejects it as a candidate in
                // every direction while it is focused; clearing focus restores
                // the usual root-anchored search for one retry.
                focusManager.clearFocus(force = true)
                moved = focusManager.moveFocus(direction)
            }
            NuvioFocusDiagnostics.log(
                "nuvio-nav: key=${event.keyCode} moved=$moved text=${DesktopTextInputState.isActive} " +
                    "ml=${DesktopTextInputState.isMultilineActive} slider=${DesktopSliderState.isActive} " +
                    "focus=${PlatformFocusPresence.hasFocus} anchor=${PlatformFocusPresence.anchorActive}",
            )
            // Consume the arrow even when the move failed (moved=false):
            // returning the event untranslated risks double-handling, and
            // swallowing failed moves is the current tested behavior.
            true
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(dispatcher)
        onDispose {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(dispatcher)
        }
    }
}

private fun KeyEvent.toNuvioNavKey(): NuvioNavKey? = when (keyCode) {
    KeyEvent.VK_LEFT -> NuvioNavKey.Left
    KeyEvent.VK_RIGHT -> NuvioNavKey.Right
    KeyEvent.VK_UP -> NuvioNavKey.Up
    KeyEvent.VK_DOWN -> NuvioNavKey.Down
    else -> null
}

private fun NuvioNavKey.toFocusDirection(): FocusDirection = when (this) {
    NuvioNavKey.Left -> FocusDirection.Left
    NuvioNavKey.Right -> FocusDirection.Right
    NuvioNavKey.Up -> FocusDirection.Up
    NuvioNavKey.Down -> FocusDirection.Down
}

private fun Component?.isArrowKeyConsumer(): Boolean = when (this) {
    is JTextField -> true
    is JTextArea -> true
    is TextComponent -> true
    is JSpinner -> true
    is JSlider -> true
    is JScrollPane -> true
    else -> false
}
