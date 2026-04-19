package com.letter.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

actual val apiBaseUrl: String = "http://localhost:8080"

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "信件",
        state = rememberWindowState(size = DpSize(480.dp, 720.dp))
    ) {
        App()
    }
}
