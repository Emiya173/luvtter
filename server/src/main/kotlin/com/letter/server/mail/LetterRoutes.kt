package com.letter.server.mail

import com.letter.contract.dto.*
import com.letter.server.auth.userId
import com.letter.server.common.parseId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.letterRoutes(service: LetterService) {
    authenticate("auth-jwt") {
        route("/api/v1/letters") {
            // 草稿
            post("/drafts") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val req = call.receive<CreateDraftRequest>()
                call.respond(HttpStatusCode.Created, ApiResponse(service.createDraft(uid, req)))
            }
            get("/drafts") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                call.respond(ApiResponse(service.listDrafts(uid)))
            }
            get("/drafts/{id}") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                call.respond(ApiResponse(service.getDraft(uid, id)))
            }
            patch("/drafts/{id}") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                val req = call.receive<UpdateDraftRequest>()
                call.respond(ApiResponse(service.updateDraft(uid, id, req)))
            }
            delete("/drafts/{id}") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                service.deleteDraft(uid, id)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/drafts/{id}/seal") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                val req = call.receive<SealDraftRequest>()
                call.respond(ApiResponse(service.sealDraft(uid, id, req)))
            }
            post("/drafts/{id}/send") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                call.respond(ApiResponse(service.send(uid, id)))
            }

            // 详情/已读/隐藏
            get("/{id}") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                call.respond(ApiResponse(service.detail(uid, id)))
            }
            post("/{id}/read") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                service.markRead(uid, id)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/{id}/hide") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                service.hide(uid, id)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        get("/api/v1/inbox") {
            val uid = call.principal<JWTPrincipal>()!!.userId()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
            call.respond(ApiResponse(service.inbox(uid, limit)))
        }
        get("/api/v1/outbox") {
            val uid = call.principal<JWTPrincipal>()!!.userId()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
            call.respond(ApiResponse(service.outbox(uid, limit)))
        }
    }
}
