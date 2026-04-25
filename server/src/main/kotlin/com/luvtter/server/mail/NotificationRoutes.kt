package com.luvtter.server.mail

import com.luvtter.contract.dto.ApiResponse
import com.luvtter.contract.dto.NotificationPrefsDto
import com.luvtter.contract.dto.UnreadCountDto
import com.luvtter.server.auth.userId
import com.luvtter.server.common.now
import com.luvtter.server.common.parseId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private val streamJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

fun Route.notificationRoutes(heartbeatSeconds: Long = 25L) {
    authenticate("auth-jwt") {
        sse("/api/v1/notifications/stream") {
            val uid = call.principal<JWTPrincipal>()!!.userId()
            send(ServerSentEvent(event = "ready", data = """{"heartbeatSeconds":$heartbeatSeconds}"""))
            val pingJob = launch {
                while (isActive) {
                    delay(heartbeatSeconds * 1000)
                    runCatching {
                        send(ServerSentEvent(event = "ping", data = """{"ts":"${now()}"}"""))
                    }.onFailure { return@launch }
                }
            }
            val signalJob = launch {
                NotificationService.subscribeSignals(uid).collect { sig ->
                    send(ServerSentEvent(event = "signal", data = streamJson.encodeToString(sig)))
                }
            }
            try {
                NotificationService.subscribe(uid).collect { dto ->
                    send(
                        ServerSentEvent(
                            event = "notification",
                            id = dto.id,
                            data = streamJson.encodeToString(dto)
                        )
                    )
                }
            } finally {
                pingJob.cancel()
                signalJob.cancel()
            }
        }
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
