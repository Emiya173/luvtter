package com.luvtter.server

import com.luvtter.contract.dto.AddressDto
import com.luvtter.contract.dto.ApiResponse
import com.luvtter.contract.dto.CreateAddressRequest
import com.luvtter.contract.dto.CreateDraftRequest
import com.luvtter.contract.dto.LetterDetailDto
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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient as JdkHttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class HandwritingUploadFlowTest {

    @Test
    fun `presign handwriting then create handwriting draft and detail returns signed bodyUrl`() =
        runServerTest(useMinio = true) { client ->
            val tokens = register(client, "hw@example.com", "HW")
            val token = tokens.accessToken

            val strokes = """{"strokes":[{"pts":[[0.1,0.2],[0.3,0.4]],"color":"#000"}]}""".toByteArray()
            val signed = client.post("/api/v1/uploads/handwriting/sign-put") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                setBody(SignPutRequest("strokes.json", "application/json", strokes.size.toLong()))
            }.let { resp ->
                assertEquals(HttpStatusCode.OK, resp.status)
                resp.body<ApiResponse<SignPutResponse>>().data
            }
            assertTrue(
                signed.objectKey.startsWith("users/${tokens.user.id}/handwriting/"),
                "key 应在 handwriting/ 目录下,实际: ${signed.objectKey}"
            )
            assertTrue(signed.objectKey.endsWith(".json"))

            val jdk = JdkHttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
            val put = jdk.send(
                HttpRequest.newBuilder().uri(URI(signed.uploadUrl))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(strokes)).build(),
                HttpResponse.BodyHandlers.discarding()
            )
            assertTrue(put.statusCode() in 200..299, "PUT failed: ${put.statusCode()}")

            // 创建手写草稿 (无收件人 = 草稿停留在 drafts)
            val draftResp = client.post("/api/v1/letters/drafts") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                setBody(
                    CreateDraftRequest(
                        contentType = "handwriting",
                        handwritingObjectKey = signed.objectKey
                    )
                )
            }
            assertEquals(HttpStatusCode.Created, draftResp.status)
            val draft = draftResp.body<ApiResponse<LetterDetailDto>>().data
            assertEquals("handwriting", draft.contentType)

            val signedGet = draft.bodyUrl
            assertNotNull(signedGet, "手写草稿 detail 应返回签名 GET URL")
            assertNotEquals(signed.objectKey, signedGet)

            val getResp = jdk.send(
                HttpRequest.newBuilder().uri(URI(signedGet!!)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()
            )
            assertEquals(200, getResp.statusCode())
            assertTrue(strokes.contentEquals(getResp.body()), "下载到的字节应与上传一致")
        }

    @Test
    fun `handwriting draft without object key is rejected`() = runServerTest { client ->
        val tokens = register(client, "hw2@example.com", "HW2")
        val resp = client.post("/api/v1/letters/drafts") {
            contentType(ContentType.Application.Json)
            bearerAuth(tokens.accessToken)
            setBody(CreateDraftRequest(contentType = "handwriting"))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, resp.status)
    }

    @Test
    fun `handwriting sign-put rejects bad content type and oversized payload`() =
        runServerTest(useMinio = true) { client ->
            val tokens = register(client, "hw3@example.com", "HW3")
            val token = tokens.accessToken

            val bad = client.post("/api/v1/uploads/handwriting/sign-put") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                setBody(SignPutRequest("a.pdf", "application/pdf", 10))
            }
            assertEquals(HttpStatusCode.UnprocessableEntity, bad.status)

            val huge = client.post("/api/v1/uploads/handwriting/sign-put") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                setBody(SignPutRequest("strokes.json", "application/json", 50L * 1024 * 1024))
            }
            assertEquals(HttpStatusCode.UnprocessableEntity, huge.status)
        }

    @Test
    fun `handwriting letter sent triggers ocr_index task and indexes placeholder text`() =
        runServerTest(useMinio = true) { client ->
            val sender = register(client, "hw-sender@example.com", "HWS")
            val recipient = register(client, "hw-recipient@example.com", "HWR")
            val senderAddr = createRealAddress(client, sender.accessToken, "家", 31.23, 121.47)
            createRealAddress(client, recipient.accessToken, "家", 39.90, 116.40)
            val stamp = client.get("/api/v1/stamps").body<ApiResponse<List<StampDto>>>().data
                .first { it.tier == 1 }

            val strokes = """{"strokes":[]}""".toByteArray()
            val signed = client.post("/api/v1/uploads/handwriting/sign-put") {
                contentType(ContentType.Application.Json)
                bearerAuth(sender.accessToken)
                setBody(SignPutRequest("s.json", "application/json", strokes.size.toLong()))
            }.body<ApiResponse<SignPutResponse>>().data
            JdkHttpClient.newHttpClient().send(
                HttpRequest.newBuilder().uri(URI(signed.uploadUrl))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(strokes)).build(),
                HttpResponse.BodyHandlers.discarding()
            )

            val draft = client.post("/api/v1/letters/drafts") {
                contentType(ContentType.Application.Json)
                bearerAuth(sender.accessToken)
                setBody(
                    CreateDraftRequest(
                        recipientHandle = recipient.user.handle,
                        senderAddressId = senderAddr.id,
                        stampId = stamp.id,
                        contentType = "handwriting",
                        handwritingObjectKey = signed.objectKey
                    )
                )
            }.body<ApiResponse<LetterDetailDto>>().data
            client.post("/api/v1/letters/drafts/${draft.summary.id}/send") {
                bearerAuth(sender.accessToken)
            }

            // 与扫描信走同一 ocr_index 任务流;5s 内完成
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
            assertEquals("done", finalStatus.status)
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
