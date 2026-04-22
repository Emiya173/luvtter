package com.letter.shared.network

import com.letter.contract.dto.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

private val log = KotlinLogging.logger {}

class AuthApi(private val client: HttpClient) {
    suspend fun register(req: RegisterRequest): TokenPair {
        log.info { "auth.register email=${req.email}" }
        return client.post("/api/v1/auth/register") { setBody(req) }.body<ApiResponse<TokenPair>>().data
    }

    suspend fun login(req: LoginRequest): TokenPair {
        log.info { "auth.login email=${req.email}" }
        return client.post("/api/v1/auth/login") { setBody(req) }.body<ApiResponse<TokenPair>>().data
    }

    suspend fun refresh(req: RefreshRequest): TokenPair =
        client.post("/api/v1/auth/refresh") { setBody(req) }.body<ApiResponse<TokenPair>>().data

    suspend fun logout(req: RefreshRequest) {
        client.post("/api/v1/auth/logout") { setBody(req) }
    }
}

class MeApi(private val client: HttpClient) {
    suspend fun me(): UserDto =
        client.get("/api/v1/me").body<ApiResponse<UserDto>>().data

    suspend fun update(req: UpdateMeRequest): UserDto =
        client.patch("/api/v1/me") { setBody(req) }.body<ApiResponse<UserDto>>().data

    suspend fun handleAvailable(handle: String): HandleAvailability =
        client.get("/api/v1/me/handle/available") { url { parameters.append("handle", handle) } }
            .body<ApiResponse<HandleAvailability>>().data

    suspend fun finalizeHandle(req: FinalizeHandleRequest): UserDto =
        client.post("/api/v1/me/handle") { setBody(req) }.body<ApiResponse<UserDto>>().data
}
