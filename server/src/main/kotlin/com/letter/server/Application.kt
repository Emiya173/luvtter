package com.letter.server

import com.letter.server.auth.AuthService
import com.letter.server.auth.authRoutes
import com.letter.server.auth.configureAuth
import com.letter.server.auth.jwtConfig
import com.letter.server.auth.meRoutes
import com.letter.server.config.configureDatabase
import com.letter.server.config.configureSerialization
import com.letter.server.config.configureStatusPages
import com.letter.server.config.runMigrations
import com.letter.server.mail.AttachmentService
import com.letter.server.mail.FolderService
import com.letter.server.mail.LetterService
import com.letter.server.mail.attachmentRoutes
import com.letter.server.mail.folderRoutes
import com.letter.server.mail.letterRoutes
import com.letter.server.mail.notificationRoutes
import com.letter.server.routes.helloRoutes
import com.letter.server.stamp.CatalogService
import com.letter.server.stamp.DailyRewardService
import com.letter.server.stamp.catalogRoutes
import com.letter.server.stamp.dailyRewardRoutes
import com.letter.server.user.AddressService
import com.letter.server.user.ContactService
import com.letter.server.user.UserService
import com.letter.server.user.addressRoutes
import com.letter.server.user.contactRoutes
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

    val jwt = environment.config.jwtConfig()
    configureAuth(jwt)

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

    val authService = AuthService(jwt)
    val userService = UserService()
    val addressService = AddressService()
    val contactService = ContactService()
    val catalogService = CatalogService()
    val letterService = LetterService()
    val folderService = FolderService()
    val attachmentService = AttachmentService()
    val dailyRewardService = DailyRewardService()

    routing {
        helloRoutes()
        authRoutes(authService, userService)
        meRoutes(userService, addressService)
        addressRoutes(addressService)
        contactRoutes(contactService)
        catalogRoutes(catalogService)
        letterRoutes(letterService)
        folderRoutes(folderService)
        attachmentRoutes(attachmentService)
        notificationRoutes()
        dailyRewardRoutes(dailyRewardService)
    }
}
