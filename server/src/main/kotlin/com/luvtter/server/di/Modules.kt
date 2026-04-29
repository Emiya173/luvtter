package com.luvtter.server.di

import com.luvtter.server.auth.AuthService
import com.luvtter.server.auth.JwtConfig
import com.luvtter.server.auth.jwtConfig
import com.luvtter.server.mail.AttachmentService
import com.luvtter.server.mail.EventGenerator
import com.luvtter.server.mail.FolderService
import com.luvtter.server.mail.LetterService
import com.luvtter.server.stamp.CatalogService
import com.luvtter.server.stamp.DailyRewardService
import com.luvtter.server.storage.StorageConfig
import com.luvtter.server.storage.StorageService
import com.luvtter.server.storage.storageConfig
import com.luvtter.server.tasks.AsyncTaskRunner
import com.luvtter.server.tasks.OcrIndexService
import com.luvtter.server.tasks.OcrTaskQuery
import com.luvtter.server.user.AddressService
import com.luvtter.server.user.ContactService
import com.luvtter.server.user.ExportService
import com.luvtter.server.user.OnboardingService
import com.luvtter.server.user.UserService
import io.ktor.server.config.ApplicationConfig
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun configModule(config: ApplicationConfig) = module {
    single<JwtConfig> { config.jwtConfig() }
    single<StorageConfig> { config.storageConfig() }
    single<TasksConfig> {
        val poll = config.propertyOrNull("tasks.pollMillis")?.getString()?.toLongOrNull() ?: 2_000L
        // useStubOcr=true(默认): Kotlin runner 处理 ocr_index,写占位文本(测试 + 单机开发用)
        // useStubOcr=false: 让出给 Python image-worker,Kotlin runner 不再认领 ocr_index 任务
        val stub = config.propertyOrNull("tasks.useStubOcr")?.getString()?.toBoolean() ?: true
        TasksConfig(pollMillis = poll, useStubOcr = stub)
    }
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
    singleOf(::OnboardingService)
    singleOf(::ExportService)
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

val tasksModule = module {
    singleOf(::OcrIndexService)
    singleOf(::OcrTaskQuery)
    single {
        val cfg = get<TasksConfig>()
        val handled = if (cfg.useStubOcr) setOf("ocr_index") else emptySet()
        AsyncTaskRunner(get(), cfg.pollMillis, handled)
    }
}

data class TasksConfig(val pollMillis: Long, val useStubOcr: Boolean = true)

fun appModules(config: ApplicationConfig) = listOf(
    configModule(config),
    storageModule,
    authModule,
    userModule,
    stampModule,
    mailModule,
    tasksModule,
)
