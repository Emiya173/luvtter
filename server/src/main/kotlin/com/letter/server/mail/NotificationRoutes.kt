package com.letter.server.mail

import com.letter.contract.dto.ApiResponse
import com.letter.contract.dto.NotificationPrefsDto
import com.letter.contract.dto.UnreadCountDto
import com.letter.server.auth.userId
import com.letter.server.common.parseId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Route.notificationRoutes() {
    authenticate("auth-jwt") {
        route("/api/v1/notifications") {
            get {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50
                val list = transaction { NotificationService.list(uid, limit) }
                call.respond(ApiResponse(list))
            }
            post("/{id}/read") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                transaction { NotificationService.markRead(uid, id) }
                call.respond(HttpStatusCode.NoContent)
            }
            post("/read-all") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                transaction { NotificationService.markAllRead(uid) }
                call.respond(HttpStatusCode.NoContent)
            }
            get("/unread-count") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val n = transaction { NotificationService.unreadCount(uid) }
                call.respond(ApiResponse(UnreadCountDto(n)))
            }
            get("/prefs") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                call.respond(ApiResponse(transaction { NotificationService.getPrefs(uid) }))
            }
            patch("/prefs") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val req = call.receive<NotificationPrefsDto>()
                call.respond(ApiResponse(transaction { NotificationService.updatePrefs(uid, req) }))
            }
        }
    }
}
