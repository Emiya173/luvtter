package com.letter.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.letter.app.di.appModule
import com.letter.app.navigation.AddressesRoute
import com.letter.app.navigation.ComposeRoute
import com.letter.app.navigation.ContactsRoute
import com.letter.app.navigation.HomeRoute
import com.letter.app.navigation.LetterDetailRoute
import com.letter.app.navigation.LoginRoute
import com.letter.app.navigation.RegisterRoute
import com.letter.app.ui.addresses.AddressesScreen
import com.letter.app.ui.auth.LoginScreen
import com.letter.app.ui.auth.RegisterScreen
import com.letter.app.ui.compose.ComposeScreen
import com.letter.app.ui.contacts.ContactsScreen
import com.letter.app.ui.home.HomeScreen
import com.letter.app.ui.letter.LetterDetailScreen
import com.letter.shared.auth.TokenStore
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

expect val apiBaseUrl: String

@Composable
fun App() {
    KoinApplication(application = {
        modules(appModule(apiBaseUrl))
    }) {
        MaterialTheme {
            val nav = rememberNavController()
            val tokens: TokenStore = koinInject()

            NavHost(navController = nav, startDestination = LoginRoute) {
                composable<LoginRoute> {
                    LoginScreen(
                        onSuccess = {
                            nav.navigate(HomeRoute) { popUpTo(LoginRoute) { inclusive = true } }
                        },
                        onGoRegister = { nav.navigate(RegisterRoute) }
                    )
                }
                composable<RegisterRoute> {
                    RegisterScreen(
                        onSuccess = {
                            nav.navigate(HomeRoute) { popUpTo(LoginRoute) { inclusive = true } }
                        },
                        onGoLogin = { nav.popBackStack() }
                    )
                }
                composable<HomeRoute> {
                    HomeScreen(
                        onCompose = { nav.navigate(ComposeRoute()) },
                        onAddresses = { nav.navigate(AddressesRoute) },
                        onContacts = { nav.navigate(ContactsRoute) },
                        onOpenLetter = { id -> nav.navigate(LetterDetailRoute(id)) },
                        onEditDraft = { id -> nav.navigate(ComposeRoute(editDraftId = id)) },
                        onLogout = {
                            tokens.clear()
                            nav.navigate(LoginRoute) { popUpTo(0) { inclusive = true } }
                        }
                    )
                }
                composable<ComposeRoute> {
                    ComposeScreen(
                        onSent = { nav.popBackStack(HomeRoute, inclusive = false) },
                        onCancel = { nav.popBackStack() }
                    )
                }
                composable<LetterDetailRoute> { entry ->
                    val route: LetterDetailRoute = entry.toRoute()
                    LetterDetailScreen(
                        onReply = { handle ->
                            nav.navigate(ComposeRoute(replyToLetterId = route.id, recipientHandle = handle))
                        },
                        onBack = { nav.popBackStack() }
                    )
                }
                composable<AddressesRoute> {
                    AddressesScreen(onBack = { nav.popBackStack() })
                }
                composable<ContactsRoute> {
                    ContactsScreen(onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
