package com.letter.server.user

import com.letter.contract.dto.ApiResponse
import com.letter.contract.dto.CreateAddressRequest
import com.letter.contract.dto.UpdateAddressRequest
import com.letter.server.auth.userId
import com.letter.server.common.parseId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.addressRoutes(service: AddressService) {
    authenticate("auth-jwt") {
        route("/api/v1/me/addresses") {
            get {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                call.respond(ApiResponse(service.list(uid)))
            }
            post {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val req = call.receive<CreateAddressRequest>()
                call.respond(HttpStatusCode.Created, ApiResponse(service.create(uid, req)))
            }
            patch("/{id}") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                val req = call.receive<UpdateAddressRequest>()
                call.respond(ApiResponse(service.update(uid, id, req)))
            }
            delete("/{id}") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                service.softDelete(uid, id)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/{id}/default") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                call.respond(ApiResponse(service.setDefault(uid, id)))
            }
        }
    }
    get("/api/v1/virtual-anchors") {
        call.respond(ApiResponse(service.listAnchors()))
    }
}
