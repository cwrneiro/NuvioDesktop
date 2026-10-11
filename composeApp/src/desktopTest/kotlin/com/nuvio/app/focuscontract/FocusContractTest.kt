package com.nuvio.app.focuscontract

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.nuvio.app.AppScreenTab
import com.nuvio.app.DesktopHoverSidebar
import com.nuvio.app.RootTabHost
import com.nuvio.app.core.ui.FocusRingSemantics
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.core.ui.SettingsFocusLinks
import com.nuvio.app.core.ui.nuvioFocusBorder
import org.junit.Before
import org.junit.Rule
import kotlin.test.Test

/**
 * cwrneirOS headless focus-contract tests.
 *
 * These encode the couch acceptance rules as deterministic Compose assertions so
 * focus regressions are caught without a hardware pass. Arrow navigation is an
 * AWT KeyEventDispatcher on desktop, which is absent headlessly; focus is driven
 * with LocalFocusManager.moveFocus, exactly the path the dispatcher invokes.
 *
 * Invariant under test: after a directional move, focus must land on a node that
 * is BOTH ringed ([FocusRingSemantics]) AND actionable. A focus landing that is
 * ringless or has no click/text action is a dead-end (the retest-7 blocker:
 * RootTabPane's `canFocus = active` promoted the LazyColumn scroll anchor — an
 * invisible, non-actionable focus target — into a 2D candidate).
 */
class FocusContractTest {

    @get:Rule
    val compose = createComposeRule()

    private val ringed: SemanticsMatcher = SemanticsMatcher.keyIsDefined(FocusRingSemantics)

    @Before
    fun resetGlobalFocusState() {
        SettingsFocusLinks.railEntries.clear()
        SettingsFocusLinks.categoryEntry = null
        SettingsFocusLinks.contentEntry = null
    }

    @Test
    fun rightFromRailLandsOnRingedActionableContentNotThePaneAnchor() {
        lateinit var focusManager: FocusManager

        compose.setContent {
            NuvioTheme {
                focusManager = LocalFocusManager.current
                Row(Modifier.fillMaxSize()) {
                    DesktopHoverSidebar(
                        selectedTab = AppScreenTab.Home,
                        onTabSelected = {},
                        onProfileSelected = {},
                        onAddProfileRequested = {},
                        sidebarWidth = 68.dp,
                    )
                    RootTabHost(
                        selectedTab = AppScreenTab.Home,
                        modifier = Modifier.fillMaxSize(),
                        active = true,
                        profileId = 1,
                    ) { _ ->
                        // A LazyColumn reproduces the real content root: Foundation
                        // attaches a Focusability.Never scroll anchor to it.
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(20) { index ->
                                androidx.compose.foundation.layout.Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .nuvioFocusBorder(RoundedCornerShape(12.dp))
                                        .clickable {}
                                        .testTag("content-row-$index"),
                                )
                            }
                        }
                    }
                }
            }
        }

        compose.waitForIdle()
        val homeRequester = SettingsFocusLinks.railEntries[AppScreenTab.Home]
        requireNotNull(homeRequester) { "rail home requester was not registered" }
        compose.runOnIdle { homeRequester.requestFocus() }
        compose.waitForIdle()

        compose.runOnIdle { focusManager.moveFocus(FocusDirection.Right) }
        compose.waitForIdle()

        // Focus must be on a ringed, actionable node: never the ringless,
        // non-actionable LazyColumn scroll anchor.
        compose.onNode(isFocused() and ringed and hasClickAction()).assertExists()

        // And it must be one of the real content rows, not some wrapper.
        val focusedRow = (0 until 20).firstOrNull { index ->
            runCatching {
                compose.onNodeWithTag("content-row-$index").assertIsFocused()
            }.isSuccess
        }
        require(focusedRow != null) { "focus did not land on a content row after Right" }

        // Sanity: the landed row is ringed.
        compose.onNodeWithTag("content-row-$focusedRow")
            .assert(ringed)
            .assert(hasClickAction())
    }
}
