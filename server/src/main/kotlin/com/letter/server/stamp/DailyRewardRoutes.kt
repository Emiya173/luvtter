package com.letter.server.stamp

import com.letter.contract.dto.ApiResponse
import com.letter.server.auth.userId
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.dailyRewardRoutes(service: DailyRewardService) {
    authenticate("auth-jwt") {
        post("/api/v1/me/daily-reward") {
            val uid = call.principal<JWTPrincipal>()!!.userId()
            val tz = call.request.headers["X-User-Timezone"]
                ?: call.request.queryParameters["tz"]
            call.respond(ApiResponse(service.claim(uid, tz)))
        }
    }
}
