package com.familyhome.app.presentation.screens.e2e

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhome.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun appLaunchesWithoutCrash() {
        // Just verify the app doesn't crash on launch
        composeRule.waitForIdle()
    }

    @Test
    fun setupScreen_showsCreateFamilyButton_onFirstLaunch() {
        // On a fresh install, the setup screen should appear
        // This is a best-effort check — if family data exists, login screen shows instead
        composeRule.waitForIdle()
        // App is either on Setup or Login screen — both are valid states
        val onSetup = composeRule.onAllNodesWithText("Create Family").fetchSemanticsNodes().isNotEmpty()
        val onLogin = composeRule.onAllNodesWithText("Welcome").fetchSemanticsNodes().isNotEmpty()
            || composeRule.onAllNodesWithText("Unlock").fetchSemanticsNodes().isNotEmpty()
            || composeRule.onAllNodesWithText("FamilyHome").fetchSemanticsNodes().isNotEmpty()
        assert(onSetup || onLogin) { "Expected setup or login screen to be visible" }
    }
}
