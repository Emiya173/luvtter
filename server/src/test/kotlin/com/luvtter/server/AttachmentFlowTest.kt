package com.luvtter.server

import com.luvtter.contract.dto.AddPhotoAttachmentRequest
import com.luvtter.contract.dto.AddStickerRequest
import com.luvtter.contract.dto.AddressDto
import com.luvtter.contract.dto.ApiResponse
import com.luvtter.contract.dto.AttachmentDto
import com.luvtter.contract.dto.CreateAddressRequest
import com.luvtter.contract.dto.CreateDraftRequest
import com.luvtter.contract.dto.LetterBodyText
import com.luvtter.contract.dto.LetterDetailDto
import com.luvtter.contract.dto.RegisterRequest
import com.luvtter.contract.dto.StickerDto
import com.luvtter.contract.dto.TextSegment
import com.luvtter.contract.dto.TokenPair
import com.luvtter.server.test.runServerTest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AttachmentFlowTest {

    @Test
    fun `sticker + photo attachment lifecycle`() = runServerTest { client ->
        val sender = register(client, "att-sender@example.com", "Sender")
        createRealAddress(client, sender.accessToken, "家", lat = 31.23, lng = 121.47)

        val draft = client.post("/api/v1/letters/drafts") {
            contentType(ContentType.Application.Json)
            bearerAuth(sender.accessToken)
            setBody(
                CreateDraftRequest(
                    recipientHandle = null,
                    body = LetterBodyText(listOf(TextSegment("附件测试")))
                )
            )
        }.body<ApiResponse<LetterDetailDto>>().data
        val draftId = draft.summary.id

        // 贴纸
        val sticker = client.get("/api/v1/stickers").body<ApiResponse<List<StickerDto>>>().data.first()
        val addSticker = client.post("/api/v1/letters/drafts/$draftId/stickers") {
            contentType(ContentType.Application.Json)
            bearerAuth(sender.accessToken)
            setBody(AddStickerRequest(stickerId = sticker.id))
        }
        assertEquals(HttpStatusCode.Created, addSticker.status)
        val stickerAtt = addSticker.body<ApiResponse<AttachmentDto>>().data
        assertEquals("sticker", stickerAtt.attachmentType)
        assertEquals(sticker.weight, stickerAtt.weight)

        // 图片
        val addPhoto = client.post("/api/v1/letters/drafts/$draftId/attachments") {
            contentType(ContentType.Application.Json)
            bearerAuth(sender.accessToken)
            setBody(AddPhotoAttachmentRequest(mediaUrl = "https://example.com/a.png", weight = 20))
        }
        assertEquals(HttpStatusCode.Created, addPhoto.status)
        val photoAtt = addPhoto.body<ApiResponse<AttachmentDto>>().data
        assertEquals("photo", photoAtt.attachmentType)

        // 草稿详情应包含两条附件 + totalWeight 累加
        val detail = client.get("/api/v1/letters/drafts/$draftId") {
            bearerAuth(sender.accessToken)
        }.body<ApiResponse<LetterDetailDto>>().data
        assertEquals(2, detail.attachments.size)
        assertEquals(sticker.weight + 20, detail.summary.totalWeight)

        // 删除贴纸,totalWeight 回落
        val del = client.delete("/api/v1/letters/drafts/$draftId/attachments/${stickerAtt.id}") {
            bearerAuth(sender.accessToken)
        }
        assertEquals(HttpStatusCode.NoContent, del.status)
        val afterDelete = client.get("/api/v1/letters/drafts/$draftId") {
            bearerAuth(sender.accessToken)
        }.body<ApiResponse<LetterDetailDto>>().data
        assertEquals(1, afterDelete.attachments.size)
        assertEquals(20, afterDelete.summary.totalWeight)
        assertNotNull(afterDelete.attachments.firstOrNull { it.attachmentType == "photo" })
        assertTrue(afterDelete.attachments.none { it.attachmentType == "sticker" })
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
}
