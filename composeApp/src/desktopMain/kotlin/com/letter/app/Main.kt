package com.letter.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Toolkit

actual val apiBaseUrl: String = "http://localhost:8080"

fun main() = application {
    val screen = Toolkit.getDefaultToolkit().screenSize
    // 按屏幕宽度分段推荐缩放，4K/5K 自动放大
    val uiScale = when {
        screen.width >= 3840 -> 2.0f
        screen.width >= 2560 -> 1.5f
        screen.width >= 1920 -> 1.2f
        else -> 1.0f
    }
    val baseWidth = 520.dp
    val baseHeight = 820.dp

    Window(
        onCloseRequest = ::exitApplication,
        title = "信件",
        state = rememberWindowState(size = DpSize(baseWidth * uiScale, baseHeight * uiScale))
    ) {
        val baseDensity = LocalDensity.current
        val scaled = remember(baseDensity, uiScale) {
            Density(density = baseDensity.density * uiScale, fontScale = baseDensity.fontScale * uiScale)
        }
        CompositionLocalProvider(LocalDensity provides scaled) {
            App()
        }
    }
}
