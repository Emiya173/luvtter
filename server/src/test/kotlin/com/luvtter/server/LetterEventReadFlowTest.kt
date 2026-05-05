package com.luvtter.server

import com.luvtter.contract.dto.AddressDto
import com.luvtter.contract.dto.ApiResponse
import com.luvtter.contract.dto.CreateAddressRequest
import com.luvtter.contract.dto.CreateDraftRequest
import com.luvtter.contract.dto.ErrorResponse
import com.luvtter.contract.dto.LetterBodyText
import com.luvtter.contract.dto.LetterDetailDto
import com.luvtter.contract.dto.LetterEventDto
import com.luvtter.contract.dto.RegisterRequest
import com.luvtter.contract.dto.StampDto
import com.luvtter.contract.dto.TextSegment
import com.luvtter.contract.dto.TokenPair
import com.luvtter.server.test.PostgresContainer
import com.luvtter.server.test.runServerTest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.time.OffsetDateTime
import java.util.UUID

/**
 * 覆盖 `POST /api/v1/letters/{id}/events/{eventId}/read` 路径:
 * - 收件人/寄件人各自能标已读,readAt 持久化
 * - 二次调用幂等(read_at 不被刷新)
 * - 非寄收件人调用返回 404 LETTER_NOT_FOUND
 * - 不存在 / 跨信 eventId 不报错(UPDATE 0 行) ,但 readAt 仍为 null
 *
 * 拟真事件由 `EventGenerator` 在寄信时按距离/时长概率生成,测试里直接 INSERT 一条
 * letter_events 行,绕开非确定性。
 */
class LetterEventReadFlowTest {

    @Test
    fun `recipient marks event read then idempotent re-read`() = runServerTest { client ->
        val (sender, recipient, letterId) = setupLetter(client)

        val eventId = insertPostcardEvent(letterId)

        // 初始 readAt 应为 null
        val before = client.get("/api/v1/letters/$letterId/events") {
            bearerAuth(recipient.accessToken)
        }.body<ApiResponse<List<LetterEventDto>>>().data
        val initialEvent = before.firstOrNull { it.id == eventId.toString() }
        assertNotNull(initialEvent, "事件应可见")
        assertNull(initialEvent!!.readAt)

        // 标已读
        val resp1 = client.post("/api/v1/letters/$letterId/events/$eventId/read") {
            bearerAuth(recipient.accessToken)
        }
        assertEquals(HttpStatusCode.NoContent, resp1.status)

        val readAtFirst = readReadAt(eventId)
        assertNotNull(readAtFirst)

        // 再次调用幂等:不应抛错,read_at 不被覆盖
        val resp2 = client.post("/api/v1/letters/$letterId/events/$eventId/read") {
            bearerAuth(recipient.accessToken)
        }
        assertEquals(HttpStatusCode.NoContent, resp2.status)

        val readAtSecond = readReadAt(eventId)
        assertEquals(readAtFirst, readAtSecond, "二次调用不应刷新 read_at")

        // events 列表 readAt 现在非空
        val after = client.get("/api/v1/letters/$letterId/events") {
            bearerAuth(recipient.accessToken)
        }.body<ApiResponse<List<LetterEventDto>>>().data
        val readEvent = after.first { it.id == eventId.toString() }
        assertNotNull(readEvent.readAt)

        // 寄件人也能调用(身份校验是寄/收件人)
        val resp3 = client.post("/api/v1/letters/$letterId/events/$eventId/read") {
            bearerAuth(sender.accessToken)
        }
        assertEquals(HttpStatusCode.NoContent, resp3.status)
    }

