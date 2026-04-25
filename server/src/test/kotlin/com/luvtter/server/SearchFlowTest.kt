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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchFlowTest {

    @Test
    fun `bigram search matches Chinese substring and ascii words`() = runServerTest { client ->
        val sender = register(client, "search-sender@example.com", "Sender")
        val recipient = register(client, "search-recipient@example.com", "Recipient")

        val senderAddr = createRealAddress(client, sender.accessToken, "家", 31.2304, 121.4737)
        createRealAddress(client, recipient.accessToken, "家", 39.9042, 116.4074)

        val stamps = client.get("/api/v1/stamps").body<ApiResponse<List<StampDto>>>().data
        val stamp = stamps.first { it.tier == 1 }

        // 1) 寄件人写一封"今天去看了海"
        val l1 = sendLetter(
            client, sender, recipient, senderAddr, stamp.id,
            "今天去看了海,海风很大,heart Beat racing"
        )
        // 2) 再写一封"明天回家"
        val l2 = sendLetter(
            client, sender, recipient, senderAddr, stamp.id,
            "明天回家做饭"
        )

        // 等送达入库 (expedite=1s)
        delay(1500)
        // 触发 inbox 扫描以确保 delivered (search 只看 sender / recipient 的索引,不需要 delivered)
        client.get("/api/v1/inbox") { bearerAuth(recipient.accessToken) }

        // 寄件人按"海"搜索 -> l1 命中, l2 不应命中
        val r1 = client.get("/api/v1/letters/search?q=海") {
            bearerAuth(sender.accessToken)
        }.body<ApiResponse<List<LetterSummaryDto>>>().data
        assertTrue(r1.any { it.id == l1.summary.id }, "单字「海」应命中含「海风」的信")
        assertFalse(r1.any { it.id == l2.summary.id }, "「海」不应命中只含「明天回家」的信")

        // 寄件人按"回家"二字 bigram 搜 -> l2 命中
        val r2 = client.get("/api/v1/letters/search?q=回家") {
            bearerAuth(sender.accessToken)
        }.body<ApiResponse<List<LetterSummaryDto>>>().data
        assertTrue(r2.any { it.id == l2.summary.id }, "「回家」bigram 应命中「明天回家」")
        assertFalse(r2.any { it.id == l1.summary.id })

        // ASCII 大小写不敏感: "heart" -> 命中 l1
        val r3 = client.get("/api/v1/letters/search?q=HEART") {
            bearerAuth(sender.accessToken)
        }.body<ApiResponse<List<LetterSummaryDto>>>().data
        assertTrue(r3.any { it.id == l1.summary.id }, "ASCII 应大小写不敏感")

        // 多关键词混合: "海 风" 应通过 AND 仍命中 l1
        val r4 = client.get("/api/v1/letters/search?q=海风") {
            bearerAuth(sender.accessToken)
        }.body<ApiResponse<List<LetterSummaryDto>>>().data
        assertTrue(r4.any { it.id == l1.summary.id })

        // 收件人也能搜到自己收到的信
        val r5 = client.get("/api/v1/letters/search?q=回家") {
            bearerAuth(recipient.accessToken)
        }.body<ApiResponse<List<LetterSummaryDto>>>().data
        assertTrue(r5.any { it.id == l2.summary.id })

        // 完全无匹配
        val r6 = client.get("/api/v1/letters/search?q=完全没有这种东西xyz") {
            bearerAuth(sender.accessToken)
        }.body<ApiResponse<List<LetterSummaryDto>>>().data
        assertTrue(r6.isEmpty())
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
                    city = "TestCity", country = "TestCountry"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        return resp.body<ApiResponse<AddressDto>>().data
    }

    private suspend fun sendLetter(
        client: HttpClient,
        sender: RegisteredUser,
        recipient: RegisteredUser,
        senderAddress: AddressDto,
        stampId: String,
        text: String
    ): LetterDetailDto {
        val draft = client.post("/api/v1/letters/drafts") {
            contentType(ContentType.Application.Json)
            bearerAuth(sender.accessToken)
            setBody(
                CreateDraftRequest(
                    recipientHandle = recipient.user.handle,
                    senderAddressId = senderAddress.id,
                    stampId = stampId,
                    body = LetterBodyText(listOf(TextSegment(text)))
                )
            )
        }.body<ApiResponse<LetterDetailDto>>().data

        client.post("/api/v1/letters/drafts/${draft.summary.id}/send") {
            bearerAuth(sender.accessToken)
        }.body<ApiResponse<SendResultDto>>()
        client.post("/api/v1/letters/${draft.summary.id}/expedite?seconds=1") {
            bearerAuth(sender.accessToken)
        }
        return draft
    }
}
