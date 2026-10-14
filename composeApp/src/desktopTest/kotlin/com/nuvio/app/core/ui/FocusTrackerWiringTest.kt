package com.nuvio.app.core.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * cwrneirOS: pins the tracker wiring invariant documented in
 * DesktopTextInputState.kt and DesktopSliderState.kt — every focused-node
 * increment is paired with exactly one decrement (focus loss or disposal,
 * whichever comes first), so the process-global counters always return to
 * exactly zero and `isActive` mirrors "a tracked node currently holds focus".
 *
 * Gain, loss, overlap (focus transferred directly between two tracked nodes)
 * and detach-while-focused are driven through the same modifier factories the
 * real screens use ([nuvioTracksTextInput] / [nuvioTracksSlider]); a leaked
 * counter from any of these paths would leave `isActive` stuck true and fail
 * the next assertion.
 */
class FocusTrackerWiringTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun textInputTrackerCountsGainLoseAndDetachSymmetrically() {
        lateinit var focusManager: FocusManager
        val singleField = FocusRequester()
        val multilineField = FocusRequester()
        val untracked = FocusRequester()
        var showSingle by mutableStateOf(true)
        var showMultiline by mutableStateOf(true)

        compose.setContent {
            focusManager = LocalFocusManager.current
            Box(Modifier.size(400.dp, 200.dp)) {
                if (showSingle) {
                    Box(
                        Modifier
                            .nuvioTracksTextInput(singleLine = true)
                            .focusRequester(singleField)
                            .focusable(),
                    )
                }
                if (showMultiline) {
                    Box(
                        Modifier
                            .nuvioTracksTextInput(singleLine = false)
                            .focusRequester(multilineField)
                            .focusable(),
                    )
                }
                Box(Modifier.focusRequester(untracked).focusable())
            }
        }

        compose.waitForIdle()
        compose.runOnIdle { focusManager.clearFocus(force = true) }
        compose.waitForIdle()

        // Baseline: no tracked field focused, counters at exactly zero.
        assertFalse(compose.runOnIdle { DesktopTextInputState.isActive })
        assertFalse(compose.runOnIdle { DesktopTextInputState.isMultilineActive })
        assertFalse(compose.runOnIdle { PlatformTextInputState.isActive })

        // Gain: single-line field focused (gate on, multiline gate off).
        compose.runOnIdle { singleField.requestFocus() }
        compose.waitForIdle()
        assertTrue(compose.runOnIdle { DesktopTextInputState.isActive })
        assertFalse(compose.runOnIdle { DesktopTextInputState.isMultilineActive })
        assertTrue(compose.runOnIdle { PlatformTextInputState.isActive })

        // Overlap: transfer directly to the multiline field — one loss and one
        // gain inside the same focus transaction must stay balanced.
        compose.runOnIdle { multilineField.requestFocus() }
        compose.waitForIdle()
        assertTrue(compose.runOnIdle { DesktopTextInputState.isActive })
        assertTrue(compose.runOnIdle { DesktopTextInputState.isMultilineActive })
        assertTrue(compose.runOnIdle { PlatformTextInputState.isMultilineActive })

        // Lose: move to an untracked node — both counters back to exactly zero.
        compose.runOnIdle { untracked.requestFocus() }
        compose.waitForIdle()
        assertFalse(compose.runOnIdle { DesktopTextInputState.isActive })
        assertFalse(compose.runOnIdle { DesktopTextInputState.isMultilineActive })
        assertFalse(compose.runOnIdle { PlatformTextInputState.isActive })

        // Detach while focused: disposal decrements exactly once, even though
        // no focus-loss callback ever fires for the removed node.
        compose.runOnIdle { multilineField.requestFocus() }
        compose.waitForIdle()
        assertTrue(compose.runOnIdle { DesktopTextInputState.isMultilineActive })
        compose.runOnIdle { showMultiline = false }
        compose.waitForIdle()
        assertFalse(compose.runOnIdle { DesktopTextInputState.isActive })
        assertFalse(compose.runOnIdle { DesktopTextInputState.isMultilineActive })

        // Detach a focused single-line field as the last tracked node.
        compose.runOnIdle { singleField.requestFocus() }
        compose.waitForIdle()
        assertTrue(compose.runOnIdle { DesktopTextInputState.isActive })
        compose.runOnIdle { showSingle = false }
        compose.waitForIdle()
        assertFalse(compose.runOnIdle { DesktopTextInputState.isActive })
        assertFalse(compose.runOnIdle { PlatformTextInputState.isActive })

        compose.runOnIdle { focusManager.clearFocus(force = true) }
    }

    @Test
    fun sliderTrackerCountsGainLoseAndDetachSymmetrically() {
        lateinit var focusManager: FocusManager
        val sliderA = FocusRequester()
        val sliderB = FocusRequester()
        val untracked = FocusRequester()
        var showA by mutableStateOf(true)
        var showB by mutableStateOf(true)

        compose.setContent {
            focusManager = LocalFocusManager.current
            Box(Modifier.size(400.dp, 200.dp)) {
                if (showA) {
                    Box(
                        Modifier
                            .nuvioTracksSlider()
                            .focusRequester(sliderA)
                            .focusable(),
                    )
                }
                if (showB) {
                    Box(
                        Modifier
                            .nuvioTracksSlider()
                            .focusRequester(sliderB)
                            .focusable(),
                    )
                }
                Box(Modifier.focusRequester(untracked).focusable())
            }
        }

        compose.waitForIdle()
        compose.runOnIdle { focusManager.clearFocus(force = true) }
        compose.waitForIdle()

        // Baseline: no tracked slider focused.
        assertFalse(compose.runOnIdle { DesktopSliderState.isActive })

        // Gain.
        compose.runOnIdle { sliderA.requestFocus() }
        compose.waitForIdle()
        assertTrue(compose.runOnIdle { DesktopSliderState.isActive })

        // Overlap: direct tracked -> tracked transfer stays balanced at one.
        compose.runOnIdle { sliderB.requestFocus() }
        compose.waitForIdle()
        assertTrue(compose.runOnIdle { DesktopSliderState.isActive })

        // Lose: untracked node takes focus — counter back to exactly zero.
        compose.runOnIdle { untracked.requestFocus() }
        compose.waitForIdle()
        assertFalse(compose.runOnIdle { DesktopSliderState.isActive })

        // Detach while focused: disposal decrements exactly once.
        compose.runOnIdle { sliderB.requestFocus() }
        compose.waitForIdle()
        assertTrue(compose.runOnIdle { DesktopSliderState.isActive })
        compose.runOnIdle { showB = false }
        compose.waitForIdle()
        assertFalse(compose.runOnIdle { DesktopSliderState.isActive })

        // Detach the last tracked slider while it holds focus.
        compose.runOnIdle { sliderA.requestFocus() }
        compose.waitForIdle()
        assertTrue(compose.runOnIdle { DesktopSliderState.isActive })
        compose.runOnIdle { showA = false }
        compose.waitForIdle()
        assertFalse(compose.runOnIdle { DesktopSliderState.isActive })

        compose.runOnIdle { focusManager.clearFocus(force = true) }
    }
}
