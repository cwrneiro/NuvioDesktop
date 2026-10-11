package com.nuvio.app.core.ui

import androidx.compose.ui.focus.FocusRequester

/** cwrneirOS: focus-graph bridge between the app rail, the settings category column and its content. */
internal object SettingsFocusLinks {
    var categoryEntry: FocusRequester? = null
    var contentEntry: FocusRequester? = null
}
