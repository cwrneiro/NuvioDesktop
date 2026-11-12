package com.nuvio.app.core.ui

import androidx.compose.ui.Modifier

/** Android keeps its native D-pad focus traversal inside dialogs. */
internal actual fun Modifier.nuvioModalArrowNav(): Modifier = this
