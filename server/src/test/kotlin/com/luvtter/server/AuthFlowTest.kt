package com.luvtter.server

import com.luvtter.contract.dto.ApiResponse
import com.luvtter.contract.dto.LoginRequest
import com.luvtter.contract.dto.RegisterRequest
import com.luvtter.contract.dto.TokenPair
import com.luvtter.contract.dto.UserDto
import com.luvtter.server.test.runServerTest
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue

class AuthFlowTest {

    @Test
    fun `register then login then me`() = runServerTest { client ->
        val email = "alice@example.com"
        val password = "password123"

        val registerResp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = email, password = password, displayName = "Alice"))
        }
        assertEquals(HttpStatusCode.Created, registerResp.status)
        val registerTokens = registerResp.body<ApiResponse<TokenPair>>().data
        assertTrue(registerTokens.accessToken.isNotBlank())
        assertTrue(registerTokens.refreshToken.isNotBlank())
        assertEquals("Alice", registerTokens.user.displayName)

        val loginResp = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = email, password = password))
        }
        assertEquals(HttpStatusCode.OK, loginResp.status)
        val loginTokens = loginResp.body<ApiResponse<TokenPair>>().data
        assertEquals(registerTokens.user.id, loginTokens.user.id)
        assertNotEquals(registerTokens.refreshToken, loginTokens.refreshToken)

        val meResp = client.get("/api/v1/me") {
            bearerAuth(loginTokens.accessToken)
        }
        assertEquals(HttpStatusCode.OK, meResp.status)
        val me = meResp.body<ApiResponse<UserDto>>().data
        assertEquals(registerTokens.user.id, me.id)
        assertEquals("Alice", me.displayName)
        assertTrue(me.handle.startsWith("u_"))
        assertEquals(false, me.handleFinalized)
    }

    @Test
    fun `login with wrong password returns 401`() = runServerTest { client ->
        client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = "bob@example.com", password = "password123", displayName = "Bob"))
        }
        val resp = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = "bob@example.com", password = "wrong-pass"))
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `me without token returns 401`() = runServerTest { client ->
        val resp = client.get("/api/v1/me")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }
}
