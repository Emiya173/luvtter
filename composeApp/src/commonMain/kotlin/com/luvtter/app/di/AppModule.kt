package com.luvtter.app.di

import androidx.lifecycle.SavedStateHandle
import com.luvtter.app.ui.addresses.AddressesViewModel
import com.luvtter.app.ui.auth.LoginViewModel
import com.luvtter.app.ui.auth.RegisterViewModel
import com.luvtter.app.ui.compose.ComposeViewModel
import com.luvtter.app.ui.contacts.ContactsViewModel
import com.luvtter.app.ui.home.HomeViewModel
import com.luvtter.app.ui.home.SearchViewModel
import com.luvtter.app.ui.letter.LetterDetailViewModel
import com.luvtter.app.ui.sessions.SessionsViewModel
import com.luvtter.shared.auth.TokenStore
import com.luvtter.shared.network.AddressApi
import com.luvtter.shared.network.AuthApi
import com.luvtter.shared.network.CatalogApi
import com.luvtter.shared.network.ContactApi
import com.luvtter.shared.network.DailyRewardApi
import com.luvtter.shared.network.FolderApi
import com.luvtter.shared.network.LetterApi
import com.luvtter.shared.network.MeApi
import com.luvtter.shared.network.NotificationApi
import com.luvtter.shared.network.createHttpClient
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun appModule(apiBaseUrl: String) = module {
    single { TokenStore() }
    single<HttpClient> {
        val tokens: TokenStore = get()
        createHttpClient(apiBaseUrl) { tokens.accessToken() }
    }

    single { AuthApi(get()) }
    single { MeApi(get()) }
    single { AddressApi(get()) }
    single { ContactApi(get()) }
    single { CatalogApi(get()) }
    single { LetterApi(get()) }
    single { FolderApi(get()) }
    single { NotificationApi(get()) }
    single { DailyRewardApi(get()) }

    viewModelOf(::LoginViewModel)
    viewModelOf(::RegisterViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::AddressesViewModel)
    viewModelOf(::ContactsViewModel)
    viewModelOf(::SessionsViewModel)

    viewModel { (handle: SavedStateHandle) ->
        ComposeViewModel(handle, get(), get(), get(), get())
    }
    viewModel { (handle: SavedStateHandle) ->
        LetterDetailViewModel(handle, get(), get(), get())
    }
}
