package com.letter.server.auth

import com.letter.contract.dto.*
import com.letter.server.common.parseId
import com.letter.server.user.AddressService
import com.letter.server.user.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(authService: AuthService, userService: UserService) {
    route("/api/v1/auth") {
        post("/register") {
            val req = call.receive<RegisterRequest>()
            val tokens = authService.register(req, deviceName = null, platform = null)
            call.respond(HttpStatusCode.Created, ApiResponse(tokens))
        }
        post("/login") {
            val req = call.receive<LoginRequest>()
            call.respond(ApiResponse(authService.login(req)))
        }
        post("/refresh") {
            val req = call.receive<RefreshRequest>()
            call.respond(ApiResponse(authService.refresh(req)))
        }
        post("/logout") {
            val req = call.receive<RefreshRequest>()
            authService.logout(req.refreshToken)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

fun Route.meRoutes(userService: UserService, addressService: AddressService) {
    authenticate("auth-jwt") {
        route("/api/v1/me") {
            get {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                call.respond(ApiResponse(userService.get(uid)))
            }
            patch {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val req = call.receive<UpdateMeRequest>()
                call.respond(ApiResponse(userService.update(uid, req)))
            }
            get("/handle/available") {
                val h = call.request.queryParameters["handle"].orEmpty()
                call.respond(ApiResponse(userService.handleAvailable(h)))
            }
            post("/handle") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val req = call.receive<FinalizeHandleRequest>()
                call.respond(ApiResponse(userService.finalizeHandle(uid, req)))
            }
            post("/current-address") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val req = call.receive<SetCurrentAddressRequest>()
                call.respond(ApiResponse(userService.setCurrentAddress(uid, parseId(req.addressId))))
            }
        }
        get("/api/v1/users/by-handle/{handle}/addresses") {
            val handle = call.parameters["handle"].orEmpty()
            call.respond(ApiResponse(addressService.listForRecipient(handle)))
        }
    }
}
