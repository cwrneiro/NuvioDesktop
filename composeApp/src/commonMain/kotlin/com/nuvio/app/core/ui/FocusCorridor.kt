package com.nuvio.app.core.ui

import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusRequester

/** cwrneirOS focus corridor: a declarative 1-D chain of focus stops. */
internal enum class CorridorOrientation { Vertical, Horizontal }

/**
 * Builds the [FocusProperties] scope for item [index] of a 1-D corridor.
 *
 * - Vertical: `up`/`down` point at the previous/next requester; Horizontal:
 *   `left`/`right`.
 * - The first/last item fall back to [start]/[end] (both default to
 *   [FocusRequester.Cancel]).
 * - [overrideEdges] runs LAST inside the same properties block, so caller
 *   edges (e.g. the Settings-tab Right redirect, the avatar's profile-stack
 *   Down) always win over the corridor's generated edges.
 *
 * Precedence note (Compose focus properties are fetched into ONE instance;
 * per property, the outermost `focusProperties` in the modifier chain wins):
 * do NOT chain a second `.focusProperties { }` after this call — the outer
 * block would discard the override. Pass overrides through [overrideEdges].
 */
internal fun corridorEdges(
    orientation: CorridorOrientation,
    requesters: List<FocusRequester>,
    index: Int,
    start: FocusRequester = FocusRequester.Cancel,
    end: FocusRequester = FocusRequester.Cancel,
    overrideEdges: (FocusProperties.() -> Unit)? = null,
): FocusProperties.() -> Unit = {
    when (orientation) {
        CorridorOrientation.Vertical -> {
            up = requesters.getOrNull(index - 1) ?: start
            down = requesters.getOrNull(index + 1) ?: end
        }
        CorridorOrientation.Horizontal -> {
            left = requesters.getOrNull(index - 1) ?: start
            right = requesters.getOrNull(index + 1) ?: end
        }
    }
    overrideEdges?.invoke(this)
}
