package com.letter.server.mail

import com.letter.contract.dto.*
import com.letter.server.auth.userRow
import com.letter.server.common.newId
import com.letter.server.common.now
import com.letter.server.common.parseId
import com.letter.server.config.ApiException
import com.letter.server.config.NotFoundException
import com.letter.server.config.ValidationException
import com.letter.server.db.*
import com.letter.server.user.toDto
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val jsonCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

@OptIn(ExperimentalUuidApi::class)
class LetterService(private val events: EventGenerator = EventGenerator()) {

    // --- 草稿 ---

    fun createDraft(senderId: Uuid, req: CreateDraftRequest): LetterDetailDto = transaction {
        val id = newId()
        val ts = now()
        val recipientId = resolveRecipient(req.recipientHandle, req.recipientId)
        Letters.insert {
            it[Letters.id] = id
            it[Letters.senderId] = senderId
            it[Letters.recipientId] = recipientId
            it[recipientAddressId] = req.recipientAddressId?.let(::parseId)
            it[senderAddressId] = req.senderAddressId?.let(::parseId)
            it[stampId] = req.stampId?.let(::parseId)
            it[stationeryId] = req.stationeryId?.let(::parseId)
            it[status] = "draft"
            it[replyToLetterId] = req.replyToLetterId?.let(::parseId)
            it[createdAt] = ts
            it[updatedAt] = ts
        }
        LetterContents.insert {
            it[letterId] = id
            it[contentType] = req.contentType
            it[fontCode] = req.fontCode
            it[bodyJson] = req.body?.let { b -> jsonCodec.encodeToJsonElement<LetterBodyText>(b) }
            it[bodyUrl] = req.bodyUrl
            it[createdAt] = ts
            it[updatedAt] = ts
        }
        loadDetail(id, senderId)
    }

    fun listDrafts(senderId: Uuid): List<LetterSummaryDto> = transaction {
        Letters.selectAll()
            .where { (Letters.senderId eq senderId) and (Letters.status eq "draft") }
            .orderBy(Letters.updatedAt to SortOrder.DESC)
            .map { buildSummary(it, senderId) }
    }

    fun getDraft(senderId: Uuid, id: Uuid): LetterDetailDto = transaction {
        val row = Letters.selectAll().where { Letters.id eq id }.firstOrNull()
            ?: throw NotFoundException("LETTER_NOT_FOUND", "信件不存在")
        if (row[Letters.senderId] != senderId || row[Letters.status] != "draft") {
            throw NotFoundException("LETTER_NOT_FOUND", "草稿不存在")
        }
        loadDetail(id, senderId)
    }

    fun updateDraft(senderId: Uuid, id: Uuid, req: UpdateDraftRequest): LetterDetailDto = transaction {
        val row = Letters.selectAll().where { Letters.id eq id }.firstOrNull()
            ?: throw NotFoundException("LETTER_NOT_FOUND", "信件不存在")
        if (row[Letters.senderId] != senderId || row[Letters.status] != "draft") {
            throw ApiException("LETTER_NOT_EDITABLE", "仅草稿可修改", HttpStatusCode.Conflict)
        }
        val sealedUntil = row[Letters.sealedUntil]
        if (sealedUntil != null && sealedUntil.isAfter(now())) {
            throw ApiException("DRAFT_SEALED", "草稿处于冷静期,暂不可编辑", HttpStatusCode.Conflict)
        }
        val ts = now()
        Letters.update({ Letters.id eq id }) {
            req.recipientHandle?.let { h ->
                it[recipientId] = resolveRecipient(h, null)
            }
            req.recipientId?.let { rid -> it[recipientId] = parseId(rid) }
            req.recipientAddressId?.let { v -> it[recipientAddressId] = parseId(v) }
            req.senderAddressId?.let { v -> it[senderAddressId] = parseId(v) }
            req.stampId?.let { v -> it[stampId] = parseId(v) }
            req.stationeryId?.let { v -> it[stationeryId] = parseId(v) }
            it[updatedAt] = ts
        }
        LetterContents.update({ LetterContents.letterId eq id }) {
            req.fontCode?.let { v -> it[fontCode] = v }
            req.body?.let { b -> it[bodyJson] = jsonCodec.encodeToJsonElement<LetterBodyText>(b) }
            req.bodyUrl?.let { v -> it[bodyUrl] = v }
            it[updatedAt] = ts
        }
        loadDetail(id, senderId)
    }

