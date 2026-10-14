package com.nuvio.app.focuscontract

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.PlatformFocusPresence
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import kotlin.test.Test

/**
 * cwrneirOS headless contract test for the focus-parking anchor's NaN trick.
 *
 * The production anchor (MainAppContent.kt, root overlay Box) is a 0x0,
 * invisible, but fully programmatically-focusable node whose modifier chain is
 * replicated verbatim below:
 *
 *   .size(0.dp)
 *   .focusProperties { focusRect = Rect(Float.NaN, Float.NaN, Float.NaN, Float.NaN) }
 *   .onFocusChanged { PlatformFocusPresence.anchorActive = it.isFocused }
 *   .focusRequester(rootFocusAnchor)
 *   .focusable()
 *
 * THE TRICK: Compose's TwoDimensionalFocusSearch compares candidate rects with
 * plain IEEE-754 float comparisons. Every comparison against NaN evaluates to
 * false, so a NaN focus rect is never selected as a directional candidate in
 * ANY direction — focus can REST on the anchor (requestFocus is not geometric)
 * but arrows can never TARGET it. This is what lets the app park focus on the
 * anchor (Esc / "press B to park" path) without it ever becoming an invisible
 * dead-end that arrows could wander into.
 *
 * THIS IS UNDOCUMENTED CMP BEHAVIOR. It has been verified against the CMP
 * 1.12.0 bytecode (raw float comparisons, no isFinite guard), but nothing in
 * the public API promises it. Two upgrade scenarios would silently break the
 * app's navigation:
 *   1. A future Compose adds an isFinite/filter guard that SKIPS NaN rects:
 *      behavior stays "never a candidate" — fine — unless it instead CLAMPS or
 *      substitutes layout bounds, in which case the 0x0 anchor at its layout
 *      position becomes a legal invisible target and arrow navigation can dead-
 *      end on an invisible node.
 *   2. From-NaN SOURCE semantics change: today, moveFocus(direction) while the
 *      anchor holds focus fails primitively (returns false, focus stays put).
 *      The desktop AWT KeyEventDispatcher (PlatformKeyboardNavigation.desktop.kt)
 *      depends on exactly that: `if (!moved && PlatformFocusPresence.anchorActive)`
 *      it does clearFocus(force = true) + one retry to re-anchor the search at
 *      the root. If CMP made from-NaN search "recover" on its own, that retry
 *      becomes dead code; if it made it succeed into arbitrary nodes, parking
 *      would leak focus to invisible places.
 *
 * This test exists to FAIL LOUDLY on any CMP upgrade that changes those
 * semantics, so the bump is caught here rather than as a couch-hardware focus
 * bug. If it fails after an upgrade, re-verify the bytecode of
 * TwoDimensionalFocusSearch and revisit the anchor + dispatcher recovery as a
 * pair.
 *
 * Scene note: the anchor here additionally carries `.align(Alignment.Center)`
 * purely for scene placement; focusRect overrides layout bounds for 2D search,
 * so placement is immaterial to the contract — Center placement is chosen
 * because it puts the anchor dead-center inside every directional beam, i.e.
 * the most adversarial position: without the NaN rejection it would be the
 * nearest straight-line candidate for all four moves under test.
 */
class FocusAnchorContractTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var focusManager: FocusManager
    private val anchorRequester = FocusRequester()
    private val requesters = mutableMapOf<String, FocusRequester>()

    @Before
    fun resetGlobalFocusState() {
        PlatformFocusPresence.anchorActive = false
        PlatformFocusPresence.hasFocus = false
    }

    @After
    fun resetGlobalFocusStateAfter() {
        PlatformFocusPresence.anchorActive = false
        PlatformFocusPresence.hasFocus = false
    }

    /**
     * Arrow moves from a normal focused node must never land on the NaN anchor,
     * even though the anchor sits dead-center in every directional beam while
     * real candidates exist in each move direction. Search must stay functional
     * and land on a real tile.
     */
    @Test
    fun arrowMovesNeverLandOnTheAnchorWhenRealCandidatesExist() {
        setContentWithAnchorScene()

        // Down from "top": anchor is dead-center in the vertical beam below.
        moveAndAssertLanding("top", FocusDirection.Down)

        // Up from "bottom": symmetric.
        moveAndAssertLanding("bottom", FocusDirection.Up)

        // Right from "left": anchor shares the exact vertical center with the
        // left tile — without NaN rejection it would be the nearest in-beam
        // candidate for the move.
        moveAndAssertLanding("left", FocusDirection.Right)

        // Left from "right": symmetric.
        moveAndAssertLanding("right", FocusDirection.Left)
    }

    /**
     * With focus parked on the anchor, moveFocus(direction) must return false
     * for every arrow direction: the from-NaN source rect makes 2D search fail
     * primitively. This is the exact behavior the desktop AWT dispatcher's
     * clearFocus+retry recovery (PlatformKeyboardNavigation.desktop.kt) is
     * built around. It also asserts focus stays parked after the failed search.
     */
    @Test
    fun fromAnchorDirectionalSearchFailsPrimitivelyInEveryDirection() {
        setContentWithAnchorScene()

        compose.runOnIdle { anchorRequester.requestFocus() }
        compose.waitForIdle()
        assertTrue(
            "precondition: anchor must receive focus via requestFocus (parking)",
            PlatformFocusPresence.anchorActive,
        )

        val results = linkedMapOf<FocusDirection, Boolean>()
        for (direction in listOf(
                FocusDirection.Up,
                FocusDirection.Down,
                FocusDirection.Left,
                FocusDirection.Right,
            )
        ) {
            compose.runOnIdle { results[direction] = focusManager.moveFocus(direction) }
            compose.waitForIdle()
        }
        for ((direction, moved) in results) {
            assertFalse(
                "moveFocus($direction) from the NaN anchor must fail primitively — the AWT " +
                    "dispatcher's clearFocus+retry recovery depends on this signal",
                moved,
            )
        }
        assertTrue(
            "failed searches must leave focus parked on the anchor",
            PlatformFocusPresence.anchorActive,
        )
    }

    /**
     * The anchor must still RECEIVE focus programmatically: parking is what the
     * Esc/B path and the `!PlatformFocusPresence.hasFocus` auto-park in
     * MainAppContent rely on. NaN only affects 2D search candidacy, never
     * requestFocus.
     */
    @Test
    fun anchorStillReceivesProgrammaticFocusParking() {
        setContentWithAnchorScene()

        compose.runOnIdle { requesters.getValue("top").requestFocus() }
        compose.waitForIdle()
        assertFalse(PlatformFocusPresence.anchorActive)

        compose.runOnIdle { anchorRequester.requestFocus() }
        compose.waitForIdle()
        assertTrue(PlatformFocusPresence.anchorActive)

        // Parking is stable across idle frames, not a transient focus grant.
        compose.waitForIdle()
        assertTrue(PlatformFocusPresence.anchorActive)
    }

    /**
     * Focuses [from], moves [direction], and asserts the move succeeded, focus
     * landed on a REAL tile, and never on the anchor. Which real tile CMP's
     * 2D scoring picks is heuristic (e.g. Down from "top" prefers the nearer
     * out-of-beam "left" tile over the farther straight-line "bottom") and is
     * NOT part of this contract — only the NaN-anchor rejection is.
     */
    private fun moveAndAssertLanding(from: String, direction: FocusDirection) {
        compose.runOnIdle { requesters.getValue(from).requestFocus() }
        compose.waitForIdle()
        assertFalse(
            "precondition: focus started on a real node, not the anchor",
            PlatformFocusPresence.anchorActive,
        )
        compose.onNodeWithTag(from).assertIsFocused()

        var moved = false
        compose.runOnIdle { moved = focusManager.moveFocus(direction) }
        compose.waitForIdle()

        val landedOn = focusedTileTag()
        assertTrue(
            "moveFocus($direction) from \"$from\" must find a real candidate — NaN rejection " +
                "must not break normal 2D search (landed on: $landedOn)",
            moved,
        )
        assertFalse(
            "moveFocus($direction) from \"$from\" must NEVER land on the NaN anchor " +
                "(it is dead-center in the beam — exactly the invisible dead-end this " +
                "trick prevents)",
            PlatformFocusPresence.anchorActive,
        )
        assertTrue(
            "moveFocus($direction) from \"$from\" must land on a real tile, not the anchor " +
                "or nowhere — landed on: $landedOn",
            landedOn != null,
        )
    }

    /** The tag of the currently focused tile, or null if focus is elsewhere. */
    private fun focusedTileTag(): String? =
        requesters.keys.firstOrNull { tag ->
            runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess
        }

    private fun setContentWithAnchorScene() {
        val tiles = mapOf(
            "top" to FocusRequester(),
            "bottom" to FocusRequester(),
            "left" to FocusRequester(),
            "right" to FocusRequester(),
        )
        compose.setContent {
            focusManager = LocalFocusManager.current
            Box(Modifier.fillMaxSize()) {
                FocusableTile("top", tiles.getValue("top"), Modifier.align(Alignment.TopCenter).size(width = 240.dp, height = 90.dp))
                FocusableTile("bottom", tiles.getValue("bottom"), Modifier.align(Alignment.BottomCenter).size(width = 240.dp, height = 90.dp))
                FocusableTile("left", tiles.getValue("left"), Modifier.align(Alignment.CenterStart).size(width = 90.dp, height = 160.dp))
                FocusableTile("right", tiles.getValue("right"), Modifier.align(Alignment.CenterEnd).size(width = 90.dp, height = 160.dp))

                // PRODUCTION ANCHOR — exact modifier chain from MainAppContent.kt
                // (root overlay Box). Only differences: scene `.align` (layout
                // only; focusRect overrides search bounds) and this test's own
                // FocusRequester instance instead of rootFocusAnchor. Do NOT
                // add semantics/testTag here — it must stay an invisible,
                // non-actionable node exactly like production.
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(0.dp)
                        .focusProperties { focusRect = Rect(Float.NaN, Float.NaN, Float.NaN, Float.NaN) }
                        .onFocusChanged { PlatformFocusPresence.anchorActive = it.isFocused }
                        .focusRequester(anchorRequester)
                        .focusable(),
                ) {
                }
            }
        }
        compose.waitForIdle()
        requesters.putAll(tiles)
    }
}

@androidx.compose.runtime.Composable
private fun FocusableTile(
    tag: String,
    requester: FocusRequester,
    modifier: Modifier,
) {
    Box(
        modifier
            .focusRequester(requester)
            .clickable {}
            .testTag(tag),
    )
}
