package com.luvtter.server

import com.luvtter.contract.dto.AddressDto
import com.luvtter.contract.dto.ApiResponse
import com.luvtter.contract.dto.CreateAddressRequest
import com.luvtter.contract.dto.CreateDraftRequest
import com.luvtter.contract.dto.ExportResultDto
import com.luvtter.contract.dto.LetterBodyText
import com.luvtter.contract.dto.LetterDetailDto
import com.luvtter.contract.dto.RegisterRequest
import com.luvtter.contract.dto.StampDto
import com.luvtter.contract.dto.TextSegment
import com.luvtter.contract.dto.TokenPair
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import com.luvtter.server.test.runServerTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient as JdkHttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.ZipInputStream

class ExportFlowTest {

    @Test
    fun `export contains both sent and received letters`() = runServerTest(useMinio = true) { client ->
        val a = register(client, "exp-a@example.com", "Alice")
        val b = register(client, "exp-b@example.com", "Bob")
        val aAddr = createAddress(client, a.accessToken, "家A", 31.23, 121.47)
        val bAddr = createAddress(client, b.accessToken, "家B", 39.90, 116.40)

        val stamp = client.get("/api/v1/stamps")
            .body<ApiResponse<List<StampDto>>>().data.first { it.tier == 1 }

        // A 寄给 B
        sendLetter(client, a.accessToken, b.user.handle, aAddr.id, stamp.id, "你好 Bob")
        // B 寄给 A
        sendLetter(client, b.accessToken, a.user.handle, bAddr.id, stamp.id, "回信 Alice")

        // A 导出
        val exportResp = client.post("/api/v1/me/export") { bearerAuth(a.accessToken) }
        assertEquals(HttpStatusCode.OK, exportResp.status)
        val result = exportResp.body<ApiResponse<ExportResultDto>>().data
        assertTrue(result.objectKey.startsWith("users/${a.user.id}/exports/"))
        assertTrue(result.objectKey.endsWith(".zip"))
        assertTrue(result.sizeBytes > 0)
        assertEquals(2, result.letterCount, "A 应同时拿到自己寄出 + 自己收到的两封")

        // 通过 presigned GET 真正下载
        val jdk = JdkHttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        val download = jdk.send(
            HttpRequest.newBuilder().uri(URI(result.downloadUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray()
        )
        assertEquals(200, download.statusCode())
        val bytes = download.body()
        assertEquals(result.sizeBytes, bytes.size.toLong())

        // 解 ZIP,核对内容
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                entries[e.name] = zin.readBytes()
                zin.closeEntry()
                e = zin.nextEntry
            }
        }
        assertNotNull(entries["manifest.json"], "ZIP 必须含 manifest.json")
        assertNotNull(entries["letters.json"], "ZIP 必须含 letters.json")

        val manifest = Json.parseToJsonElement(entries["manifest.json"]!!.decodeToString()).jsonObject
        assertEquals(a.user.id, manifest["userId"]!!.jsonPrimitive.content)
        assertEquals(2, manifest["letterCount"]!!.jsonPrimitive.content.toInt())

        val letters = Json.parseToJsonElement(entries["letters.json"]!!.decodeToString()).jsonArray
        assertEquals(2, letters.size)
        // 至少应包含两条文本
        val plainTexts = letters.flatMap { it.collectSegmentTexts() }
        assertTrue(plainTexts.any { it.contains("你好 Bob") })
        assertTrue(plainTexts.any { it.contains("回信 Alice") })
    }

    @Test
    fun `empty user gets empty letters json`() = runServerTest(useMinio = true) { client ->
        val solo = register(client, "exp-solo@example.com", "Solo")
        val resp = client.post("/api/v1/me/export") { bearerAuth(solo.accessToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
        val r = resp.body<ApiResponse<ExportResultDto>>().data
        assertEquals(0, r.letterCount)
        assertTrue(r.sizeBytes > 0, "空账号也应生成 manifest+空数组")
    }

    // --- helpers ---

    private fun kotlinx.serialization.json.JsonElement.collectSegmentTexts(): List<String> {
        val obj = this as? JsonObject ?: return emptyList()
        val body = obj["body"] as? JsonObject ?: return emptyList()
        val segs = body["segments"] as? JsonArray ?: return emptyList()
        return segs.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.content }
    }

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

    private suspend fun createAddress(
        client: HttpClient, token: String, label: String, lat: Double, lng: Double
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

    private suspend fun sendLetter(
        client: HttpClient,
        token: String,
        recipientHandle: String,
        senderAddressId: String,
        stampId: String,
        body: String,
    ) {
        val draft = client.post("/api/v1/letters/drafts") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(
                CreateDraftRequest(
                    recipientHandle = recipientHandle,
                    senderAddressId = senderAddressId,
                    stampId = stampId,
                    body = LetterBodyText(listOf(TextSegment(body)))
                )
            )
        }.body<ApiResponse<LetterDetailDto>>().data
        val sendResp = client.post("/api/v1/letters/drafts/${draft.summary.id}/send") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, sendResp.status)
    }
}