    fun deleteDraft(senderId: Uuid, id: Uuid) = transaction {
        val row = Letters.selectAll().where { Letters.id eq id }.firstOrNull()
            ?: throw NotFoundException("LETTER_NOT_FOUND", "信件不存在")
        if (row[Letters.senderId] != senderId || row[Letters.status] != "draft") {
            throw ApiException("LETTER_NOT_EDITABLE", "仅草稿可删除", HttpStatusCode.Conflict)
        }
        LetterContents.deleteWhere { LetterContents.letterId eq id }
        Letters.deleteWhere { Letters.id eq id }
    }

    fun sealDraft(senderId: Uuid, id: Uuid, req: SealDraftRequest): LetterDetailDto = transaction {
        val row = Letters.selectAll().where { Letters.id eq id }.firstOrNull()
            ?: throw NotFoundException("LETTER_NOT_FOUND", "信件不存在")
        if (row[Letters.senderId] != senderId || row[Letters.status] != "draft") {
            throw ApiException("LETTER_NOT_EDITABLE", "仅草稿可封存", HttpStatusCode.Conflict)
        }
        val until = runCatching { OffsetDateTime.parse(req.sealedUntil) }.getOrNull()
            ?: throw ValidationException("sealedUntil 需 ISO-8601 格式")
        if (until.isBefore(now())) throw ValidationException("封存时间必须在未来")
        Letters.update({ Letters.id eq id }) {
            it[sealedUntil] = until
            it[updatedAt] = now()
        }
        loadDetail(id, senderId)
    }

    // --- 寄出 ---

