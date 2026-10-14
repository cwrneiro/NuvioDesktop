package com.nuvio.app.core.ui

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * cwrneirOS: the single shared copy of the frame-retry focus idiom.
 *
 * A focus target that was just composed may not be placed/attached yet, so an
 * immediate [FocusRequester.requestFocus] can fail — or throw while the
 * requester is still detached. The idiom: wait one frame, request, stop at the
 * first success, give up after [maxFrames] frames. `runCatching` treats a
 * not-yet-attached requester as "not placed yet" instead of crashing.
 *
 * @return true when a request succeeded within [maxFrames] frames.
 */
internal suspend fun FocusRequester.requestFocusWhenPlaced(maxFrames: Int = 8): Boolean {
    repeat(maxFrames) {
        withFrameNanos { }
        if (runCatching { requestFocus() }.getOrDefault(false)) return true
    }
    return false
}

/**
 * Seeds [requester] with focus only while nothing in the main scene currently
 * holds it (see [PlatformFocusPresence]): background events such as data
 * arrival must never steal focus from whatever the user is already operating.
 */
internal suspend fun seedFocusIfUnfocused(requester: FocusRequester) {
    if (!PlatformFocusPresence.hasFocus) requester.requestFocusWhenPlaced()
}
