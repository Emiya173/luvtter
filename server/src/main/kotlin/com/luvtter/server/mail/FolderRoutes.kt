package com.luvtter.server.mail

import com.luvtter.contract.dto.*
import com.luvtter.server.auth.userId
import com.luvtter.server.common.parseId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.folderRoutes(service: FolderService) {
    authenticate("auth-jwt") {
        route("/api/v1/folders") {
            get {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                call.respond(ApiResponse(service.list(uid)))
            }
            post {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val req = call.receive<CreateFolderRequest>()
                call.respond(HttpStatusCode.Created, ApiResponse(service.create(uid, req)))
            }
            patch("/{id}") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                val req = call.receive<UpdateFolderRequest>()
                call.respond(ApiResponse(service.update(uid, id, req)))
            }
            delete("/{id}") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                service.delete(uid, id)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/reorder") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val req = call.receive<ReorderFoldersRequest>()
                service.reorder(uid, req.orderedIds)
                call.respond(HttpStatusCode.NoContent)
            }
        }
        post("/api/v1/letters/{id}/folder") {
            val uid = call.principal<JWTPrincipal>()!!.userId()
            val id = parseId(call.parameters["id"]!!)
            val req = call.receive<AssignFolderRequest>()
            service.assign(uid, id, req.folderId?.let(::parseId))
            call.respond(HttpStatusCode.NoContent)
        }
        post("/api/v1/letters/{id}/favorite") {
            val uid = call.principal<JWTPrincipal>()!!.userId()
            val id = parseId(call.parameters["id"]!!)
            service.favorite(uid, id)
            call.respond(HttpStatusCode.NoContent)
        }
        delete("/api/v1/letters/{id}/favorite") {
            val uid = call.principal<JWTPrincipal>()!!.userId()
            val id = parseId(call.parameters["id"]!!)
            service.unfavorite(uid, id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
