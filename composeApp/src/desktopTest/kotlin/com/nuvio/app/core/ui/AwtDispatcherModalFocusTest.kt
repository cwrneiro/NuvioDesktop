package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.awt.Component
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.KeyEvent
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Hardware-level contract test for the desktop arrow dispatcher: real windows,
 * real AWT key events, real dialog.
 *
 * Guards the H1 fix: while a modal dialog owns the keyboard the app-wide
 * dispatcher must NOT move focus in the main window behind it, and arrows must
 * still traverse focus inside the dialog (NuvioModalDialog's own handler).
 *
 * Key events are posted as real AWT KeyEvents into the system event queue, so
 * they travel through the same KeyboardFocusManager/KeyEventDispatcher chain
 * as physical keys without depending on the compositor granting OS focus
 * (which is unreliable under Wayland).
 *
 * Only runs when explicitly requested, because it opens windows:
 *
 *   NUVIO_FOCUS_HW_TEST=1 ./gradlew :composeApp:desktopTest \
 *       --tests "*AwtDispatcherModalFocusTest*"
 *
 * Skips automatically in headless environments (CI).
 */
class AwtDispatcherModalFocusTest {

    @Test
    fun arrowsStayInsideOpenDialogAndNeverMoveBackgroundFocus() {
        assumeTrue("set NUVIO_FOCUS_HW_TEST=1 to run this hardware test", System.getenv("NUVIO_FOCUS_HW_TEST") == "1")
        assumeTrue("requires a display", !GraphicsEnvironment.isHeadless())

        val focusLog = ConcurrentLinkedQueue<String>()
        val dialogComposed = AtomicBoolean(false)
        var mainFocus: FocusRequester? = null
        var dialogFocus: FocusRequester? = null
        var mainWindow: ComposeWindow? = null
        var dialogOpen: MutableState<Boolean>? = null

        try {
            SwingUtilities.invokeAndWait {
                val window = ComposeWindow()
                val requestMain = FocusRequester()
                val requestDialog = FocusRequester()
                val dialogOpenState = mutableStateOf(false)
                mainFocus = requestMain
                dialogFocus = requestDialog
                dialogOpen = dialogOpenState
                window.setContent {
                    NuvioTheme {
                        PlatformKeyboardNavigation()
                        Column(Modifier.fillMaxSize()) {
                            repeat(3) { index ->
                                Box(
                                    Modifier
                                        .size(width = 120.dp, height = 60.dp)
                                        .background(Color.DarkGray)
                                        .then(if (index == 0) Modifier.focusRequester(requestMain) else Modifier)
                                        .onFocusChanged { if (it.isFocused) focusLog.add("main-$index") }
                                        .clickable { },
                                )
                            }
                        }
                        if (dialogOpenState.value) {
                            DialogSurface(onDismissRequest = { }, title = "Test dialog") {
                                SideEffect { dialogComposed.set(true) }
                                LaunchedEffect(Unit) { requestDialog.requestFocusWhenPlaced() }
                                repeat(3) { index ->
                                    DialogOption(
                                        text = "Option $index",
                                        selected = false,
                                        onClick = { },
                                        modifier = Modifier
                                            .then(if (index == 0) Modifier.focusRequester(requestDialog) else Modifier)
                                            .onFocusChanged {
                                                if (it.isFocused) focusLog.add("dialog-$index")
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
                window.setSize(640, 720)
                window.isVisible = true
                window.toFront()
                window.requestFocus()
                mainWindow = window
            }

            // The main window must own AWT focus before we can dispatch into it.
            assertTrue("main window never took focus") { awaitMainFocus(mainWindow) }
            SwingUtilities.invokeLater { mainFocus?.requestFocus() }
            assertTrue("main-0 never gained focus") { waitUntil { focusLog.contains("main-0") } }

            // Sanity: the dispatcher must move focus with arrows in the main window.
            focusLog.clear()
            postKey(mainWindow, KeyEvent.VK_DOWN)
            assertTrue("dispatcher did not move focus in the main window") {
                waitUntil { focusLog.any { it.startsWith("main-") } }
            }

            // Open the dialog. It may be a separate AWT window or an in-scene
            // layer (current CMP renders BasicAlertDialog in-scene on desktop),
            // so detect composition and focus rather than windows.
            SwingUtilities.invokeLater { dialogOpen?.value = true }
            assertTrue("dialog never composed") { waitUntil { dialogComposed.get() } }
            assertTrue("dialog never took focus") {
                waitUntil { focusLog.any { it.startsWith("dialog-") } }
            }

            // (1) Arrows owned by the dialog must traverse the dialog itself.
            focusLog.clear()
            repeat(2) {
                postKey(mainWindow, KeyEvent.VK_DOWN)
                Thread.sleep(250)
            }
            postKey(mainWindow, KeyEvent.VK_UP)
            Thread.sleep(400)
            assertTrue("arrows did not move focus inside the dialog: $focusLog") {
                focusLog.any { it.startsWith("dialog-") }
            }
            assertTrue("background focus moved while the dialog was open: $focusLog") {
                focusLog.none { it.startsWith("main-") }
            }

            // (2) Modal gate: even an event addressed to the main window's focus
            // owner must not move background focus while the dialog is open.
            // (Before the fix this moved focus behind the modal.)
            focusLog.clear()
            postKey(mainWindow, KeyEvent.VK_DOWN)
            Thread.sleep(400)
            assertTrue("dispatcher moved background focus through the modal gate: $focusLog") {
                focusLog.none { it.startsWith("main-") }
            }
        } finally {
            SwingUtilities.invokeLater {
                Window.getWindows().forEach { if (it.isVisible) it.dispose() }
            }
            Thread.sleep(200)
        }
    }

    @Test
    fun arrowsStayInsideOpenMenuAndNeverMoveBackgroundFocus() {
        assumeTrue("set NUVIO_FOCUS_HW_TEST=1 to run this hardware test", System.getenv("NUVIO_FOCUS_HW_TEST") == "1")
        assumeTrue("requires a display", !GraphicsEnvironment.isHeadless())

        val focusLog = ConcurrentLinkedQueue<String>()
        var mainWindow: ComposeWindow? = null
        var mainFocus: FocusRequester? = null
        var menuState: MutableState<Boolean>? = null

        try {
            SwingUtilities.invokeAndWait {
                val window = ComposeWindow()
                val requestMain = FocusRequester()
                val requestMenu = FocusRequester()
                val open = mutableStateOf(false)
                mainWindow = window
                mainFocus = requestMain
                menuState = open
                window.setContent {
                    NuvioTheme {
                        PlatformKeyboardNavigation()
                        Box(Modifier.fillMaxSize()) {
                            Column {
                                repeat(3) { index ->
                                    Box(
                                        Modifier
                                            .size(120.dp, 60.dp)
                                            .background(Color.DarkGray)
                                            .then(if (index == 0) Modifier.focusRequester(requestMain) else Modifier)
                                            .onFocusChanged { if (it.isFocused) focusLog.add("main-$index") }
                                            .clickable { },
                                    )
                                }
                            }
                            if (open.value) {
                                Menu(expanded = true, onDismissRequest = { }) {
                                    LaunchedEffect(Unit) { requestMenu.requestFocusWhenPlaced() }
                                    repeat(3) { index ->
                                        MenuItem(
                                            text = "Item $index",
                                            onClick = { },
                                            modifier = Modifier
                                                .then(if (index == 0) Modifier.focusRequester(requestMenu) else Modifier)
                                                .onFocusChanged { if (it.isFocused) focusLog.add("menu-$index") },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                window.setSize(640, 720)
                window.isVisible = true
                window.toFront()
                window.requestFocus()
            }

            assertTrue("main window never took focus") { awaitMainFocus(mainWindow) }
            SwingUtilities.invokeLater { mainFocus?.requestFocus() }
            assertTrue("main-0 never gained focus") { waitUntil { focusLog.contains("main-0") } }

            focusLog.clear()
            postKey(mainWindow, KeyEvent.VK_DOWN)
            assertTrue("dispatcher did not move focus in the main window") {
                waitUntil { focusLog.any { it.startsWith("main-") } }
            }

            SwingUtilities.invokeLater { menuState?.value = true }
            assertTrue("menu never took focus") { waitUntil { focusLog.any { it.startsWith("menu-") } } }

            focusLog.clear()
            repeat(2) {
                postKey(mainWindow, KeyEvent.VK_DOWN)
                Thread.sleep(250)
            }
            postKey(mainWindow, KeyEvent.VK_UP)
            Thread.sleep(400)

            assertTrue("arrows did not move focus inside the menu: $focusLog") {
                focusLog.any { it.startsWith("menu-") }
            }
            assertTrue("background focus moved while the menu was open: $focusLog") {
                focusLog.none { it.startsWith("main-") }
            }
        } finally {
            SwingUtilities.invokeLater {
                Window.getWindows().forEach { if (it.isVisible) it.dispose() }
            }
            Thread.sleep(200)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun arrowsMoveInsideOpenBottomSheet() {
        assumeTrue("set NUVIO_FOCUS_HW_TEST=1 to run this hardware test", System.getenv("NUVIO_FOCUS_HW_TEST") == "1")
        assumeTrue("requires a display", !GraphicsEnvironment.isHeadless())

        val focusLog = ConcurrentLinkedQueue<String>()
        val sheetComposed = AtomicBoolean(false)
        var mainWindow: ComposeWindow? = null
        var mainFocus: FocusRequester? = null
        var sheetOpenState: MutableState<Boolean>? = null

        try {
            SwingUtilities.invokeAndWait {
                val window = ComposeWindow()
                val requestMain = FocusRequester()
                val open = mutableStateOf(false)
                mainWindow = window
                mainFocus = requestMain
                sheetOpenState = open
                window.setContent {
                    NuvioTheme {
                        PlatformKeyboardNavigation()
                        Column(Modifier.fillMaxSize()) {
                            repeat(3) { index ->
                                Box(
                                    Modifier
                                        .size(120.dp, 60.dp)
                                        .background(Color.DarkGray)
                                        .then(if (index == 0) Modifier.focusRequester(requestMain) else Modifier)
                                        .onFocusChanged { if (it.isFocused) focusLog.add("main-$index") }
                                        .clickable { },
                                )
                            }
                        }
                        if (open.value) {
                            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                            NuvioModalBottomSheet(
                                onDismissRequest = { },
                                sheetState = sheetState,
                            ) {
                                SideEffect { sheetComposed.set(true) }
                                repeat(3) { index ->
                                    NuvioBottomSheetActionRow(
                                        title = "Row $index",
                                        onClick = { },
                                        modifier = Modifier.onFocusChanged {
                                            if (it.isFocused) focusLog.add("sheet-$index")
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                window.setSize(640, 720)
                window.isVisible = true
                window.toFront()
                window.requestFocus()
            }

            assertTrue("main window never took focus") { awaitMainFocus(mainWindow) }
            SwingUtilities.invokeLater { mainFocus?.requestFocus() }
            assertTrue("main-0 never gained focus") { waitUntil { focusLog.contains("main-0") } }

            focusLog.clear()
            postKey(mainWindow, KeyEvent.VK_DOWN)
            assertTrue("dispatcher did not move focus in the main window") {
                waitUntil { focusLog.any { it.startsWith("main-") } }
            }

            SwingUtilities.invokeLater { sheetOpenState?.value = true }
            assertTrue("sheet never composed") { waitUntil { sheetComposed.get() } }
            Thread.sleep(600)

            focusLog.clear()
            repeat(2) {
                postKey(mainWindow, KeyEvent.VK_DOWN)
                Thread.sleep(250)
            }
            postKey(mainWindow, KeyEvent.VK_UP)
            Thread.sleep(400)

            assertTrue("arrows did not move focus inside the sheet: $focusLog") {
                focusLog.any { it.startsWith("sheet-") }
            }
            assertTrue("background focus moved while the sheet was open: $focusLog") {
                focusLog.none { it.startsWith("main-") }
            }
        } finally {
            SwingUtilities.invokeLater {
                Window.getWindows().forEach { if (it.isVisible) it.dispose() }
            }
            Thread.sleep(200)
        }
    }

    /** Posts a real AWT key event as if the window's focus owner had typed it. */
    private fun postKey(window: Window?, keyCode: Int) {
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val source: Component = focusManager.focusOwner ?: window ?: return
        Toolkit.getDefaultToolkit().systemEventQueue.postEvent(
            KeyEvent(source, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED),
        )
    }

    /** Waits for AWT focus, re-requesting it (XWayland focus hand-off is flaky across test windows). */
    private fun awaitMainFocus(window: Window?): Boolean = waitUntil(12_000) {
        if (window != null && !focusOwnerInside(window)) {
            SwingUtilities.invokeLater {
                window.toFront()
                window.requestFocus()
            }
        }
        focusOwnerInside(window)
    }

    private fun focusOwnerInside(window: Window?): Boolean {
        if (window == null) return false
        var component: Component? = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        while (component != null) {
            if (component === window) return true
            component = component.parent
        }
        return false
    }

    private fun waitUntil(timeoutMs: Long = 8_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }
}
