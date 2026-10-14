package com.nuvio.app.core.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArrowKeyNavTest {

    @Test
    fun `left and right traverse focus when no text field is active`() {
        assertTrue(
            shouldConsumeArrowForFocusNav(
                NuvioNavKey.Left,
                textFieldActive = false,
                multilineFieldActive = false,
            ),
        )
        assertTrue(
            shouldConsumeArrowForFocusNav(
                NuvioNavKey.Right,
                textFieldActive = false,
                multilineFieldActive = false,
            ),
        )
    }

    @Test
    fun `left and right belong to the caret while a text field is active`() {
        assertFalse(
            shouldConsumeArrowForFocusNav(
                NuvioNavKey.Left,
                textFieldActive = true,
                multilineFieldActive = false,
            ),
        )
        assertFalse(
            shouldConsumeArrowForFocusNav(
                NuvioNavKey.Right,
                textFieldActive = true,
                multilineFieldActive = false,
            ),
        )
    }

    @Test
    fun `up and down traverse focus in a single line field`() {
        assertTrue(
            shouldConsumeArrowForFocusNav(
                NuvioNavKey.Up,
                textFieldActive = false,
                multilineFieldActive = false,
            ),
        )
        assertTrue(
            shouldConsumeArrowForFocusNav(
                NuvioNavKey.Down,
                textFieldActive = false,
                multilineFieldActive = false,
            ),
        )
        assertTrue(
            shouldConsumeArrowForFocusNav(
                NuvioNavKey.Up,
                textFieldActive = true,
                multilineFieldActive = false,
            ),
        )
        assertTrue(
            shouldConsumeArrowForFocusNav(
                NuvioNavKey.Down,
                textFieldActive = true,
                multilineFieldActive = false,
            ),
        )
    }

    @Test
    fun `up and down belong to the caret in a multiline field`() {
        assertFalse(
            shouldConsumeArrowForFocusNav(
                NuvioNavKey.Up,
                textFieldActive = true,
                multilineFieldActive = true,
            ),
        )
        assertFalse(
            shouldConsumeArrowForFocusNav(
                NuvioNavKey.Down,
                textFieldActive = true,
                multilineFieldActive = true,
            ),
        )
    }

    @Test
    fun `left and right belong to the slider while a slider is active`() {
        assertFalse(
            shouldConsumeArrowForFocusNav(
                NuvioNavKey.Left,
                textFieldActive = false,
                multilineFieldActive = false,
                sliderActive = true,
            ),
        )
        assertFalse(
            shouldConsumeArrowForFocusNav(
                NuvioNavKey.Right,
                textFieldActive = false,
                multilineFieldActive = false,
                sliderActive = true,
            ),
        )
    }

    @Test
    fun `up and down traverse focus while only a slider is active`() {
        assertTrue(
            shouldConsumeArrowForFocusNav(
                NuvioNavKey.Up,
                textFieldActive = false,
                multilineFieldActive = false,
                sliderActive = true,
            ),
        )
        assertTrue(
            shouldConsumeArrowForFocusNav(
                NuvioNavKey.Down,
                textFieldActive = false,
                multilineFieldActive = false,
                sliderActive = true,
            ),
        )
    }

    @Test
    fun `the text field wins when both a text field and a slider claim focus`() {
        // Left/Right: the caret wins over the thumb either way.
        assertFalse(
            shouldConsumeArrowForFocusNav(
                NuvioNavKey.Left,
                textFieldActive = true,
                multilineFieldActive = false,
                sliderActive = true,
            ),
        )
        // Up/Down in a multiline field: the caret still owns them; a slider
        // never claims Up/Down, so text semantics decide.
        assertFalse(
            shouldConsumeArrowForFocusNav(
                NuvioNavKey.Up,
                textFieldActive = true,
                multilineFieldActive = true,
                sliderActive = true,
            ),
        )
        assertFalse(
            shouldConsumeArrowForFocusNav(
                NuvioNavKey.Down,
                textFieldActive = true,
                multilineFieldActive = true,
                sliderActive = true,
            ),
        )
    }
}
