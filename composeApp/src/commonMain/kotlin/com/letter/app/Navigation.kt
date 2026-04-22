package com.letter.app

sealed interface Screen {
    data object Login : Screen
    data object Register : Screen
    data object Home : Screen
    data object Compose : Screen
    data class LetterDetail(val id: String) : Screen
    data object Addresses : Screen
}
