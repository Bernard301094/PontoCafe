package com.pontocafe.app.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class PontoCafeThemeSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun temaRenderizaConteudoBasico() {
        composeRule.setContent {
            PontoCafeTheme {
                Text("Ponto Café")
            }
        }

        composeRule.onNodeWithText("Ponto Café").assertIsDisplayed()
    }
}
