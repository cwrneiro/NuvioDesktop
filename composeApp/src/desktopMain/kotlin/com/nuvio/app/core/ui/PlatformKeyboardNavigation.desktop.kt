package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import com.nuvio.app.core.ui.PlatformFocusPresence
import java.awt.Component
import java.awt.Container
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.TextComponent
import java.awt.event.KeyEvent
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.Scrollable

@Composable
actual fun PlatformKeyboardNavigation() {
    val focusManager = LocalFocusManager.current

    DisposableEffect(Unit) {
        val dispatcher = KeyEventDispatcher { event: KeyEvent ->
            if (event.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .focusOwner.isArrowKeyConsumer()
            ) return@KeyEventDispatcher false

            // Compose text fields are not AWT components, so isArrowKeyConsumer()
            // cannot see them. Gate per key instead of blanket-passing arrows:
            // Left/Right always belong to the caret while a field is focused,
            // but Up/Down belong to the caret only in a multiline field; in a
            // single-line field they move nothing and must still traverse focus.
            val key = event.keyCode
            when (key) {
                KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT ->
                    if (DesktopTextInputState.isActive) return@KeyEventDispatcher false
                KeyEvent.VK_UP, KeyEvent.VK_DOWN ->
                    if (DesktopTextInputState.isMultilineActive) return@KeyEventDispatcher false
            }

            val direction = when (key) {
                KeyEvent.VK_LEFT -> FocusDirection.Left
                KeyEvent.VK_RIGHT -> FocusDirection.Right
                KeyEvent.VK_UP -> FocusDirection.Up
                KeyEvent.VK_DOWN -> FocusDirection.Down
                else -> return@KeyEventDispatcher false
            }
            var moved = focusManager.moveFocus(direction)
            if (!moved && PlatformFocusPresence.anchorActive) {
                // The anchor's far focusRect makes normal 2D search fail while it
                // is focused; clearing focus restores the usual root-anchored
                // search for one retry.
                focusManager.clearFocus(force = true)
                moved = focusManager.moveFocus(direction)
            }
            println(
                "nuvio-nav: key=$key moved=$moved text=${DesktopTextInputState.isActive} " +
                    "ml=${DesktopTextInputState.isMultilineActive} focus=${PlatformFocusPresence.hasFocus} " +
                    "anchor=${PlatformFocusPresence.anchorActive}",
            )
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

private fun Component?.isArrowKeyConsumer(): Boolean = when (this) {
    is JTextField -> true
    is JTextArea -> true
    is TextComponent -> true
    is JSpinner -> true
    is JSlider -> true
    is JScrollPane -> true
    is Container -> this is Scrollable
    else -> false
}
