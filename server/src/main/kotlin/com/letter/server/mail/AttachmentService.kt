package com.letter.server.mail

import com.letter.contract.dto.*
import com.letter.server.common.newId
import com.letter.server.common.now
import com.letter.server.common.parseId
import com.letter.server.config.ApiException
import com.letter.server.config.NotFoundException
import com.letter.server.config.ValidationException
import com.letter.server.db.LetterAttachments
import com.letter.server.db.Letters
import com.letter.server.db.Stamps
import com.letter.server.db.Stickers
import com.letter.server.storage.StorageService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

@OptIn(ExperimentalUuidApi::class)
class AttachmentService(private val storage: StorageService) {

    fun list(userId: Uuid, letterId: Uuid): List<AttachmentDto> = transaction {
        requireDraftOwner(userId, letterId)
        LetterAttachments.selectAll()
            .where { LetterAttachments.letterId eq letterId }
            .orderBy(LetterAttachments.orderIndex to SortOrder.ASC)
            .map { it.toAttachmentDto(storage) }
    }

    fun addPhoto(userId: Uuid, letterId: Uuid, req: AddPhotoAttachmentRequest): AttachmentDto = transaction {
        val letter = requireDraftOwner(userId, letterId)
        if (req.weight <= 0) throw ValidationException("重量必须为正")
        if (req.mediaUrl.isNullOrBlank() && req.objectKey.isNullOrBlank()) {
            throw ValidationException("mediaUrl 或 objectKey 必填其一")
        }
        if (!req.objectKey.isNullOrBlank() && !storage.isUserOwnedKey(userId, req.objectKey!!)) {
            throw ValidationException("无权引用该对象")
        }
        checkWeight(letter, req.weight)
        val id = newId()
        val order = nextOrder(letterId)
        LetterAttachments.insert {
            it[LetterAttachments.id] = id
            it[LetterAttachments.letterId] = letterId
            it[attachmentType] = "photo"
            it[mediaUrl] = req.mediaUrl
            it[thumbnailUrl] = req.thumbnailUrl
            it[objectKey] = req.objectKey
            it[positionX] = req.positionX
            it[positionY] = req.positionY
            it[rotation] = req.rotation
            it[weight] = req.weight
            it[orderIndex] = order
            it[createdAt] = now()
        }
        bumpWeight(letterId, req.weight)
        log.info { "attachment.addPhoto letter=$letterId weight=${req.weight} objectKey=${req.objectKey ?: "-"}" }
        LetterAttachments.selectAll().where { LetterAttachments.id eq id }.first().toAttachmentDto(storage)
    }

    fun addSticker(userId: Uuid, letterId: Uuid, req: AddStickerRequest): AttachmentDto = transaction {
        val letter = requireDraftOwner(userId, letterId)
        val stickerId = parseId(req.stickerId)
        val sticker = Stickers.selectAll().where { Stickers.id eq stickerId }.firstOrNull()
            ?: throw ValidationException("贴纸不存在")
        val w = sticker[Stickers.weight]
        checkWeight(letter, w)
        val id = newId()
        val order = nextOrder(letterId)
        LetterAttachments.insert {
            it[LetterAttachments.id] = id
            it[LetterAttachments.letterId] = letterId
            it[attachmentType] = "sticker"
            it[LetterAttachments.stickerId] = stickerId
            it[positionX] = req.positionX
            it[positionY] = req.positionY
            it[rotation] = req.rotation
            it[weight] = w
            it[orderIndex] = order
            it[createdAt] = now()
        }
        bumpWeight(letterId, w)
        log.info { "attachment.addSticker letter=$letterId stickerId=$stickerId weight=$w" }
        LetterAttachments.selectAll().where { LetterAttachments.id eq id }.first().toAttachmentDto(storage)
    }

    fun delete(userId: Uuid, letterId: Uuid, attachmentId: Uuid) = transaction {
        requireDraftOwner(userId, letterId)
        val a = LetterAttachments.selectAll()
            .where { (LetterAttachments.id eq attachmentId) and (LetterAttachments.letterId eq letterId) }
            .firstOrNull() ?: throw NotFoundException(message = "附件不存在")
        val w = a[LetterAttachments.weight]
        LetterAttachments.deleteWhere { LetterAttachments.id eq attachmentId }
        bumpWeight(letterId, -w)
        log.info { "attachment.delete letter=$letterId attachment=$attachmentId weight=-$w" }
    }

    private fun requireDraftOwner(userId: Uuid, letterId: Uuid): ResultRow {
        val letter = Letters.selectAll().where { Letters.id eq letterId }.firstOrNull()
            ?: throw NotFoundException("LETTER_NOT_FOUND", "信件不存在")
        if (letter[Letters.senderId] != userId) throw NotFoundException("LETTER_NOT_FOUND", "信件不存在")
        if (letter[Letters.status] != "draft") {
            throw ApiException("LETTER_NOT_EDITABLE", "仅草稿可添加附件", HttpStatusCode.Conflict)
        }
        return letter
    }

    private fun checkWeight(letter: ResultRow, added: Int) {
        val stampId = letter[Letters.stampId] ?: return
        val stamp = Stamps.selectAll().where { Stamps.id eq stampId }.firstOrNull() ?: return
        val cap = stamp[Stamps.weightCapacity]
        val newTotal = letter[Letters.totalWeight] + added
        if (newTotal > cap) {
            throw ApiException(
                "WEIGHT_EXCEEDED",
                "超过邮票承重(${cap}g)，请升级邮票或减少附件",
                HttpStatusCode.UnprocessableEntity
            )
        }
    }

    private fun nextOrder(letterId: Uuid): Int =
        (LetterAttachments.selectAll()
            .where { LetterAttachments.letterId eq letterId }
            .maxOfOrNull { it[LetterAttachments.orderIndex] } ?: -1) + 1

    private fun bumpWeight(letterId: Uuid, delta: Int) {
        val cur = Letters.selectAll().where { Letters.id eq letterId }.first()[Letters.totalWeight]
        Letters.update({ Letters.id eq letterId }) {
            it[totalWeight] = (cur + delta).coerceAtLeast(0)
            it[updatedAt] = now()
        }
    }

}

@OptIn(ExperimentalUuidApi::class)
fun ResultRow.toAttachmentDto(storage: StorageService): AttachmentDto {
    val key = this[LetterAttachments.objectKey]
    val signedUrl = if (!key.isNullOrBlank()) {
        runCatching { storage.presignGet(key) }.getOrNull()
    } else null
    return AttachmentDto(
        id = this[LetterAttachments.id].toString(),
        attachmentType = this[LetterAttachments.attachmentType],
        mediaUrl = signedUrl ?: this[LetterAttachments.mediaUrl],
        thumbnailUrl = this[LetterAttachments.thumbnailUrl],
        stickerId = this[LetterAttachments.stickerId]?.toString(),
        objectKey = key,
        positionX = this[LetterAttachments.positionX],
        positionY = this[LetterAttachments.positionY],
        rotation = this[LetterAttachments.rotation],
        weight = this[LetterAttachments.weight],
        orderIndex = this[LetterAttachments.orderIndex]
    )
}
