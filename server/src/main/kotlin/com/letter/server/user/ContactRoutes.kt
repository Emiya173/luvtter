package com.letter.server.user

import com.letter.contract.dto.ApiResponse
import com.letter.contract.dto.CreateBlockRequest
import com.letter.contract.dto.CreateContactRequest
import com.letter.contract.dto.UpdateContactRequest
import com.letter.server.auth.userId
import com.letter.server.common.parseId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.contactRoutes(service: ContactService) {
    authenticate("auth-jwt") {
        route("/api/v1/contacts") {
            get {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                call.respond(ApiResponse(service.list(uid)))
            }
            post {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val req = call.receive<CreateContactRequest>()
                call.respond(HttpStatusCode.Created, ApiResponse(service.create(uid, req)))
            }
            patch("/{id}") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                val req = call.receive<UpdateContactRequest>()
                call.respond(ApiResponse(service.update(uid, id, req)))
            }
            delete("/{id}") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                service.delete(uid, id)
                call.respond(HttpStatusCode.NoContent)
            }
            get("/lookup") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val handle = call.request.queryParameters["handle"].orEmpty()
                call.respond(ApiResponse(service.lookup(uid, handle)))
            }
        }
        route("/api/v1/blocks") {
            get {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                call.respond(ApiResponse(service.listBlocks(uid)))
            }
            post {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val req = call.receive<CreateBlockRequest>()
                call.respond(HttpStatusCode.Created, ApiResponse(service.block(uid, req)))
            }
            delete("/{targetId}") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val target = parseId(call.parameters["targetId"]!!)
                service.unblock(uid, target)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
