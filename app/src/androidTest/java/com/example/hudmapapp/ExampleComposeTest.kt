package com.example.hudmapapp

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ExampleComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun compose_test_environment_works() {
        composeTestRule.setContent {
            Text("HUD")
        }

        composeTestRule
            .onNodeWithText("HUD")
            .assertIsDisplayed()
    }
}