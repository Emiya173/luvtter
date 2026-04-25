package com.luvtter.server

import com.luvtter.contract.dto.AddressDto
import com.luvtter.contract.dto.ApiResponse
import com.luvtter.contract.dto.CreateAddressRequest
import com.luvtter.contract.dto.CreateDraftRequest
import com.luvtter.contract.dto.LetterDetailDto
import com.luvtter.contract.dto.LetterSummaryDto
import com.luvtter.contract.dto.OcrTaskStatusDto
import com.luvtter.contract.dto.RegisterRequest
import com.luvtter.contract.dto.SignPutRequest
import com.luvtter.contract.dto.SignPutResponse
import com.luvtter.contract.dto.StampDto
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
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient as JdkHttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class OcrTaskRunnerTest {

    @Test
    fun `scan letter gets ocr_index task processed and becomes searchable`() =
        runServerTest(useMinio = true) { client ->
            val sender = register(client, "ocr-sender@example.com", "Sender")
            val recipient = register(client, "ocr-recipient@example.com", "Recipient")
            val senderAddr = createRealAddress(client, sender.accessToken, "家", 31.23, 121.47)
            createRealAddress(client, recipient.accessToken, "家", 39.90, 116.40)
            val stamp = client.get("/api/v1/stamps").body<ApiResponse<List<StampDto>>>().data
                .first { it.tier == 1 }

            // 1) sign-put + 直传一个伪 PDF 字节
            val payload = "fake-scan-${System.nanoTime()}".toByteArray()
            val signed = client.post("/api/v1/uploads/scan/sign-put") {
                contentType(ContentType.Application.Json)
                bearerAuth(sender.accessToken)
                setBody(SignPutRequest("scan.pdf", "application/pdf", payload.size.toLong()))
            }.body<ApiResponse<SignPutResponse>>().data
            val jdk = JdkHttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
            val put = jdk.send(
                HttpRequest.newBuilder().uri(URI(signed.uploadUrl))
                    .header("Content-Type", "application/pdf")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(payload)).build(),
                HttpResponse.BodyHandlers.discarding()
            )
            assertTrue(put.statusCode() in 200..299)

            // 2) 创建扫描草稿(指定收件人) → 寄出 → 触发 ocr_index 任务排队
            val draft = client.post("/api/v1/letters/drafts") {
                contentType(ContentType.Application.Json)
                bearerAuth(sender.accessToken)
                setBody(
                    CreateDraftRequest(
                        recipientHandle = recipient.user.handle,
                        senderAddressId = senderAddr.id,
                        stampId = stamp.id,
                        contentType = "scan",
                        scanObjectKey = signed.objectKey
                    )
                )
            }.body<ApiResponse<LetterDetailDto>>().data
            val sendResp = client.post("/api/v1/letters/drafts/${draft.summary.id}/send") {
                bearerAuth(sender.accessToken)
            }
            assertEquals(HttpStatusCode.OK, sendResp.status)

            // 3) 轮询 ocr-status 直到 done (poll=150ms,5s 上限够多了)
            val finalStatus = withTimeout(5_000) {
                var status: OcrTaskStatusDto
                while (true) {
                    val resp = client.get("/api/v1/letters/${draft.summary.id}/ocr-status") {
                        bearerAuth(sender.accessToken)
                    }
                    assertEquals(HttpStatusCode.OK, resp.status)
                    status = resp.body<ApiResponse<OcrTaskStatusDto>>().data
                    if (status.status == "done" || status.status == "failed") break
                    delay(100)
                }
                status
            }
            assertEquals("done", finalStatus.status, "扫描信 OCR 任务应在 5s 内完成,实际: $finalStatus")
            assertTrue(finalStatus.attempts >= 1)

            // 4) 占位 OCR 文本应进入 tsvector 索引,搜索能命中该信
            val results = client.get("/api/v1/letters/search?q=占位") {
                bearerAuth(sender.accessToken)
            }.body<ApiResponse<List<LetterSummaryDto>>>().data
            assertTrue(
                results.any { it.id == draft.summary.id },
                "占位 OCR 文本应让扫描信通过 bigram 搜索被命中,实际: ${results.map { it.id }}"
            )
        }

    @Test
    fun `text letter has no ocr task and ocr-status returns 404`() = runServerTest { client ->
        val sender = register(client, "noocr-sender@example.com", "Sender")
        val recipient = register(client, "noocr-recipient@example.com", "Recipient")
        val senderAddr = createRealAddress(client, sender.accessToken, "家", 31.23, 121.47)
        createRealAddress(client, recipient.accessToken, "家", 39.90, 116.40)
        val stamp = client.get("/api/v1/stamps").body<ApiResponse<List<StampDto>>>().data
            .first { it.tier == 1 }
        val draft = client.post("/api/v1/letters/drafts") {
            contentType(ContentType.Application.Json)
            bearerAuth(sender.accessToken)
            setBody(
                CreateDraftRequest(
                    recipientHandle = recipient.user.handle,
                    senderAddressId = senderAddr.id,
                    stampId = stamp.id,
                    body = com.luvtter.contract.dto.LetterBodyText(
                        listOf(com.luvtter.contract.dto.TextSegment("普通文本信"))
                    )
                )
            )
        }.body<ApiResponse<LetterDetailDto>>().data
        client.post("/api/v1/letters/drafts/${draft.summary.id}/send") {
            bearerAuth(sender.accessToken)
        }
        val statusResp = client.get("/api/v1/letters/${draft.summary.id}/ocr-status") {
            bearerAuth(sender.accessToken)
        }
        assertEquals(HttpStatusCode.NotFound, statusResp.status)
    }

    // --- helpers ---

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