    @Test
    fun `non-party returns 404 LETTER_NOT_FOUND`() = runServerTest { client ->
        val (_, _, letterId) = setupLetter(client)
        val outsider = register(client, "outsider@example.com", "Outsider")
        val eventId = insertPostcardEvent(letterId)

        val resp = client.post("/api/v1/letters/$letterId/events/$eventId/read") {
            bearerAuth(outsider.accessToken)
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
        assertEquals("LETTER_NOT_FOUND", resp.body<ErrorResponse>().error.code)

        // 数据库 read_at 仍为 null
        assertNull(readReadAt(eventId))
    }

    @Test
    fun `unknown eventId is no-op`() = runServerTest { client ->
        val (_, recipient, letterId) = setupLetter(client)

        val ghost = UUID.randomUUID()
        val resp = client.post("/api/v1/letters/$letterId/events/$ghost/read") {
            bearerAuth(recipient.accessToken)
        }
        // 当前实现:UPDATE 匹配 0 行也返回 204(幂等语义);不让客户端因竞态/陈旧 id 报错
        assertEquals(HttpStatusCode.NoContent, resp.status)
    }

    // --- helpers ---

    private data class RegisteredUser(
        val accessToken: String,
        val user: com.luvtter.contract.dto.UserDto,
    )

    private data class Setup(
        val sender: RegisteredUser,
        val recipient: RegisteredUser,
        val letterId: String,
    )

    private suspend fun setupLetter(client: HttpClient): Setup {
        val sender = register(client, "ev-sender@example.com", "Sender")
        val recipient = register(client, "ev-recipient@example.com", "Recipient")
        val senderAddr = createRealAddress(client, sender.accessToken, "家", 31.2304, 121.4737)
        createRealAddress(client, recipient.accessToken, "家", 39.9042, 116.4074)

        val stamps = client.get("/api/v1/stamps").body<ApiResponse<List<StampDto>>>().data
        val stamp = stamps.first { it.tier == 1 }

        val draftResp = client.post("/api/v1/letters/drafts") {
            contentType(ContentType.Application.Json)
            bearerAuth(sender.accessToken)
            setBody(
                CreateDraftRequest(
                    recipientHandle = recipient.user.handle,
                    senderAddressId = senderAddr.id,
                    stampId = stamp.id,
                    body = LetterBodyText(listOf(TextSegment("hi"))),
                ),
            )
        }
        val draft = draftResp.body<ApiResponse<LetterDetailDto>>().data

        client.post("/api/v1/letters/drafts/${draft.summary.id}/send") {
            bearerAuth(sender.accessToken)
        }
        return Setup(sender, recipient, draft.summary.id)
    }

    private fun insertPostcardEvent(letterId: String): UUID {
        val eventId = UUID.randomUUID()
        val now = OffsetDateTime.now()
        val c = PostgresContainer.container
        DriverManager.getConnection(c.jdbcUrl, c.username, c.password).use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO letter_events
                  (id, letter_id, event_type, title, content, triggered_at, visible_at, created_at)
                VALUES (?, ?::uuid, 'postcard', '测试明信片', '测试内容', ?, ?, ?)
                """.trimIndent(),
            ).use { ps ->
                ps.setObject(1, eventId)
                ps.setString(2, letterId)
                ps.setObject(3, now)
                ps.setObject(4, now.minusMinutes(1))
                ps.setObject(5, now)
                ps.executeUpdate()
            }
        }
        return eventId
    }

    private fun readReadAt(eventId: UUID): String? {
        val c = PostgresContainer.container
        DriverManager.getConnection(c.jdbcUrl, c.username, c.password).use { conn ->
            conn.prepareStatement("SELECT read_at FROM letter_events WHERE id = ?").use { ps ->
                ps.setObject(1, eventId)
                ps.executeQuery().use { rs ->
                    return if (rs.next()) rs.getString("read_at") else null
                }
            }
        }
    }

    private suspend fun register(client: HttpClient, email: String, displayName: String): RegisteredUser {
        val resp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = email, password = "password123", displayName = displayName))
        }
        assertTrue(resp.status == HttpStatusCode.Created, "register failed: ${resp.status}")
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
                    label = label,
                    type = "real",
                    latitude = lat,
                    longitude = lng,
                    city = "TestCity",
                    country = "TestCountry",
                ),
            )
        }
        return resp.body<ApiResponse<AddressDto>>().data
    }
}
