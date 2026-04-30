package com.luvtter.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import com.luvtter.app.theme.LuvtterTheme
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.luvtter.app.di.appModule
import com.luvtter.app.navigation.AddressesRoute
import com.luvtter.app.navigation.ComposeRoute
import com.luvtter.app.navigation.ContactsRoute
import com.luvtter.app.navigation.HomeRoute
import com.luvtter.app.navigation.LetterDetailRoute
import com.luvtter.app.navigation.LetterPlaygroundRoute
import com.luvtter.app.navigation.LoginRoute
import com.luvtter.app.navigation.RegisterRoute
import com.luvtter.app.navigation.SessionsRoute
import com.luvtter.app.ui.addresses.AddressesScreen
import com.luvtter.app.ui.auth.LoginScreen
import com.luvtter.app.ui.auth.RegisterScreen
import com.luvtter.app.ui.compose.ComposeScreen
import com.luvtter.app.ui.contacts.ContactsScreen
import com.luvtter.app.ui.home.HomeScreen
import com.luvtter.app.ui.letter.LetterDetailScreen
import com.luvtter.app.ui.letter.LetterPlaygroundScreen
import com.luvtter.app.ui.sessions.SessionsScreen
import com.luvtter.shared.auth.TokenStore
import com.luvtter.shared.network.createRawHttpClient
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.PlatformContext
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

expect val apiBaseUrl: String

@Composable
fun App() {
    KoinApplication(application = {
        modules(appModule(apiBaseUrl))
    }) {
        setSingletonImageLoaderFactory { ctx: PlatformContext ->
            ImageLoader.Builder(ctx)
                .components { add(KtorNetworkFetcherFactory(httpClient = createRawHttpClient())) }
                .build()
        }
        LuvtterTheme {
            val nav = rememberNavController()
            val tokens: TokenStore = koinInject()

            NavHost(navController = nav, startDestination = LoginRoute) {
                composable<LoginRoute> {
                    LoginScreen(
                        onSuccess = {
                            nav.navigate(HomeRoute) { popUpTo(LoginRoute) { inclusive = true } }
                        },
                        onGoRegister = { nav.navigate(RegisterRoute) },
                        onPlayground = { nav.navigate(LetterPlaygroundRoute) },
                    )
                }
                composable<LetterPlaygroundRoute> {
                    LetterPlaygroundScreen(onBack = { nav.popBackStack() })
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
                        onSessions = { nav.navigate(SessionsRoute) },
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
                composable<SessionsRoute> {
                    SessionsScreen(onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
