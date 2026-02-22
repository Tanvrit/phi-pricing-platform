package com.rate.buyonline

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "PRUHealth — Buy Online",
        state = rememberWindowState(width = 430.dp, height = 900.dp)
    ) {
        BuyOnlineApp()
    }
}
