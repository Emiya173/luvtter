package com.luvtter.server.mail

import com.luvtter.contract.dto.ApiResponse
import com.luvtter.contract.dto.CreateDraftRequest
import com.luvtter.contract.dto.SealDraftRequest
import com.luvtter.contract.dto.UpdateDraftRequest
import com.luvtter.server.auth.userId
import com.luvtter.server.common.parseId
import com.luvtter.server.tasks.OcrTaskQuery
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.letterRoutes(service: LetterService, ocr: OcrTaskQuery) {
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
            post("/{id}/unhide") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                service.unhide(uid, id)
                call.respond(HttpStatusCode.NoContent)
            }
            get("/{id}/events") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                call.respond(ApiResponse(service.events(uid, id)))
            }
            get("/{id}/ocr-status") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                call.respond(ApiResponse(ocr.statusFor(uid, id)))
            }
            // 测试入口：将在途信件加速到 N 秒后送达（仅寄件人；上限 1h）
            post("/{id}/expedite") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                val seconds = call.request.queryParameters["seconds"]?.toLongOrNull() ?: 5L
                call.respond(ApiResponse(service.expedite(uid, id, seconds)))
            }
        }

        get("/api/v1/inbox") {
            val uid = call.principal<JWTPrincipal>()!!.userId()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
            val hidden = call.request.queryParameters["hidden"]?.toBooleanStrictOrNull() ?: false
            call.respond(ApiResponse(service.inbox(uid, limit, hidden)))
        }
        get("/api/v1/outbox") {
            val uid = call.principal<JWTPrincipal>()!!.userId()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
            val hidden = call.request.queryParameters["hidden"]?.toBooleanStrictOrNull() ?: false
            call.respond(ApiResponse(service.outbox(uid, limit, hidden)))
        }
        get("/api/v1/letters/search") {
            val uid = call.principal<JWTPrincipal>()!!.userId()
            val q = call.request.queryParameters["q"].orEmpty()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
            call.respond(ApiResponse(service.search(uid, q, limit)))
        }
        get("/api/v1/me/favorites") {
            val uid = call.principal<JWTPrincipal>()!!.userId()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
            call.respond(ApiResponse(service.favorites(uid, limit)))
        }
        get("/api/v1/folders/{id}/letters") {
            val uid = call.principal<JWTPrincipal>()!!.userId()
            val fid = parseId(call.parameters["id"]!!)
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
            call.respond(ApiResponse(service.byFolder(uid, fid, limit)))
        }
    }
}
