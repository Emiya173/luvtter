package com.luvtter.shared.network

import com.luvtter.contract.dto.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*

private val log = KotlinLogging.logger {}

class LetterApi(private val client: HttpClient) {
    suspend fun createDraft(req: CreateDraftRequest): LetterDetailDto {
        log.info { "letters.createDraft recipient=${req.recipientHandle ?: req.recipientId}" }
        return client.post("/api/v1/letters/drafts") { setBody(req) }.unwrap()
    }

    suspend fun listDrafts(): List<LetterSummaryDto> =
        client.get("/api/v1/letters/drafts").unwrap()

    suspend fun getDraft(id: String): LetterDetailDto =
        client.get("/api/v1/letters/drafts/$id").unwrap()

    suspend fun updateDraft(id: String, req: UpdateDraftRequest): LetterDetailDto =
        client.patch("/api/v1/letters/drafts/$id") { setBody(req) }.unwrap()

    suspend fun deleteDraft(id: String) {
        client.delete("/api/v1/letters/drafts/$id").ensureSuccess()
    }

    suspend fun seal(id: String, req: SealDraftRequest): LetterDetailDto =
        client.post("/api/v1/letters/drafts/$id/seal") { setBody(req) }.unwrap()

    suspend fun send(id: String): SendResultDto {
        log.info { "letters.send id=$id" }
        return client.post("/api/v1/letters/drafts/$id/send").unwrap()
    }

    suspend fun detail(id: String): LetterDetailDto =
        client.get("/api/v1/letters/$id").unwrap()

    suspend fun markRead(id: String) {
        client.post("/api/v1/letters/$id/read").ensureSuccess()
    }

    suspend fun hide(id: String) {
        client.post("/api/v1/letters/$id/hide").ensureSuccess()
    }

    suspend fun unhide(id: String) {
        client.post("/api/v1/letters/$id/unhide").ensureSuccess()
    }

    suspend fun inbox(limit: Int = 50, hidden: Boolean = false): List<LetterSummaryDto> =
        client.get("/api/v1/inbox") {
            url {
                parameters.append("limit", limit.toString())
                if (hidden) parameters.append("hidden", "true")
            }
        }.unwrap()

    suspend fun outbox(limit: Int = 50, hidden: Boolean = false): List<LetterSummaryDto> =
        client.get("/api/v1/outbox") {
            url {
                parameters.append("limit", limit.toString())
                if (hidden) parameters.append("hidden", "true")
            }
        }.unwrap()

    suspend fun expedite(id: String, seconds: Long = 5): LetterDetailDto {
        log.info { "letters.expedite id=$id seconds=$seconds" }
        return client.post("/api/v1/letters/$id/expedite") {
            url { parameters.append("seconds", seconds.toString()) }
        }.unwrap()
    }

    suspend fun events(id: String): List<LetterEventDto> =
        client.get("/api/v1/letters/$id/events").unwrap()

    suspend fun markEventRead(letterId: String, eventId: String) {
        client.post("/api/v1/letters/$letterId/events/$eventId/read").ensureSuccess()
    }

    suspend fun favorite(id: String) {
        client.post("/api/v1/letters/$id/favorite").ensureSuccess()
    }

    suspend fun unfavorite(id: String) {
        client.delete("/api/v1/letters/$id/favorite").ensureSuccess()
    }

    suspend fun search(query: String, limit: Int = 50): List<LetterSummaryDto> =
        client.get("/api/v1/letters/search") {
            url {
                parameters.append("q", query)
                parameters.append("limit", limit.toString())
            }
        }.unwrap()

    suspend fun favorites(limit: Int = 50): List<LetterSummaryDto> =
        client.get("/api/v1/me/favorites") { url { parameters.append("limit", limit.toString()) } }.unwrap()

    suspend fun byFolder(folderId: String, limit: Int = 50): List<LetterSummaryDto> =
        client.get("/api/v1/folders/$folderId/letters") { url { parameters.append("limit", limit.toString()) } }.unwrap()

    suspend fun listAttachments(draftId: String): List<AttachmentDto> =
        client.get("/api/v1/letters/drafts/$draftId/attachments").unwrap()

    suspend fun addPhotoAttachment(draftId: String, req: AddPhotoAttachmentRequest): AttachmentDto {
        log.info { "letters.addPhoto draft=$draftId weight=${req.weight}" }
        return client.post("/api/v1/letters/drafts/$draftId/attachments") { setBody(req) }.unwrap()
    }

    suspend fun addSticker(draftId: String, req: AddStickerRequest): AttachmentDto {
        log.info { "letters.addSticker draft=$draftId sticker=${req.stickerId}" }
        return client.post("/api/v1/letters/drafts/$draftId/stickers") { setBody(req) }.unwrap()
    }

    suspend fun deleteAttachment(draftId: String, attachmentId: String) {
        client.delete("/api/v1/letters/drafts/$draftId/attachments/$attachmentId").ensureSuccess()
    }
}
