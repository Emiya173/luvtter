package com.luvtter.server.stamp

import com.luvtter.contract.dto.ApiResponse
import com.luvtter.server.auth.userId
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.catalogRoutes(service: CatalogService) {
    get("/api/v1/stamps") { call.respond(ApiResponse(service.listStamps())) }
    get("/api/v1/stationeries") { call.respond(ApiResponse(service.listStationeries())) }
    get("/api/v1/stickers") { call.respond(ApiResponse(service.listStickers())) }

    authenticate("auth-jwt") {
        get("/api/v1/me/assets") {
            val uid = call.principal<JWTPrincipal>()!!.userId()
            call.respond(ApiResponse(service.myAssets(uid)))
        }
    }
}
