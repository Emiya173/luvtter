package com.letter.app

sealed interface Screen {
    data object Login : Screen
    data object Register : Screen
    data object Home : Screen
    data class Compose(val replyToLetterId: String? = null, val recipientHandle: String? = null) : Screen
    data class LetterDetail(val id: String) : Screen
    data object Addresses : Screen
    data object Contacts : Screen
}
