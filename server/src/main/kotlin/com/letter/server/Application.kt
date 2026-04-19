package com.letter.server

import com.letter.server.config.configureDatabase
import com.letter.server.config.configureSerialization
import com.letter.server.config.configureStatusPages
import com.letter.server.config.runMigrations
import com.letter.server.routes.helloRoutes
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    runMigrations(environment.config)
    configureDatabase(environment.config)
    configureSerialization()
    configureStatusPages()

    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
        allowHeader("Authorization")
        allowCredentials = true
    }

    install(CallLogging)

    routing {
        helloRoutes()
    }
}