    fun send(senderId: Uuid, id: Uuid): SendResultDto = transaction {
        val letter = Letters.selectAll().where { Letters.id eq id }.firstOrNull()
            ?: throw NotFoundException("LETTER_NOT_FOUND", "信件不存在")
        if (letter[Letters.senderId] != senderId || letter[Letters.status] != "draft") {
            throw ApiException("LETTER_NOT_EDITABLE", "仅草稿可寄出", HttpStatusCode.Conflict)
        }
        val sealedUntil = letter[Letters.sealedUntil]
        if (sealedUntil != null && sealedUntil.isAfter(now())) {
            throw ApiException("DRAFT_SEALED", "草稿处于冷静期,暂不可寄出", HttpStatusCode.Conflict)
        }

        val recipientId = letter[Letters.recipientId]
            ?: throw ValidationException("收件人未设置")
        val stampId = letter[Letters.stampId]
            ?: throw ValidationException("未选择邮票")
        val senderAddressId = letter[Letters.senderAddressId]
            ?: throw ValidationException("未选择寄件地址")

        // 收件人规则
        val recipient = Users.selectAll().where { Users.id eq recipientId }.firstOrNull()
            ?: throw NotFoundException(message = "收件人不存在")
        val blocked = Blocks.selectAll()
            .where { (Blocks.ownerId eq recipientId) and (Blocks.targetId eq senderId) }
            .firstOrNull() != null
        if (blocked) throw ApiException("RECIPIENT_REFUSES", "收件人不接受该信件", HttpStatusCode.Forbidden)
        if (recipient[Users.onlyFriends]) {
            val isContactOfRecipient = Contacts.selectAll()
                .where { (Contacts.ownerId eq recipientId) and (Contacts.targetId eq senderId) }
                .firstOrNull() != null
            if (!isContactOfRecipient) {
                throw ApiException("RECIPIENT_REFUSES", "收件人仅接受好友来信", HttpStatusCode.Forbidden)
            }
        }

        // 邮票 & 重量
        val stamp = Stamps.selectAll().where { Stamps.id eq stampId }.firstOrNull()
            ?: throw ValidationException("邮票不存在")
        val totalWeight = letter[Letters.totalWeight]
        if (totalWeight > stamp[Stamps.weightCapacity]) {
            throw ApiException("WEIGHT_EXCEEDED", "当前重量超过所选邮票承载上限", HttpStatusCode.UnprocessableEntity)
        }
        val assetRow = UserAssets.selectAll()
            .where { (UserAssets.userId eq senderId) and (UserAssets.assetType eq "stamp") and (UserAssets.assetId eq stampId) }
            .firstOrNull()
            ?: throw ApiException("INSUFFICIENT_STAMPS", "没有该邮票", HttpStatusCode.UnprocessableEntity)
        if (assetRow[UserAssets.quantity] <= 0) {
            throw ApiException("INSUFFICIENT_STAMPS", "邮票不足", HttpStatusCode.UnprocessableEntity)
        }

        // 收件地址(寄件人不可见，但需计算距离):若未指定,取收件人默认地址
        val recipientAddressId = letter[Letters.recipientAddressId]
            ?: pickRecipientDefaultAddress(recipientId)
            ?: throw ValidationException("收件人暂无可用地址")
        val senderAddr = loadAddressPoint(senderAddressId)
        val recipientAddr = loadAddressPoint(recipientAddressId)
        val distance = distanceValue(senderAddr, recipientAddr)

        // 送达时间
        val ts = now()
        var deliveryAt = computeDeliveryAt(ts, distance, stamp[Stamps.tier])

        // 扣邮票
        UserAssets.update({ UserAssets.id eq assetRow[UserAssets.id] }) {
            it[quantity] = assetRow[UserAssets.quantity] - 1
        }

        // 更新信件状态
        Letters.update({ Letters.id eq id }) {
            it[status] = "in_transit"
            it[sentAt] = ts
            it[Letters.recipientAddressId] = recipientAddressId
            it[distanceValue] = distance
            it[Letters.deliveryAt] = deliveryAt
            it[updatedAt] = ts
        }

        // 拟真事件
        val finalDeliveryAt = events.generate(id, ts, deliveryAt, distance)
        if (finalDeliveryAt != deliveryAt) {
            deliveryAt = finalDeliveryAt
            Letters.update({ Letters.id eq id }) {
                it[Letters.deliveryAt] = finalDeliveryAt
                it[updatedAt] = ts
            }
        }

        // OCR 任务(手写/扫描)
        val contentType = LetterContents.selectAll().where { LetterContents.letterId eq id }
            .first()[LetterContents.contentType]
        if (contentType != "text") {
            AsyncTasks.insert {
                it[AsyncTasks.id] = newId()
                it[taskType] = "ocr_index"
                it[payload] = jsonCodec.parseToJsonElement("""{"letter_id":"$id"}""")
                it[scheduledAt] = ts
                it[createdAt] = ts
                it[updatedAt] = ts
            }
        } else {
            // 文本信直接索引
            val body = LetterContents.selectAll().where { LetterContents.letterId eq id }
                .first()[LetterContents.bodyJson]
            val plain = body?.let { extractPlainText(it) }.orEmpty()
            LetterContents.update({ LetterContents.letterId eq id }) {
                it[indexText] = plain
                it[updatedAt] = ts
            }
        }

        val summary = buildSummary(Letters.selectAll().where { Letters.id eq id }.first(), senderId)
        SendResultDto(
            letter = summary,
            estimatedDeliveryAt = deliveryAt.toString(),
            transitStage = transitStage(ts, ts, deliveryAt)
        )
    }

    // --- 收件 ---

    fun inbox(userId: Uuid, limit: Int = 50): List<LetterSummaryDto> = transaction {
        val nowTs = now()
        Letters.selectAll()
            .where {
                (Letters.recipientId eq userId) and
                    (Letters.status inList listOf("in_transit", "delivered", "read")) and
                    (Letters.deliveryAt lessEq nowTs) and
                    (Letters.recipientHiddenAt.isNull())
            }
            .orderBy(Letters.deliveryAt to SortOrder.DESC)
            .limit(limit)
            .map { row ->
                // 首次被查询时,将 in_transit 转为 delivered
                if (row[Letters.status] == "in_transit") {
                    Letters.update({ Letters.id eq row[Letters.id] }) {
                        it[status] = "delivered"
                        it[deliveredAt] = nowTs
                        it[updatedAt] = nowTs
                    }
                }
                buildSummary(Letters.selectAll().where { Letters.id eq row[Letters.id] }.first(), userId)
            }
    }

