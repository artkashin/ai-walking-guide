package com.artkashin.aiwalkingguide

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class GuideHomeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun homeExplainsAuthenticationStatus() {
        compose.onNodeWithText("Гид рядом").assertIsDisplayed()
        compose.onNodeWithText("Статус подключения ChatGPT").performScrollTo().performClick()
        compose.onNodeWithText("Вход пока не включён").performScrollTo().assertIsDisplayed()
    }
}
