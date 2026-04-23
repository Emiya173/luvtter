package com.letter.app.di

import androidx.lifecycle.SavedStateHandle
import com.letter.app.ui.addresses.AddressesViewModel
import com.letter.app.ui.auth.LoginViewModel
import com.letter.app.ui.auth.RegisterViewModel
import com.letter.app.ui.compose.ComposeViewModel
import com.letter.app.ui.contacts.ContactsViewModel
import com.letter.app.ui.home.HomeViewModel
import com.letter.app.ui.home.SearchViewModel
import com.letter.app.ui.letter.LetterDetailViewModel
import com.letter.shared.auth.TokenStore
import com.letter.shared.network.AddressApi
import com.letter.shared.network.AuthApi
import com.letter.shared.network.CatalogApi
import com.letter.shared.network.ContactApi
import com.letter.shared.network.DailyRewardApi
import com.letter.shared.network.FolderApi
import com.letter.shared.network.LetterApi
import com.letter.shared.network.MeApi
import com.letter.shared.network.NotificationApi
import com.letter.shared.network.createHttpClient
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

    viewModel { (handle: SavedStateHandle) ->
        ComposeViewModel(handle, get(), get(), get(), get())
    }
    viewModel { (handle: SavedStateHandle) ->
        LetterDetailViewModel(handle, get(), get(), get())
    }
}
