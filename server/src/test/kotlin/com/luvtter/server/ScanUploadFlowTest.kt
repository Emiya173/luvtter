package com.luvtter.server

import com.luvtter.contract.dto.AddressDto
import com.luvtter.contract.dto.ApiResponse
import com.luvtter.contract.dto.CreateAddressRequest
import com.luvtter.contract.dto.CreateDraftRequest
import com.luvtter.contract.dto.LetterDetailDto
import com.luvtter.contract.dto.RegisterRequest
import com.luvtter.contract.dto.SignPutRequest
import com.luvtter.contract.dto.SignPutResponse
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient as JdkHttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class ScanUploadFlowTest {

    @Test
    fun `presign scan upload then create scan draft and detail returns signed bodyUrl`() =
        runServerTest(useMinio = true) { client ->
            val tokens = register(client, "scan@example.com", "Scan")
            val token = tokens.accessToken

            // 给寄件人建一个地址 + 备一封空收件人 (扫描信也是寄给某人,这里给自己的虚拟收件人不必要,留空 recipient 即可作为草稿)
            createRealAddress(client, token, "家", 31.23, 121.47)

            // 1) sign-put 申请扫描上传
            val payload = "fake-scan-pdf-bytes-${System.nanoTime()}".toByteArray()
            val signResp = client.post("/api/v1/uploads/scan/sign-put") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                setBody(
                    SignPutRequest(
                        filename = "letter.pdf",
                        contentType = "application/pdf",
                        sizeBytes = payload.size.toLong()
                    )
                )
            }
            assertEquals(HttpStatusCode.OK, signResp.status)
            val signed = signResp.body<ApiResponse<SignPutResponse>>().data
            assertTrue(
                signed.objectKey.startsWith("users/${tokens.user.id}/scans/"),
                "key 应在 scans/ 目录下,实际: ${signed.objectKey}"
            )
            assertTrue(signed.objectKey.endsWith(".pdf"))

            // 2) 直接 PUT 上传到 MinIO
            val jdk = JdkHttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
            val putReq = HttpRequest.newBuilder()
                .uri(URI(signed.uploadUrl))
                .header("Content-Type", "application/pdf")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build()
            val putResp = jdk.send(putReq, HttpResponse.BodyHandlers.discarding())
            assertTrue(putResp.statusCode() in 200..299, "PUT failed: ${putResp.statusCode()}")

            // 3) 用 scanObjectKey 建扫描草稿 (无收件人,留作草稿)
            val draftResp = client.post("/api/v1/letters/drafts") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                setBody(
                    CreateDraftRequest(
                        contentType = "scan",
                        scanObjectKey = signed.objectKey,
                    )
                )
            }
            assertEquals(HttpStatusCode.Created, draftResp.status)
            val draft = draftResp.body<ApiResponse<LetterDetailDto>>().data
            assertEquals("scan", draft.contentType)
            // 4) detail 应返回重新签名的 bodyUrl,可直接下载到原始字节
            val signedGet = draft.bodyUrl
            assertNotNull(signedGet, "扫描草稿的 detail 应返回签名 GET URL")
            // 验证签名 URL 不等于 objectKey 本身 (确实是 presigned)
            assertNotEquals(signed.objectKey, signedGet)

            val getReq = HttpRequest.newBuilder().uri(URI(signedGet!!)).GET().build()
            val getResp = jdk.send(getReq, HttpResponse.BodyHandlers.ofByteArray())
            assertEquals(200, getResp.statusCode())
            assertTrue(payload.contentEquals(getResp.body()), "下载到的字节应与上传一致")
        }

    @Test
    fun `scan-draft without scanObjectKey is rejected`() = runServerTest { client ->
        val tokens = register(client, "scan2@example.com", "Scan2")
        val resp = client.post("/api/v1/letters/drafts") {
            contentType(ContentType.Application.Json)
            bearerAuth(tokens.accessToken)
            setBody(CreateDraftRequest(contentType = "scan"))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, resp.status)
    }

    @Test
    fun `scan-draft with foreign objectKey is rejected`() = runServerTest { client ->
        val tokens = register(client, "scan3@example.com", "Scan3")
        val resp = client.post("/api/v1/letters/drafts") {
            contentType(ContentType.Application.Json)
            bearerAuth(tokens.accessToken)
            setBody(
                CreateDraftRequest(
                    contentType = "scan",
                    scanObjectKey = "users/00000000-0000-0000-0000-000000000000/scans/abc.pdf"
                )
            )
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, resp.status)
    }

    @Test
    fun `scan sign-put rejects non-image-non-pdf type and oversized payload`() =
        runServerTest(useMinio = true) { client ->
            val tokens = register(client, "scan4@example.com", "Scan4")
            val token = tokens.accessToken

            val bad = client.post("/api/v1/uploads/scan/sign-put") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                setBody(SignPutRequest("a.txt", "text/plain", 10))
            }
            assertEquals(HttpStatusCode.UnprocessableEntity, bad.status)

            val huge = client.post("/api/v1/uploads/scan/sign-put") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                setBody(
                    SignPutRequest(
                        filename = "huge.pdf",
                        contentType = "application/pdf",
                        sizeBytes = 100L * 1024 * 1024
                    )
                )
            }
            assertEquals(HttpStatusCode.UnprocessableEntity, huge.status)
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
