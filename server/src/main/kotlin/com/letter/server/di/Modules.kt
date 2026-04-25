package com.letter.server.di

import com.letter.server.auth.AuthService
import com.letter.server.auth.JwtConfig
import com.letter.server.auth.jwtConfig
import com.letter.server.mail.AttachmentService
import com.letter.server.mail.EventGenerator
import com.letter.server.mail.FolderService
import com.letter.server.mail.LetterService
import com.letter.server.stamp.CatalogService
import com.letter.server.stamp.DailyRewardService
import com.letter.server.storage.StorageConfig
import com.letter.server.storage.StorageService
import com.letter.server.storage.storageConfig
import com.letter.server.user.AddressService
import com.letter.server.user.ContactService
import com.letter.server.user.UserService
import io.ktor.server.config.ApplicationConfig
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun configModule(config: ApplicationConfig) = module {
    single<JwtConfig> { config.jwtConfig() }
    single<StorageConfig> { config.storageConfig() }
}

val storageModule = module {
    singleOf(::StorageService)
}

val authModule = module {
    singleOf(::AuthService)
    singleOf(::UserService)
}

val userModule = module {
    singleOf(::AddressService)
    singleOf(::ContactService)
}

val stampModule = module {
    singleOf(::CatalogService)
    singleOf(::DailyRewardService)
}

val mailModule = module {
    singleOf(::EventGenerator)
    singleOf(::LetterService)
    singleOf(::FolderService)
    singleOf(::AttachmentService)
}

fun appModules(config: ApplicationConfig) = listOf(
    configModule(config),
    storageModule,
    authModule,
    userModule,
    stampModule,
    mailModule
)
