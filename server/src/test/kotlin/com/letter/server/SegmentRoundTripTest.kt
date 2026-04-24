package com.letter.server

import com.letter.contract.dto.ApiResponse
import com.letter.contract.dto.CreateDraftRequest
import com.letter.contract.dto.LetterBodyText
import com.letter.contract.dto.LetterDetailDto
import com.letter.contract.dto.RegisterRequest
import com.letter.contract.dto.TextSegment
import com.letter.contract.dto.TokenPair
import com.letter.contract.dto.UpdateDraftRequest
import com.letter.server.test.runServerTest
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SegmentRoundTripTest {

    @Test
    fun `draft preserves multi-segment body with strikethrough`() = runServerTest { client ->
        val tokens = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = "seg@example.com", password = "password123", displayName = "Seg"))
        }.body<ApiResponse<TokenPair>>().data

        val original = listOf(
            TextSegment(text = "你好,", style = null),
            TextSegment(text = "其实我想说的是", style = "strikethrough"),
            TextSegment(text = "祝你一切都好。", style = null)
        )

        val draft = client.post("/api/v1/letters/drafts") {
            contentType(ContentType.Application.Json)
            bearerAuth(tokens.accessToken)
            setBody(
                CreateDraftRequest(
                    recipientHandle = null,
                    body = LetterBodyText(original)
                )
            )
        }.body<ApiResponse<LetterDetailDto>>().data
        assertEquals(original, draft.body?.segments)

        // 更新:追加一段新内容,并将首段也划掉
        val updated = listOf(
            TextSegment(text = "你好,", style = "strikethrough"),
            TextSegment(text = "其实我想说的是", style = "strikethrough"),
            TextSegment(text = "祝你一切都好。", style = null),
            TextSegment(text = "(附注:记得回信)", style = null)
        )
        val patched = client.patch("/api/v1/letters/drafts/${draft.summary.id}") {
            contentType(ContentType.Application.Json)
            bearerAuth(tokens.accessToken)
            setBody(UpdateDraftRequest(body = LetterBodyText(updated)))
        }.body<ApiResponse<LetterDetailDto>>().data
        assertEquals(updated, patched.body?.segments)

        // 重新 GET 验证持久化
        val reload = client.get("/api/v1/letters/drafts/${draft.summary.id}") {
            bearerAuth(tokens.accessToken)
        }
        assertEquals(HttpStatusCode.OK, reload.status)
        val reloaded = reload.body<ApiResponse<LetterDetailDto>>().data
        assertEquals(updated, reloaded.body?.segments)
    }
}
