package com.luvtter.server

import com.luvtter.contract.dto.AddressDto
import com.luvtter.contract.dto.ApiResponse
import com.luvtter.contract.dto.CreateAddressRequest
import com.luvtter.contract.dto.CreateDraftRequest
import com.luvtter.contract.dto.LetterBodyText
import com.luvtter.contract.dto.LetterDetailDto
import com.luvtter.contract.dto.LetterSummaryDto
import com.luvtter.contract.dto.RegisterRequest
import com.luvtter.contract.dto.SendResultDto
import com.luvtter.contract.dto.StampDto
import com.luvtter.contract.dto.TextSegment
import com.luvtter.contract.dto.TokenPair
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
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SendFlowTest {

    @Test
    fun `send then expedite then inbox`() = runServerTest { client ->
        val sender = register(client, "sender@example.com", "Sender")
        val recipient = register(client, "recipient@example.com", "Recipient")

        // 双方各自创建真实地址 (首个地址自动成为默认 + current)
        val senderAddress = createRealAddress(client, sender.accessToken, "家", lat = 31.2304, lng = 121.4737)
        val recipientAddress = createRealAddress(client, recipient.accessToken, "家", lat = 39.9042, lng = 116.4074)

        // 选择一枚可用的邮票 (默认注册后已发 50 张 tier=1)
        val stamps = client.get("/api/v1/stamps").body<ApiResponse<List<StampDto>>>().data
        val stamp = stamps.first { it.tier == 1 }

        // 创建草稿
        val draftResp = client.post("/api/v1/letters/drafts") {
            contentType(ContentType.Application.Json)
            bearerAuth(sender.accessToken)
            setBody(
                CreateDraftRequest(
                    recipientHandle = recipient.user.handle,
                    senderAddressId = senderAddress.id,
                    stampId = stamp.id,
                    body = LetterBodyText(listOf(TextSegment("你好,这是一封测试信。")))
                )
            )
        }
        assertEquals(HttpStatusCode.Created, draftResp.status)
        val draft = draftResp.body<ApiResponse<LetterDetailDto>>().data
        assertEquals("draft", draft.summary.status)

        // 寄出
        val sendResp = client.post("/api/v1/letters/drafts/${draft.summary.id}/send") {
            bearerAuth(sender.accessToken)
        }
        assertEquals(HttpStatusCode.OK, sendResp.status)
        val sendResult = sendResp.body<ApiResponse<SendResultDto>>().data
        assertEquals("in_transit", sendResult.letter.status)
        assertNotNull(sendResult.letter.sentAt)

        // 寄件箱应能看到在途信件
        val outbox = client.get("/api/v1/outbox") {
            bearerAuth(sender.accessToken)
        }.body<ApiResponse<List<LetterSummaryDto>>>().data
        assertTrue(outbox.any { it.id == draft.summary.id && it.status == "in_transit" })

        // 未送达前,收件人 inbox 不应看到
        val earlyInbox = client.get("/api/v1/inbox") {
            bearerAuth(recipient.accessToken)
        }.body<ApiResponse<List<LetterSummaryDto>>>().data
        assertTrue(earlyInbox.none { it.id == draft.summary.id })

        // 加速到 1 秒后送达
        val expediteResp = client.post("/api/v1/letters/${draft.summary.id}/expedite?seconds=1") {
            bearerAuth(sender.accessToken)
        }
        assertEquals(HttpStatusCode.OK, expediteResp.status)
        delay(1500)

        // 收件人 inbox 应触发 delivered 并看到该信
        val inbox = client.get("/api/v1/inbox") {
            bearerAuth(recipient.accessToken)
        }.body<ApiResponse<List<LetterSummaryDto>>>().data
        val arrived = inbox.firstOrNull { it.id == draft.summary.id }
        assertNotNull(arrived, "收件人未能在 inbox 中看到已送达的信")
        assertTrue(arrived!!.status in listOf("delivered", "read"))

        // 标记已读
        val readResp = client.post("/api/v1/letters/${draft.summary.id}/read") {
            bearerAuth(recipient.accessToken)
        }
        assertEquals(HttpStatusCode.NoContent, readResp.status)

        // 详情应可查看且状态为 read
        val detail = client.get("/api/v1/letters/${draft.summary.id}") {
            bearerAuth(recipient.accessToken)
        }.body<ApiResponse<LetterDetailDto>>().data
        assertEquals("read", detail.summary.status)
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
                    label = label,
                    type = "real",
                    latitude = lat,
                    longitude = lng,
                    city = "TestCity",
                    country = "TestCountry"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        return resp.body<ApiResponse<AddressDto>>().data
    }
}
