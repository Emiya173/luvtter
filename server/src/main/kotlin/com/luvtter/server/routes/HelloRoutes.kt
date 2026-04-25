package com.luvtter.server.routes

import com.luvtter.contract.dto.ApiResponse
import com.luvtter.contract.dto.HelloResponse
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock

fun Route.helloRoutes() {
    route("/api/v1") {
        get("/hello") {
            call.respond(
                ApiResponse(
                    HelloResponse(
                        message = "Hello from letter-app server",
                        serverTime = Clock.System.now().toString(),
                        version = "0.1.0"
                    )
                )
            )
        }

        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }
    }
}
