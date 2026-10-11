package com.nuvio.app.focuscontract

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.FocusRingSemantics
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.core.ui.SettingsFocusLinks
import com.nuvio.app.core.ui.focusPresenceMonitor
import com.nuvio.app.features.settings.SettingsScreen
import org.junit.Before
import org.junit.Rule
import kotlin.test.Test

/**
 * cwrneirOS headless focus-contract test for the Settings subpage-entry path
 * (retest-7 item 4): after the page swap, focus must be placed on a ringed,
 * actionable node (the header back button or the first content entry) rather
 * than left nowhere — a focus-nowhere state is what made the next D-pad Right
 * land on the profile/avatar. Also asserts a non-actionable node never holds
 * focus after entry.
 *
 * Mounting the real SettingsScreen also exercises `SettingsFocusLinks` and the
 * `SettingsContentFocusRetryFrames` page-entry loop.
 */
class SettingsSubpageEntryTest {

    @get:Rule
    val compose = createComposeRule()

    private val ringed: SemanticsMatcher = SemanticsMatcher.keyIsDefined(FocusRingSemantics)

    private var requestedPage by mutableStateOf<String?>(null)

    @Before
    fun resetGlobalFocusState() {
        SettingsFocusLinks.railEntries.clear()
        SettingsFocusLinks.categoryEntry = null
        SettingsFocusLinks.contentEntry = null
        requestedPage = null
    }

    @Test
    fun enteringASubpageLandsFocusOnARingedActionableNode() {
        compose.setContent {
            NuvioTheme {
                Box(Modifier.size(1200.dp, 800.dp).focusPresenceMonitor()) {
                    SettingsScreen(
                        requestedPageName = requestedPage,
                        onRequestedPageConsumed = { requestedPage = null },
                    )
                }
            }
        }

        // Let the initial Root composition settle.
        compose.waitForIdle()

        // Navigate to a subpage.
        compose.runOnIdle { requestedPage = "Playback" }
        compose.waitForIdle()
        // Give the frame-retry loop time to place focus.
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()

        // Focus must be on a ringed, actionable node after entry.
        compose.onNode(isFocused() and ringed and hasClickAction()).assertExists()
    }
}
