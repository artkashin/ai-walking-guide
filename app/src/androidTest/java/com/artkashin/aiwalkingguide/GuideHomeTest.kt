package com.artkashin.aiwalkingguide

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class GuideHomeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun homeAndMuseumRequireExplicitActions() {
        compose.onNodeWithText("Гид рядом").assertIsDisplayed()
        compose.onNodeWithText("Начать прогулку").assertIsDisplayed()
        compose.onNodeWithText("Музей",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Название музея или зала").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Выбрать фотографию").performScrollTo().assertIsDisplayed()
    }
    @Test fun replayRunsWithoutLocationOrAccount() {
        compose.onNodeWithText("Настройки",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Запустить GPS-реплей без сети").performScrollTo().performClick()
        compose.onNodeWithText("GPS-реплей (не реальная позиция): найден=true, после завершения молчание=true, при отклонении найден=true. Выйти: начните обычную прогулку.").performScrollTo().assertIsDisplayed()
    }
}
