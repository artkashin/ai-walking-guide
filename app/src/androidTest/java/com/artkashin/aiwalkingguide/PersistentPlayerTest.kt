package com.artkashin.aiwalkingguide

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.artkashin.aiwalkingguide.audio.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class PersistentPlayerTest {
    @get:Rule val compose=createComposeRule()
    @Test fun controlsStayVisibleAfterScrollAtNarrowWidthAndLargeText() {
        val plays=AtomicInteger();val seeks=AtomicLong(-1)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.8f)) {
                    Box(Modifier.width(240.dp).height(500.dp)) {
                        Scaffold(bottomBar={PersistentPlayer(PlaybackState("fixture","Очень длинное название прослушанного объекта",PlaybackPhase.COMPLETED,20000,20000,true),{plays.incrementAndGet()},{},{},{seeks.set(it)})}) { padding ->
                            Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) { repeat(30){Text("Строка $it",Modifier.height(60.dp))} }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Строка 29").performScrollTo()
        compose.onNodeWithTag("persistent-player").assertIsDisplayed()
        compose.onNodeWithContentDescription("Повторить рассказ").assertIsEnabled().assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Остановить рассказ").assertIsDisplayed()
        assertEquals(1,plays.get())
        compose.onNodeWithTag("narration-seek").performTouchInput{swipeLeft()}
        compose.runOnIdle{assertTrue("Slider gesture must dispatch seek, got ${seeks.get()}",seeks.get() in 0..20000)}
    }
}
