package com.letter.server

import com.letter.contract.dto.ApiResponse
import com.letter.contract.dto.LoginRequest
import com.letter.contract.dto.RegisterRequest
import com.letter.contract.dto.SessionDto
import com.letter.contract.dto.TokenPair
import com.letter.server.test.runServerTest
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionsFlowTest {

    @Test
    fun `list sessions then revoke one`() = runServerTest { client ->
        val email = "sess@example.com"
        // 注册 -> session #1
        val s1 = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = email, password = "password123", displayName = "Sess"))
        }.body<ApiResponse<TokenPair>>().data

        // 再登录两次(不同设备) -> session #2,#3
        val s2 = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = email, password = "password123", deviceName = "Phone", platform = "android"))
        }.body<ApiResponse<TokenPair>>().data

        val s3 = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = email, password = "password123", deviceName = "Desktop", platform = "desktop"))
        }.body<ApiResponse<TokenPair>>().data

        assertEquals(s1.user.id, s2.user.id)
        assertEquals(s1.user.id, s3.user.id)

        // 用 s1 的 access token 列 sessions
        val sessions = client.get("/api/v1/me/sessions") {
            bearerAuth(s1.accessToken)
        }.body<ApiResponse<List<SessionDto>>>().data
        assertEquals(3, sessions.size)
        val phone = sessions.firstOrNull { it.deviceName == "Phone" }
        val desktop = sessions.firstOrNull { it.deviceName == "Desktop" }
        assertNotNull(phone)
        assertNotNull(desktop)

        // 撤销 Phone
        val del = client.delete("/api/v1/me/sessions/${phone!!.id}") {
            bearerAuth(s1.accessToken)
        }
        assertEquals(HttpStatusCode.NoContent, del.status)

        val after = client.get("/api/v1/me/sessions") {
            bearerAuth(s1.accessToken)
        }.body<ApiResponse<List<SessionDto>>>().data
        assertEquals(2, after.size)
        assertTrue(after.none { it.id == phone.id })

        // 被撤销的 refresh token 不再能刷新
        val refresh = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(com.letter.contract.dto.RefreshRequest(s2.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, refresh.status)
    }
}
