package com.letter.server

import com.letter.contract.dto.AddressDto
import com.letter.contract.dto.ApiResponse
import com.letter.contract.dto.CreateAddressRequest
import com.letter.contract.dto.CreateDraftRequest
import com.letter.contract.dto.LetterBodyText
import com.letter.contract.dto.LetterDetailDto
import com.letter.contract.dto.LetterSummaryDto
import com.letter.contract.dto.RegisterRequest
import com.letter.contract.dto.SendResultDto
import com.letter.contract.dto.SignPutRequest
import com.letter.contract.dto.SignPutResponse
import com.letter.contract.dto.SignalDto
import com.letter.contract.dto.StampDto
import com.letter.contract.dto.TextSegment
import com.letter.contract.dto.TokenPair
import com.letter.contract.dto.UploadDoneRequest
import com.letter.server.test.runServerTest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.sse.sseSession
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
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SseHeartbeatAndSignalsTest {

    private val streamJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Test
    fun `stream emits ready then ping heartbeat`() = runServerTest { client ->
        val tokens = registerUser(client, "hb@example.com")
        coroutineScope {
            val session = client.sseSession("/api/v1/notifications/stream") {
                header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
            }
            // 测试配置心跳 1s。一次性收前 2 个事件:ready + ping。
            val events = withTimeout(8_000) {
                session.incoming.take(2).toList()
            }
            val ready = events.firstOrNull { it.event == "ready" }
            val ping = events.firstOrNull { it.event == "ping" }
            assertNotNull(ready, "未收到 ready 事件: $events")
            assertTrue(ready!!.data!!.contains("heartbeatSeconds"))
            assertNotNull(ping, "未收到 ping 心跳: $events")
            assertTrue(ping!!.data!!.contains("ts"))
        }
    }

    @Test
    fun `upload_done emits signal to same user`() = runServerTest(useMinio = true) { client ->
        val tokens = registerUser(client, "ud@example.com")

        // 先签一个 PUT URL,这样 objectKey 是合法用户 key
        val signed = client.post("/api/v1/uploads/photo/sign-put") {
            contentType(ContentType.Application.Json)
            bearerAuth(tokens.accessToken)
            setBody(SignPutRequest("a.png", "image/png", 100))
        }.body<ApiResponse<SignPutResponse>>().data

        coroutineScope {
            val received = async {
                val s = client.sseSession("/api/v1/notifications/stream") {
                    header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
                }
                withTimeout(5_000) { s.incoming.first { it.event == "signal" } }
            }
            delay(200) // 让订阅落地

            val done = client.post("/api/v1/uploads/photo/done") {
                contentType(ContentType.Application.Json)
                bearerAuth(tokens.accessToken)
                setBody(UploadDoneRequest(objectKey = signed.objectKey, sizeBytes = 100))
            }
            assertEquals(HttpStatusCode.NoContent, done.status)

            val sse = received.await()
            val sig = streamJson.decodeFromString(SignalDto.serializer(), sse.data!!)
            assertEquals("upload_done", sig.type)
            assertEquals(signed.objectKey, sig.objectKey)
            assertEquals(100L, sig.sizeBytes)
        }
    }

    @Test
    fun `upload_done rejects foreign object key`() = runServerTest(useMinio = true) { client ->
        val tokens = registerUser(client, "ud2@example.com")
        val resp = client.post("/api/v1/uploads/photo/done") {
            contentType(ContentType.Application.Json)
            bearerAuth(tokens.accessToken)
            setBody(UploadDoneRequest(objectKey = "users/00000000-0000-0000-0000-000000000000/photos/x.png"))
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `markRead emits letter_read signal to sender`() = runServerTest { client ->
        val sender = registerUser(client, "lr-sender@example.com")
        val recipient = registerUser(client, "lr-recipient@example.com")
        val senderAddr = createRealAddress(client, sender.accessToken, "家", 31.23, 121.47)
        createRealAddress(client, recipient.accessToken, "家", 31.24, 121.48)
        val stamp = client.get("/api/v1/stamps").body<ApiResponse<List<StampDto>>>().data.first { it.tier == 1 }

        // 寄信
        val draft = client.post("/api/v1/letters/drafts") {
            contentType(ContentType.Application.Json)
            bearerAuth(sender.accessToken)
            setBody(
                CreateDraftRequest(
                    recipientHandle = recipient.user.handle,
                    senderAddressId = senderAddr.id,
                    stampId = stamp.id,
                    body = LetterBodyText(listOf(TextSegment("read me")))
                )
            )
        }.body<ApiResponse<LetterDetailDto>>().data
        val send = client.post("/api/v1/letters/drafts/${draft.summary.id}/send") {
            bearerAuth(sender.accessToken)
        }
        assertEquals(HttpStatusCode.OK, send.status)
        assertNotNull(send.body<ApiResponse<SendResultDto>>().data.letter.sentAt)
        client.post("/api/v1/letters/${draft.summary.id}/expedite?seconds=1") {
            bearerAuth(sender.accessToken)
        }
        delay(1500)
        // 收件人扫一下 inbox 升级 delivered
        client.get("/api/v1/inbox") { bearerAuth(recipient.accessToken) }
            .body<ApiResponse<List<LetterSummaryDto>>>()

        coroutineScope {
            val received = async {
                val s = client.sseSession("/api/v1/notifications/stream") {
                    header(HttpHeaders.Authorization, "Bearer ${sender.accessToken}")
                }
                withTimeout(5_000) { s.incoming.first { it.event == "signal" } }
            }
            delay(200)

            val read = client.post("/api/v1/letters/${draft.summary.id}/read") {
                bearerAuth(recipient.accessToken)
            }
            assertEquals(HttpStatusCode.NoContent, read.status)

            val sig = streamJson.decodeFromString(SignalDto.serializer(), received.await().data!!)
            assertEquals("letter_read", sig.type)
            assertEquals(draft.summary.id, sig.letterId)
        }
    }

    // --- helpers ---

    private data class RegisteredUser(val accessToken: String, val user: com.letter.contract.dto.UserDto)

    private suspend fun registerUser(client: HttpClient, email: String): RegisteredUser {
        val resp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = email, password = "password123", displayName = email.substringBefore('@')))
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