    fun outbox(userId: Uuid, limit: Int = 50): List<LetterSummaryDto> = transaction {
        Letters.selectAll()
            .where { (Letters.senderId eq userId) and (Letters.senderHiddenAt.isNull()) }
            .orderBy(Letters.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { buildSummary(it, userId) }
    }

    fun detail(viewerId: Uuid, id: Uuid): LetterDetailDto = transaction {
        val row = Letters.selectAll().where { Letters.id eq id }.firstOrNull()
            ?: throw NotFoundException("LETTER_NOT_FOUND", "信件不存在")
        val sender = row[Letters.senderId]
        val recipient = row[Letters.recipientId]
        if (sender != viewerId && recipient != viewerId) {
            throw NotFoundException("LETTER_NOT_FOUND", "信件不存在")
        }
        // 收件人在途时不应访问
        if (recipient == viewerId && sender != viewerId) {
            val deliveryAt = row[Letters.deliveryAt]
            if (deliveryAt == null || deliveryAt.isAfter(now())) {
                throw NotFoundException("LETTER_NOT_FOUND", "信件尚未送达")
            }
        }
        loadDetail(id, viewerId)
    }

    fun markRead(viewerId: Uuid, id: Uuid) = transaction {
        val row = Letters.selectAll().where { Letters.id eq id }.firstOrNull()
            ?: throw NotFoundException("LETTER_NOT_FOUND", "信件不存在")
        if (row[Letters.recipientId] != viewerId) return@transaction
        if (row[Letters.readAt] != null) return@transaction
        val deliveryAt = row[Letters.deliveryAt]
        if (deliveryAt == null || deliveryAt.isAfter(now())) return@transaction
        Letters.update({ Letters.id eq id }) {
            it[status] = "read"
            it[readAt] = now()
            it[updatedAt] = now()
        }
    }

    fun hide(viewerId: Uuid, id: Uuid) = transaction {
        val row = Letters.selectAll().where { Letters.id eq id }.firstOrNull()
            ?: throw NotFoundException("LETTER_NOT_FOUND", "信件不存在")
        Letters.update({ Letters.id eq id }) {
            if (row[Letters.senderId] == viewerId) it[senderHiddenAt] = now()
            if (row[Letters.recipientId] == viewerId) it[recipientHiddenAt] = now()
            it[updatedAt] = now()
        }
    }

    // --- helpers ---

    private fun resolveRecipient(handle: String?, idStr: String?): Uuid? {
        if (idStr != null) return parseId(idStr)
        if (handle.isNullOrBlank()) return null
        val row = Users.selectAll().where { Users.handle eq handle }.firstOrNull()
            ?: throw NotFoundException(message = "收件人不存在")
        return row[Users.id]
    }

    private fun pickRecipientDefaultAddress(recipientId: Uuid): Uuid? {
        val row = UserAddresses.selectAll()
            .where {
                (UserAddresses.userId eq recipientId) and
                    (UserAddresses.isDefault eq true) and
                    (UserAddresses.deletedAt.isNull())
            }
            .firstOrNull() ?: UserAddresses.selectAll()
            .where { (UserAddresses.userId eq recipientId) and (UserAddresses.deletedAt.isNull()) }
            .firstOrNull()
        return row?.get(UserAddresses.id)
    }

    private fun loadAddressPoint(addressId: Uuid): AddressPoint {
        val row = UserAddresses.selectAll().where { UserAddresses.id eq addressId }.firstOrNull()
            ?: throw ValidationException("地址不存在:$addressId")
        return when (row[UserAddresses.type]) {
            "real" -> {
                val lat = row[UserAddresses.latitude] ?: throw ValidationException("真实地址缺失坐标")
                val lng = row[UserAddresses.longitude] ?: throw ValidationException("真实地址缺失坐标")
                AddressPoint(AddressPoint.Kind.REAL, real = GeoPoint(lat, lng))
            }
            "virtual" -> {
                val anchorLat = row[UserAddresses.anchorLat]
                val anchorLng = row[UserAddresses.anchorLng]
                val anchorIdCol = row[UserAddresses.anchorId]
                val anchor: GeoPoint = when {
                    anchorLat != null && anchorLng != null -> GeoPoint(anchorLat, anchorLng)
                    anchorIdCol != null -> {
                        val a = VirtualAnchors.selectAll().where { VirtualAnchors.id eq anchorIdCol }.first()
                        GeoPoint(a[VirtualAnchors.latitude], a[VirtualAnchors.longitude])
                    }
                    else -> throw ValidationException("虚拟地址锚点缺失")
                }
                val vd = row[UserAddresses.virtualDistance] ?: 0
                AddressPoint(AddressPoint.Kind.VIRTUAL, anchor = anchor, virtualDistance = vd)
            }
            else -> throw ValidationException("未知地址类型")
        }
    }

    private fun loadDetail(id: Uuid, viewerId: Uuid): LetterDetailDto {
        val letterRow = Letters.selectAll().where { Letters.id eq id }.first()
        val content = LetterContents.selectAll().where { LetterContents.letterId eq id }.first()
        val attachments = LetterAttachments.selectAll()
            .where { LetterAttachments.letterId eq id }
            .orderBy(LetterAttachments.orderIndex to SortOrder.ASC)
            .map { a ->
                AttachmentDto(
                    id = a[LetterAttachments.id].toString(),
                    attachmentType = a[LetterAttachments.attachmentType],
                    mediaUrl = a[LetterAttachments.mediaUrl],
                    thumbnailUrl = a[LetterAttachments.thumbnailUrl],
                    stickerId = a[LetterAttachments.stickerId]?.toString(),
                    positionX = a[LetterAttachments.positionX],
                    positionY = a[LetterAttachments.positionY],
                    rotation = a[LetterAttachments.rotation],
                    weight = a[LetterAttachments.weight],
                    orderIndex = a[LetterAttachments.orderIndex]
                )
            }
        val bodyElement = content[LetterContents.bodyJson]
        val body = bodyElement?.let {
            runCatching { jsonCodec.decodeFromJsonElement(LetterBodyText.serializer(), it) }.getOrNull()
        }
        return LetterDetailDto(
            summary = buildSummary(letterRow, viewerId),
            contentType = content[LetterContents.contentType],
            fontCode = content[LetterContents.fontCode],
            body = body,
            bodyUrl = content[LetterContents.bodyUrl],
            attachments = attachments
        )
    }

    private fun buildSummary(row: ResultRow, viewerId: Uuid): LetterSummaryDto {
        val sender = row[Letters.senderId]?.let { sid ->
            Users.selectAll().where { Users.id eq sid }.firstOrNull()?.let(::userRow)?.toDto()
        }
        val recipient = row[Letters.recipientId]?.let { rid ->
            Users.selectAll().where { Users.id eq rid }.firstOrNull()?.let(::userRow)?.toDto()
        }
        val stamp = row[Letters.stampId]?.let { sid ->
            Stamps.selectAll().where { Stamps.id eq sid }.firstOrNull()?.get(Stamps.code)
        }
        val stationery = row[Letters.stationeryId]?.let { sid ->
            Stationeries.selectAll().where { Stationeries.id eq sid }.firstOrNull()?.get(Stationeries.code)
        }
        val sentAt = row[Letters.sentAt]
        val deliveryAt = row[Letters.deliveryAt]
        val transitStageVal = if (sentAt != null && deliveryAt != null && row[Letters.status] == "in_transit") {
            transitStage(now(), sentAt, deliveryAt)
        } else null
        val isFavorite = Favorites.selectAll()
            .where { (Favorites.userId eq viewerId) and (Favorites.letterId eq row[Letters.id]) }
            .firstOrNull() != null
        val wear = if (sentAt != null) {
            val end = row[Letters.deliveredAt] ?: now()
            wearLevelForHours(java.time.Duration.between(sentAt, end).toHours())
        } else 0
        return LetterSummaryDto(
            id = row[Letters.id].toString(),
            status = row[Letters.status],
            sender = sender,
            recipient = recipient,
            stampCode = stamp,
            stationeryCode = stationery,
            sentAt = sentAt?.toString(),
            deliveryAt = deliveryAt?.toString(),
            deliveredAt = row[Letters.deliveredAt]?.toString(),
            readAt = row[Letters.readAt]?.toString(),
            sealedUntil = row[Letters.sealedUntil]?.toString(),
            totalWeight = row[Letters.totalWeight],
            transitStage = transitStageVal,
            wearLevel = wear,
            isFavorite = isFavorite,
            replyToLetterId = row[Letters.replyToLetterId]?.toString()
        )
    }

    private fun extractPlainText(element: JsonElement): String = runCatching {
        val parsed = jsonCodec.decodeFromJsonElement(LetterBodyText.serializer(), element)
        parsed.segments.joinToString("") { if (it.style == "strikethrough") "" else it.text }
    }.getOrDefault("")
}

