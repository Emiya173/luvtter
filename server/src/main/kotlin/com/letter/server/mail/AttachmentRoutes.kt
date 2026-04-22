package com.letter.server.mail

import com.letter.contract.dto.AddPhotoAttachmentRequest
import com.letter.contract.dto.AddStickerRequest
import com.letter.contract.dto.ApiResponse
import com.letter.server.auth.userId
import com.letter.server.common.parseId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.attachmentRoutes(service: AttachmentService) {
    authenticate("auth-jwt") {
        route("/api/v1/letters/drafts/{letterId}/attachments") {
            get {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val lid = parseId(call.parameters["letterId"]!!)
                call.respond(ApiResponse(service.list(uid, lid)))
            }
            post {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val lid = parseId(call.parameters["letterId"]!!)
                val req = call.receive<AddPhotoAttachmentRequest>()
                call.respond(HttpStatusCode.Created, ApiResponse(service.addPhoto(uid, lid, req)))
            }
            delete("/{attachmentId}") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val lid = parseId(call.parameters["letterId"]!!)
                val aid = parseId(call.parameters["attachmentId"]!!)
                service.delete(uid, lid, aid)
                call.respond(HttpStatusCode.NoContent)
            }
        }
        post("/api/v1/letters/drafts/{letterId}/stickers") {
            val uid = call.principal<JWTPrincipal>()!!.userId()
            val lid = parseId(call.parameters["letterId"]!!)
            val req = call.receive<AddStickerRequest>()
            call.respond(HttpStatusCode.Created, ApiResponse(service.addSticker(uid, lid, req)))
        }
    }
}
