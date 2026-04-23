package com.letter.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.letter.app.ui.*
import com.letter.shared.AppContainer

expect val apiBaseUrl: String

@Composable
fun App() {
    MaterialTheme {
        val container = remember { AppContainer(apiBaseUrl) }
        var screen by remember { mutableStateOf<Screen>(Screen.Login) }

        when (val s = screen) {
            Screen.Login -> LoginScreen(
                container = container,
                onSuccess = { screen = Screen.Home },
                onGoRegister = { screen = Screen.Register }
            )
            Screen.Register -> RegisterScreen(
                container = container,
                onSuccess = { screen = Screen.Home },
                onGoLogin = { screen = Screen.Login }
            )
            Screen.Home -> HomeScreen(
                container = container,
                onCompose = { screen = Screen.Compose },
                onAddresses = { screen = Screen.Addresses },
                onContacts = { screen = Screen.Contacts },
                onOpenLetter = { screen = Screen.LetterDetail(it) },
                onLogout = {
                    container.tokens.clear()
                    screen = Screen.Login
                }
            )
            Screen.Compose -> ComposeScreen(
                container = container,
                onSent = { screen = Screen.Home },
                onCancel = { screen = Screen.Home }
            )
            is Screen.LetterDetail -> LetterDetailScreen(
                container = container,
                letterId = s.id,
                onBack = { screen = Screen.Home }
            )
            Screen.Addresses -> AddressesScreen(
                container = container,
                onBack = { screen = Screen.Home }
            )
            Screen.Contacts -> ContactsScreen(
                container = container,
                onBack = { screen = Screen.Home }
            )
        }
    }
}
