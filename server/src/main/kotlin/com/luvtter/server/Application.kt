package com.luvtter.server

import com.luvtter.server.auth.JwtConfig
import com.luvtter.server.auth.authRoutes
import com.luvtter.server.auth.configureAuth
import com.luvtter.server.auth.meRoutes
import com.luvtter.server.config.configureDatabase
import com.luvtter.server.config.configureSerialization
import com.luvtter.server.config.configureStatusPages
import com.luvtter.server.config.runMigrations
import com.luvtter.server.di.appModules
import com.luvtter.server.mail.attachmentRoutes
import com.luvtter.server.mail.folderRoutes
import com.luvtter.server.mail.letterRoutes
import com.luvtter.server.mail.notificationRoutes
import com.luvtter.server.routes.helloRoutes
import com.luvtter.server.stamp.catalogRoutes
import com.luvtter.server.stamp.dailyRewardRoutes
import com.luvtter.server.storage.mediaRoutes
import com.luvtter.server.tasks.AsyncTaskRunner
import com.luvtter.server.user.addressRoutes
import com.luvtter.server.user.contactRoutes
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import io.ktor.server.sse.SSE
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    runMigrations(environment.config)
    val dataSource = configureDatabase(environment.config)
    monitor.subscribe(ApplicationStopped) {
        runCatching { dataSource.close() }
    }
    configureSerialization()
    configureStatusPages()

    install(Koin) {
        slf4jLogger()
        modules(appModules(environment.config))
    }

    configureAuth(get<JwtConfig>())

    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
        allowHeader("Authorization")
        allowCredentials = true
        listOf(
            io.ktor.http.HttpMethod.Get,
            io.ktor.http.HttpMethod.Post,
            io.ktor.http.HttpMethod.Patch,
            io.ktor.http.HttpMethod.Delete,
            io.ktor.http.HttpMethod.Put
        ).forEach { allowMethod(it) }
    }
    install(CallLogging)
    install(SSE)

    routing {
        helloRoutes()
        authRoutes(get(), get())
        meRoutes(get(), get(), get(), get())
        addressRoutes(get())
        contactRoutes(get())
        catalogRoutes(get())
        letterRoutes(get(), get())
        folderRoutes(get())
        attachmentRoutes(get())
        mediaRoutes(get())
        val heartbeat = environment.config.propertyOrNull("notifications.heartbeatSeconds")
            ?.getString()?.toLongOrNull() ?: 25L
        notificationRoutes(heartbeat)
        dailyRewardRoutes(get())
    }

    val taskRunner = get<AsyncTaskRunner>()
    taskRunner.start()
    monitor.subscribe(ApplicationStopped) { taskRunner.stop() }
}
