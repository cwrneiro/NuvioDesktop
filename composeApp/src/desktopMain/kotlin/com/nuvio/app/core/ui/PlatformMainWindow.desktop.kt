package com.nuvio.app.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import java.awt.Window

/**
 * The desktop app's main AWT window, provided by Main.kt around the app
 * content. The arrow dispatcher must ignore key events that belong to other
 * windows (dialogs, popups, the PiP player). LocalAwtWindow is an experimental
 * CMP API and is not guaranteed to be populated on every host composition
 * path, so this explicit local is the primary source and LocalAwtWindow the
 * fallback.
 */
internal val LocalNuvioMainWindow = staticCompositionLocalOf<Window?> { null }
