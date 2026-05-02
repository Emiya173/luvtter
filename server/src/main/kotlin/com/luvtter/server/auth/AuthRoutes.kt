package com.luvtter.server.auth

import com.luvtter.contract.dto.*
import com.luvtter.server.common.parseId
import com.luvtter.server.user.AddressService
import com.luvtter.server.user.ExportService
import com.luvtter.server.user.OnboardingService
import com.luvtter.server.user.UserService
import io.ktor.http.*
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

fun Route.meRoutes(
    userService: UserService,
    addressService: AddressService,
    authService: AuthService,
    onboardingService: OnboardingService,
    exportService: ExportService,
) {
    authenticate("auth-jwt") {
        route("/api/v1/me/sessions") {
            get {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                call.respond(ApiResponse(authService.listSessions(uid)))
            }
            delete("/{id}") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val id = parseId(call.parameters["id"]!!)
                authService.logoutSession(uid, id)
                call.respond(HttpStatusCode.NoContent)
            }
        }
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
            get("/onboarding-state") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                call.respond(ApiResponse(onboardingService.get(uid)))
            }
            patch("/onboarding-state") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val req = call.receive<UpdateOnboardingStateRequest>()
                call.respond(ApiResponse(onboardingService.update(uid, req)))
            }
            post("/export") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                call.respond(ApiResponse(exportService.exportForUser(uid)))
            }
        }
        get("/api/v1/users/by-handle/{handle}/addresses") {
            val handle = call.parameters["handle"].orEmpty()
            call.respond(ApiResponse(addressService.listForRecipient(handle)))
        }
    }
}
