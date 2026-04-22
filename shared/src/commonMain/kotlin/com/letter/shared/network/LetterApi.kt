package com.letter.shared.network

import com.letter.contract.dto.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

private val log = KotlinLogging.logger {}

class LetterApi(private val client: HttpClient) {
    suspend fun createDraft(req: CreateDraftRequest): LetterDetailDto {
        log.info { "letters.createDraft recipient=${req.recipientHandle ?: req.recipientId}" }
        return client.post("/api/v1/letters/drafts") { setBody(req) }.body<ApiResponse<LetterDetailDto>>().data
    }

    suspend fun listDrafts(): List<LetterSummaryDto> =
        client.get("/api/v1/letters/drafts").body<ApiResponse<List<LetterSummaryDto>>>().data

    suspend fun getDraft(id: String): LetterDetailDto =
        client.get("/api/v1/letters/drafts/$id").body<ApiResponse<LetterDetailDto>>().data

    suspend fun updateDraft(id: String, req: UpdateDraftRequest): LetterDetailDto =
        client.patch("/api/v1/letters/drafts/$id") { setBody(req) }.body<ApiResponse<LetterDetailDto>>().data

    suspend fun deleteDraft(id: String) {
        client.delete("/api/v1/letters/drafts/$id")
    }

    suspend fun seal(id: String, req: SealDraftRequest): LetterDetailDto =
        client.post("/api/v1/letters/drafts/$id/seal") { setBody(req) }.body<ApiResponse<LetterDetailDto>>().data

    suspend fun send(id: String): SendResultDto {
        log.info { "letters.send id=$id" }
        return client.post("/api/v1/letters/drafts/$id/send").body<ApiResponse<SendResultDto>>().data
    }

    suspend fun detail(id: String): LetterDetailDto =
        client.get("/api/v1/letters/$id").body<ApiResponse<LetterDetailDto>>().data

    suspend fun markRead(id: String) {
        client.post("/api/v1/letters/$id/read")
    }

    suspend fun hide(id: String) {
        client.post("/api/v1/letters/$id/hide")
    }

    suspend fun inbox(limit: Int = 50): List<LetterSummaryDto> =
        client.get("/api/v1/inbox") { url { parameters.append("limit", limit.toString()) } }
            .body<ApiResponse<List<LetterSummaryDto>>>().data

    suspend fun outbox(limit: Int = 50): List<LetterSummaryDto> =
        client.get("/api/v1/outbox") { url { parameters.append("limit", limit.toString()) } }
            .body<ApiResponse<List<LetterSummaryDto>>>().data
}
