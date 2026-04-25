package com.luvtter.server

import com.luvtter.contract.dto.AddPhotoAttachmentRequest
import com.luvtter.contract.dto.ApiResponse
import com.luvtter.contract.dto.AttachmentDto
import com.luvtter.contract.dto.CreateDraftRequest
import com.luvtter.contract.dto.LetterBodyText
import com.luvtter.contract.dto.LetterDetailDto
import com.luvtter.contract.dto.RegisterRequest
import com.luvtter.contract.dto.SignGetRequest
import com.luvtter.contract.dto.SignGetResponse
import com.luvtter.contract.dto.SignPutRequest
import com.luvtter.contract.dto.SignPutResponse
import com.luvtter.contract.dto.TextSegment
import com.luvtter.contract.dto.TokenPair
import com.luvtter.server.test.runServerTest
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient as JdkHttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class MediaUploadFlowTest {

    @Test
    fun `presign upload then attach via objectKey then re-sign on read`() = runServerTest(useMinio = true) { client ->
        // 注册用户
        val tokens = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = "media@example.com", password = "password123", displayName = "Media"))
        }.body<ApiResponse<TokenPair>>().data
        val token = tokens.accessToken

        // 1. 申请上传 URL
        val payload = "fake-image-bytes-${System.nanoTime()}".toByteArray()
        val signResp = client.post("/api/v1/uploads/photo/sign-put") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(
                SignPutRequest(
                    filename = "hello.png",
                    contentType = "image/png",
                    sizeBytes = payload.size.toLong()
                )
            )
        }
        assertEquals(HttpStatusCode.OK, signResp.status)
        val signed = signResp.body<ApiResponse<SignPutResponse>>().data
        assertTrue(signed.objectKey.startsWith("users/${tokens.user.id}/photos/"))
        assertTrue(signed.objectKey.endsWith(".png"))
        assertTrue(signed.uploadUrl.contains(signed.objectKey.substringAfterLast('/')))

        // 2. 直接 PUT 上传到 MinIO
        val jdk = JdkHttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        val putReq = HttpRequest.newBuilder()
            .uri(URI(signed.uploadUrl))
            .header("Content-Type", "image/png")
            .PUT(HttpRequest.BodyPublishers.ofByteArray(payload))
            .build()
        val putResp = jdk.send(putReq, HttpResponse.BodyHandlers.discarding())
        assertTrue(putResp.statusCode() in 200..299, "PUT failed: ${putResp.statusCode()}")

        // 3. 用 objectKey 创建草稿 + 附件
        val draft = client.post("/api/v1/letters/drafts") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(
                CreateDraftRequest(
                    recipientHandle = null,
                    body = LetterBodyText(listOf(TextSegment("照片附件")))
                )
            )
        }.body<ApiResponse<LetterDetailDto>>().data

        val addResp = client.post("/api/v1/letters/drafts/${draft.summary.id}/attachments") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(AddPhotoAttachmentRequest(objectKey = signed.objectKey, weight = 30))
        }
        assertEquals(HttpStatusCode.Created, addResp.status)
        val att = addResp.body<ApiResponse<AttachmentDto>>().data
        assertEquals(signed.objectKey, att.objectKey)
        assertNotNull(att.mediaUrl)
        // mediaUrl 是 presigned GET URL,带 X-Amz-Signature
        assertTrue(att.mediaUrl!!.contains("X-Amz-Signature"), "mediaUrl 不像 presigned URL: ${att.mediaUrl}")

        // 4. 显式 sign-get 也要可用,且只允许自己拥有的 key
        val signGet = client.post("/api/v1/uploads/photo/sign-get") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(SignGetRequest(signed.objectKey))
        }
        assertEquals(HttpStatusCode.OK, signGet.status)
        val getUrl = signGet.body<ApiResponse<SignGetResponse>>().data.url

        // 5. 通过 presigned GET URL 真正下载,字节一致
        val download = jdk.send(
            HttpRequest.newBuilder().uri(URI(getUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray()
        )
        assertEquals(200, download.statusCode())
        assertEquals(payload.toList(), download.body().toList())

        // 6. 别人的 key 拒绝签名
        val otherTokens = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = "intruder@example.com", password = "password123", displayName = "Other"))
        }.body<ApiResponse<TokenPair>>().data
        val deny = client.post("/api/v1/uploads/photo/sign-get") {
            contentType(ContentType.Application.Json)
            bearerAuth(otherTokens.accessToken)
            setBody(SignGetRequest(signed.objectKey))
        }
        assertEquals(HttpStatusCode.NotFound, deny.status)

        // 7. 越权附件引用 -> 422
        val draftOther = client.post("/api/v1/letters/drafts") {
            contentType(ContentType.Application.Json)
            bearerAuth(otherTokens.accessToken)
            setBody(
                CreateDraftRequest(
                    recipientHandle = null,
                    body = LetterBodyText(listOf(TextSegment("hi")))
                )
            )
        }.body<ApiResponse<LetterDetailDto>>().data
        val attemptAttach = client.post("/api/v1/letters/drafts/${draftOther.summary.id}/attachments") {
            contentType(ContentType.Application.Json)
            bearerAuth(otherTokens.accessToken)
            setBody(AddPhotoAttachmentRequest(objectKey = signed.objectKey, weight = 10))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, attemptAttach.status)
    }

    @Test
    fun `sign-put rejects oversized or unsupported types`() = runServerTest(useMinio = true) { client ->
        val tokens = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = "media2@example.com", password = "password123", displayName = "Media2"))
        }.body<ApiResponse<TokenPair>>().data

        val badType = client.post("/api/v1/uploads/photo/sign-put") {
            contentType(ContentType.Application.Json)
            bearerAuth(tokens.accessToken)
            setBody(SignPutRequest(filename = "a.exe", contentType = "application/x-msdownload", sizeBytes = 100))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, badType.status)

        val tooBig = client.post("/api/v1/uploads/photo/sign-put") {
            contentType(ContentType.Application.Json)
            bearerAuth(tokens.accessToken)
            setBody(SignPutRequest(filename = "big.jpg", contentType = "image/jpeg", sizeBytes = 50L * 1024 * 1024))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, tooBig.status)
    }
}
