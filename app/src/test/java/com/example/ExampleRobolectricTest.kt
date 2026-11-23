package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Julie AI", appName)
  }

  @Test
  fun `main activity launches and navigates all tabs without crash`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("dynamic_island_capsule").assertExists()
    composeTestRule.onNodeWithTag("nav_tab_tasks").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_tab_models").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_tab_screen").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_tab_chat").performClick()
    composeTestRule.waitForIdle()
  }
}
