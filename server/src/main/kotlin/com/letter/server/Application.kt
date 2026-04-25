package com.letter.server

import com.letter.server.auth.JwtConfig
import com.letter.server.auth.authRoutes
import com.letter.server.auth.configureAuth
import com.letter.server.auth.meRoutes
import com.letter.server.config.configureDatabase
import com.letter.server.config.configureSerialization
import com.letter.server.config.configureStatusPages
import com.letter.server.config.runMigrations
import com.letter.server.di.appModules
import com.letter.server.mail.attachmentRoutes
import com.letter.server.mail.folderRoutes
import com.letter.server.mail.letterRoutes
import com.letter.server.mail.notificationRoutes
import com.letter.server.routes.helloRoutes
import com.letter.server.stamp.catalogRoutes
import com.letter.server.stamp.dailyRewardRoutes
import com.letter.server.storage.mediaRoutes
import com.letter.server.user.addressRoutes
import com.letter.server.user.contactRoutes
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
    configureDatabase(environment.config)
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
        meRoutes(get(), get(), get())
        addressRoutes(get())
        contactRoutes(get())
        catalogRoutes(get())
        letterRoutes(get())
        folderRoutes(get())
        attachmentRoutes(get())
        mediaRoutes(get())
        notificationRoutes()
        dailyRewardRoutes(get())
    }
}
