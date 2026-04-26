package com.luvtter.server

import com.luvtter.contract.dto.AddressDto
import com.luvtter.contract.dto.ApiResponse
import com.luvtter.contract.dto.CreateAddressRequest
import com.luvtter.contract.dto.CreateDraftRequest
import com.luvtter.contract.dto.LetterBodyText
import com.luvtter.contract.dto.LetterDetailDto
import com.luvtter.contract.dto.OnboardingStateDto
import com.luvtter.contract.dto.RegisterRequest
import com.luvtter.contract.dto.StampDto
import com.luvtter.contract.dto.TextSegment
import com.luvtter.contract.dto.TokenPair
import com.luvtter.contract.dto.UpdateOnboardingStateRequest
import com.luvtter.server.test.runServerTest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnboardingFlowTest {

    @Test
    fun `fresh user shows first letter prompt`() = runServerTest { client ->
        val u = register(client, "ob-fresh@example.com", "U")
        val state = fetchState(client, u.accessToken)
        assertFalse(state.firstLetterPromptDismissed)
        assertFalse(state.firstLetterSent)
        assertTrue(state.showFirstLetterPrompt, "全新用户必须看到首封信引导")
    }

    @Test
    fun `dismiss prompt is persistent`() = runServerTest { client ->
        val u = register(client, "ob-dismiss@example.com", "U")
        val patched = patchState(
            client, u.accessToken,
            UpdateOnboardingStateRequest(firstLetterPromptDismissed = true)
        )
        assertTrue(patched.firstLetterPromptDismissed)
        assertFalse(patched.showFirstLetterPrompt)

        // 二次拉取仍然 dismissed
        val refetched = fetchState(client, u.accessToken)
        assertTrue(refetched.firstLetterPromptDismissed)
        assertFalse(refetched.showFirstLetterPrompt)
    }

    @Test
    fun `sending first letter auto flips firstLetterSent`() = runServerTest { client ->
        val sender = register(client, "ob-sender@example.com", "Sender")
        val recipient = register(client, "ob-recipient@example.com", "Recipient")
        val senderAddr = createRealAddress(client, sender.accessToken, "家", 31.23, 121.47)
        createRealAddress(client, recipient.accessToken, "家", 39.90, 116.40)

        // 寄信前未送出
        assertFalse(fetchState(client, sender.accessToken).firstLetterSent)

        val stamp = client.get("/api/v1/stamps")
            .body<ApiResponse<List<StampDto>>>().data.first { it.tier == 1 }
        val draft = client.post("/api/v1/letters/drafts") {
            contentType(ContentType.Application.Json)
            bearerAuth(sender.accessToken)
            setBody(
                CreateDraftRequest(
                    recipientHandle = recipient.user.handle,
                    senderAddressId = senderAddr.id,
                    stampId = stamp.id,
                    body = LetterBodyText(listOf(TextSegment("第一封信")))
                )
            )
        }.body<ApiResponse<LetterDetailDto>>().data
        client.post("/api/v1/letters/drafts/${draft.summary.id}/send") {
            bearerAuth(sender.accessToken)
        }

        val after = fetchState(client, sender.accessToken)
        assertTrue(after.firstLetterSent, "寄出第一封信后服务端应该自动 flip firstLetterSent")
        assertFalse(after.showFirstLetterPrompt, "已寄出过信件就不再展示引导")
    }

    @Test
    fun `partial patch only flips supplied fields`() = runServerTest { client ->
        val u = register(client, "ob-partial@example.com", "U")
        // 先把 firstLetterSent 用 PATCH 强写为 true(不寄信也能演示),再单独 PATCH dismissed=true
        patchState(client, u.accessToken, UpdateOnboardingStateRequest(firstLetterSent = true))
        val mid = fetchState(client, u.accessToken)
        assertTrue(mid.firstLetterSent)
        assertFalse(mid.firstLetterPromptDismissed)

        patchState(client, u.accessToken, UpdateOnboardingStateRequest(firstLetterPromptDismissed = true))
        val end = fetchState(client, u.accessToken)
        assertTrue(end.firstLetterSent, "未提供的字段应保留旧值")
        assertTrue(end.firstLetterPromptDismissed)
        assertFalse(end.showFirstLetterPrompt)
    }

    // --- helpers ---

    private suspend fun fetchState(client: HttpClient, token: String): OnboardingStateDto {
        val resp = client.get("/api/v1/me/onboarding-state") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)
        return resp.body<ApiResponse<OnboardingStateDto>>().data
    }

    private suspend fun patchState(
        client: HttpClient,
        token: String,
        req: UpdateOnboardingStateRequest
    ): OnboardingStateDto {
        val resp = client.patch("/api/v1/me/onboarding-state") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(req)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        return resp.body<ApiResponse<OnboardingStateDto>>().data
    }

    private data class RegisteredUser(
        val accessToken: String,
        val user: com.luvtter.contract.dto.UserDto,
    )

    private suspend fun register(client: HttpClient, email: String, displayName: String): RegisteredUser {
        val resp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = email, password = "password123", displayName = displayName))
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        val tokens = resp.body<ApiResponse<TokenPair>>().data
        return RegisteredUser(tokens.accessToken, tokens.user)
    }

    private suspend fun createRealAddress(
        client: HttpClient,
        token: String,
        label: String,
        lat: Double,
        lng: Double,
    ): AddressDto {
        val resp = client.post("/api/v1/me/addresses") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(
                CreateAddressRequest(
                    label = label, type = "real",
                    latitude = lat, longitude = lng,
                    city = "TC", country = "TC"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        return resp.body<ApiResponse<AddressDto>>().data
    }
}
