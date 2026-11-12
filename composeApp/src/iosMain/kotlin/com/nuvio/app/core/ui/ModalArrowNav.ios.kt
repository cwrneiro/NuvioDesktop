package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier

/** iOS keeps its native focus behavior inside dialogs. */
internal actual fun Modifier.nuvioModalArrowNav(): Modifier = this
