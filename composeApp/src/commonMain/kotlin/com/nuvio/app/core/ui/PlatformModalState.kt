package com.nuvio.app.core.ui

/**
 * Count of currently composed modal dialogs. The desktop arrow dispatcher
 * stands down while a dialog is open: its focus manager belongs to the main
 * window composition, so acting on arrows then moves focus *behind* the modal.
 * Dialogs move focus themselves instead (see NuvioModalDialog /
 * [nuvioModalArrowNav]).
 *
 * Written and read on the Compose UI thread only (composition/apply and AWT key
 * dispatch share the EDT on desktop), so a plain counter suffices.
 */
internal object PlatformModalState {
    var openDialogs: Int = 0

    val hasOpenDialog: Boolean
        get() = openDialogs > 0
}
