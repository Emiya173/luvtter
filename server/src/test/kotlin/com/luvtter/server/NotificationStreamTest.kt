package com.luvtter.server

import com.luvtter.contract.dto.AddressDto
import com.luvtter.contract.dto.ApiResponse
import com.luvtter.contract.dto.CreateAddressRequest
import com.luvtter.contract.dto.CreateDraftRequest
import com.luvtter.contract.dto.LetterBodyText
import com.luvtter.contract.dto.LetterDetailDto
import com.luvtter.contract.dto.LetterSummaryDto
import com.luvtter.contract.dto.NotificationDto
import com.luvtter.contract.dto.RegisterRequest
import com.luvtter.contract.dto.SendResultDto
import com.luvtter.contract.dto.StampDto
import com.luvtter.contract.dto.TextSegment
import com.luvtter.contract.dto.TokenPair
import com.luvtter.server.test.runServerTest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.sse.sseSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationStreamTest {

    private val streamJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Test
    fun `sse delivers new_letter event to recipient`() = runServerTest { client ->
        val sender = register(client, "sse-sender@example.com", "Sender")
        val recipient = register(client, "sse-recipient@example.com", "Recipient")

        val senderAddr = createRealAddress(client, sender.accessToken, "家", lat = 31.23, lng = 121.47)
        createRealAddress(client, recipient.accessToken, "家", lat = 39.90, lng = 116.40)
        val stamp = client.get("/api/v1/stamps").body<ApiResponse<List<StampDto>>>().data.first { it.tier == 1 }

        coroutineScope {
            val received = async {
                val session = client.sseSession("/api/v1/notifications/stream") {
                    header(HttpHeaders.Authorization, "Bearer ${recipient.accessToken}")
                }
                withTimeout(10_000) {
                    session.incoming.first { it.event == "notification" }
                }
            }

            // 给 SSE 订阅一点时间建立
            delay(300)

            // 以寄件人身份发一封信并加速送达
            val draft = client.post("/api/v1/letters/drafts") {
                contentType(ContentType.Application.Json)
                bearerAuth(sender.accessToken)
                setBody(
                    CreateDraftRequest(
                        recipientHandle = recipient.user.handle,
                        senderAddressId = senderAddr.id,
                        stampId = stamp.id,
                        body = LetterBodyText(listOf(TextSegment("SSE 测试信")))
                    )
                )
            }.body<ApiResponse<LetterDetailDto>>().data
            val sendResp = client.post("/api/v1/letters/drafts/${draft.summary.id}/send") {
                bearerAuth(sender.accessToken)
            }
            assertEquals(HttpStatusCode.OK, sendResp.status)
            assertNotNull(sendResp.body<ApiResponse<SendResultDto>>().data.letter.sentAt)

            client.post("/api/v1/letters/${draft.summary.id}/expedite?seconds=1") {
                bearerAuth(sender.accessToken)
            }
            delay(1500)
            // 触发 inbox 扫描 -> 升级 delivered 并 emit 通知
            client.get("/api/v1/inbox") { bearerAuth(recipient.accessToken) }
                .body<ApiResponse<List<LetterSummaryDto>>>()

            val event = received.await()
            val dto = streamJson.decodeFromString(NotificationDto.serializer(), event.data!!)
            assertEquals("new_letter", dto.type)
            assertEquals(draft.summary.id, dto.letterId)
            assertTrue(dto.title.contains("信"))
        }
    }

    // --- helpers ---

    private data class RegisteredUser(
        val accessToken: String,
        val user: com.luvtter.contract.dto.UserDto
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
        lng: Double
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
