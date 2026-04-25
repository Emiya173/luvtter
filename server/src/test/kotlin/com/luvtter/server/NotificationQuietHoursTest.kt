package com.luvtter.server

import com.luvtter.contract.dto.AddressDto
import com.luvtter.contract.dto.ApiResponse
import com.luvtter.contract.dto.CreateAddressRequest
import com.luvtter.contract.dto.CreateDraftRequest
import com.luvtter.contract.dto.LetterBodyText
import com.luvtter.contract.dto.LetterDetailDto
import com.luvtter.contract.dto.LetterSummaryDto
import com.luvtter.contract.dto.NotificationDto
import com.luvtter.contract.dto.NotificationPrefsDto
import com.luvtter.contract.dto.RegisterRequest
import com.luvtter.contract.dto.StampDto
import com.luvtter.contract.dto.TextSegment
import com.luvtter.contract.dto.TokenPair
import com.luvtter.server.test.runServerTest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.sse.sseSession
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationQuietHoursTest {

    @Test
    fun `quiet hours suppress sse but still persist notification`() = runServerTest { client ->
        val sender = register(client, "qh-sender@example.com", "Sender")
        val recipient = register(client, "qh-recipient@example.com", "Recipient")
        val senderAddr = createRealAddress(client, sender.accessToken, "家", 31.23, 121.47)
        createRealAddress(client, recipient.accessToken, "家", 39.90, 116.40)
        val stamp = client.get("/api/v1/stamps").body<ApiResponse<List<StampDto>>>().data.first { it.tier == 1 }

        // 设置覆盖当前 UTC 小时的免打扰窗口 [now, now+2)
        val nowHour = ZonedDateTime.now(ZoneId.of("UTC")).hour
        val patchResp = client.patch("/api/v1/notifications/prefs") {
            contentType(ContentType.Application.Json)
            bearerAuth(recipient.accessToken)
            setBody(
                NotificationPrefsDto(
                    newLetter = true, postcard = true, reply = true,
                    quietStart = nowHour, quietEnd = (nowHour + 2) % 24, timezone = "UTC"
                )
            )
        }
        assertEquals(HttpStatusCode.OK, patchResp.status)
        val saved = patchResp.body<ApiResponse<NotificationPrefsDto>>().data
        assertEquals(nowHour, saved.quietStart)

        coroutineScope {
            // 静默期内 SSE 不应收到 notification 事件
            val pingJob = async {
                val session = client.sseSession("/api/v1/notifications/stream") {
                    header(HttpHeaders.Authorization, "Bearer ${recipient.accessToken}")
                }
                runCatching {
                    withTimeout(3_000) {
                        session.incoming.first { it.event == "notification" }
                    }
                }
            }
            delay(300)

            val draft = client.post("/api/v1/letters/drafts") {
                contentType(ContentType.Application.Json)
                bearerAuth(sender.accessToken)
                setBody(
                    CreateDraftRequest(
                        recipientHandle = recipient.user.handle,
                        senderAddressId = senderAddr.id,
                        stampId = stamp.id,
                        body = LetterBodyText(listOf(TextSegment("夜深了静悄悄")))
                    )
                )
            }.body<ApiResponse<LetterDetailDto>>().data
            client.post("/api/v1/letters/drafts/${draft.summary.id}/send") {
                bearerAuth(sender.accessToken)
            }
            client.post("/api/v1/letters/${draft.summary.id}/expedite?seconds=1") {
                bearerAuth(sender.accessToken)
            }
            delay(1500)
            client.get("/api/v1/inbox") { bearerAuth(recipient.accessToken) }

            val sseResult = pingJob.await()
            assertTrue(
                sseResult.exceptionOrNull() is TimeoutCancellationException,
                "免打扰期间 SSE 不应收到 notification 事件,实际: ${sseResult.exceptionOrNull()}"
            )

            // 通知应已落库
            val list = client.get("/api/v1/notifications") {
                bearerAuth(recipient.accessToken)
            }.body<ApiResponse<List<NotificationDto>>>().data
            assertTrue(
                list.any { it.type == "new_letter" && it.letterId == draft.summary.id },
                "通知本身应仍然落库,以便用户稍后查看"
            )
        }
    }

    @Test
    fun `quiet hours off delivers sse normally`() = runServerTest { client ->
        val sender = register(client, "qh2-sender@example.com", "Sender")
        val recipient = register(client, "qh2-recipient@example.com", "Recipient")
        val senderAddr = createRealAddress(client, sender.accessToken, "家", 31.23, 121.47)
        createRealAddress(client, recipient.accessToken, "家", 39.90, 116.40)
        val stamp = client.get("/api/v1/stamps").body<ApiResponse<List<StampDto>>>().data.first { it.tier == 1 }

        // 设置一个肯定不在当前小时的窗口
        val nowHour = ZonedDateTime.now(ZoneId.of("UTC")).hour
        val nonOverlap = (nowHour + 6) % 24
        client.patch("/api/v1/notifications/prefs") {
            contentType(ContentType.Application.Json)
            bearerAuth(recipient.accessToken)
            setBody(
                NotificationPrefsDto(
                    newLetter = true, postcard = true, reply = true,
                    quietStart = nonOverlap, quietEnd = (nonOverlap + 1) % 24, timezone = "UTC"
                )
            )
        }

        coroutineScope {
            val received = async {
                val session = client.sseSession("/api/v1/notifications/stream") {
                    header(HttpHeaders.Authorization, "Bearer ${recipient.accessToken}")
                }
                withTimeout(10_000) {
                    session.incoming.first { it.event == "notification" }
                }
            }
            delay(300)

            val draft = client.post("/api/v1/letters/drafts") {
                contentType(ContentType.Application.Json)
                bearerAuth(sender.accessToken)
                setBody(
                    CreateDraftRequest(
                        recipientHandle = recipient.user.handle,
                        senderAddressId = senderAddr.id,
                        stampId = stamp.id,
                        body = LetterBodyText(listOf(TextSegment("白日里的来信")))
                    )
                )
            }.body<ApiResponse<LetterDetailDto>>().data
            client.post("/api/v1/letters/drafts/${draft.summary.id}/send") {
                bearerAuth(sender.accessToken)
            }
            client.post("/api/v1/letters/${draft.summary.id}/expedite?seconds=1") {
                bearerAuth(sender.accessToken)
            }
            delay(1500)
            client.get("/api/v1/inbox") { bearerAuth(recipient.accessToken) }

            val event = received.await()
            assertNotNull(event.data)
        }
    }

    @Test
    fun `invalid timezone returns 422`() = runServerTest { client ->
        val u = register(client, "qh3@example.com", "U")
        val resp = client.patch("/api/v1/notifications/prefs") {
            contentType(ContentType.Application.Json)
            bearerAuth(u.accessToken)
            setBody(
                NotificationPrefsDto(
                    newLetter = true, postcard = true, reply = true,
                    quietStart = 22, quietEnd = 7, timezone = "Mars/Olympus"
                )
            )
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, resp.status)
    }

    @Test
    fun `null quiet hours clears all quiet fields`() = runServerTest { client ->
        val u = register(client, "qh4@example.com", "U")
        // 先设
        client.patch("/api/v1/notifications/prefs") {
            contentType(ContentType.Application.Json)
            bearerAuth(u.accessToken)
            setBody(NotificationPrefsDto(true, true, true, 22, 7, "UTC"))
        }
        // 再清
        val resp = client.patch("/api/v1/notifications/prefs") {
            contentType(ContentType.Application.Json)
            bearerAuth(u.accessToken)
            setBody(NotificationPrefsDto(true, true, true, null, null, "UTC"))
        }
        val saved = resp.body<ApiResponse<NotificationPrefsDto>>().data
        assertEquals(null, saved.quietStart)
        assertEquals(null, saved.quietEnd)
        assertEquals(null, saved.timezone)

        val fetched = client.get("/api/v1/notifications/prefs") {
            bearerAuth(u.accessToken)
        }.body<ApiResponse<NotificationPrefsDto>>().data
        assertEquals(null, fetched.quietStart)
        assertFalse(fetched.timezone != null)
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
