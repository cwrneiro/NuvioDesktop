package com.nuvio.app.core.ui

import androidx.compose.ui.focus.FocusRequester
import com.nuvio.app.AppScreenTab

/** cwrneirOS: focus-graph bridge between the app rail, the settings category column and its content. */
internal object SettingsFocusLinks {
    var categoryEntry: FocusRequester? = null
    var contentEntry: FocusRequester? = null

    /** Per-tab rail item requesters, registered by DesktopHoverSidebar (Left → owning icon, round 4b). */
    val railEntries = mutableMapOf<AppScreenTab, FocusRequester>()
}
