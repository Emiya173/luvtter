package com.luvtter.server.mail

import com.luvtter.contract.dto.*
import com.luvtter.server.auth.userRow
import com.luvtter.server.common.newId
import com.luvtter.server.common.now
import com.luvtter.server.common.parseId
import com.luvtter.server.config.ApiException
import com.luvtter.server.config.NotFoundException
import com.luvtter.server.config.ValidationException
import com.luvtter.server.db.*
import com.luvtter.server.storage.StorageService
import com.luvtter.server.user.publicDto
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
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
private val log = KotlinLogging.logger {}

@OptIn(ExperimentalUuidApi::class)
class LetterService(
    private val storage: StorageService,
    private val events: EventGenerator = EventGenerator(),
    private val onboarding: com.luvtter.server.user.OnboardingService = com.luvtter.server.user.OnboardingService(),
) {

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
        if (req.contentType == "scan") {
            val key = req.scanObjectKey
                ?: throw ValidationException("contentType=scan 时必须传 scanObjectKey")
            if (!storage.isUserOwnedKey(senderId, key)) {
                throw ValidationException("scanObjectKey 不属于当前用户")
            }
        }
        if (req.contentType == "handwriting") {
            val key = req.handwritingObjectKey
                ?: throw ValidationException("contentType=handwriting 时必须传 handwritingObjectKey")
            if (!storage.isUserOwnedKey(senderId, key)) {
                throw ValidationException("handwritingObjectKey 不属于当前用户")
            }
        }
        LetterContents.insert {
            it[letterId] = id
            it[contentType] = req.contentType
            it[fontCode] = req.fontCode
            it[bodyJson] = req.body?.let { b -> jsonCodec.encodeToJsonElement<LetterBodyText>(b) }
            it[bodyUrl] = req.bodyUrl
            it[scanObjectKey] = req.scanObjectKey
            it[handwritingObjectKey] = req.handwritingObjectKey
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
            req.scanObjectKey?.let { v ->
                if (!storage.isUserOwnedKey(senderId, v)) {
                    throw ValidationException("scanObjectKey 不属于当前用户")
                }
                it[scanObjectKey] = v
            }
            req.handwritingObjectKey?.let { v ->
                if (!storage.isUserOwnedKey(senderId, v)) {
                    throw ValidationException("handwritingObjectKey 不属于当前用户")
                }
                it[handwritingObjectKey] = v
            }
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
        onboarding.markFirstLetterSent(senderId)
        log.info { "letter.send id=$id from=$senderId to=$recipientId distance=$distance tier=${stamp[Stamps.tier]} deliveryAt=$deliveryAt" }
        SendResultDto(
            letter = summary,
            estimatedDeliveryAt = deliveryAt.toString(),
            transitStage = transitStage(ts, ts, deliveryAt)
        )
    }

    // --- 收件 ---

    fun inbox(userId: Uuid, limit: Int = 50, hidden: Boolean = false): List<LetterSummaryDto> = transaction {
        val nowTs = now()
        val currentAddressId = Users.selectAll().where { Users.id eq userId }
            .firstOrNull()?.get(Users.currentAddressId)

        // 步骤 1：扫描所有「应已送达但仍处 in_transit」的信，无论寄到哪个地址，
        //         统一升级为 delivered 并按地址发通知（用户在 A，寄到 B 的信只通知不入箱）
        Letters.selectAll()
            .where {
                (Letters.recipientId eq userId) and
                    (Letters.status eq "in_transit") and
                    (Letters.deliveryAt lessEq nowTs)
            }
            .forEach { row ->
                val lid = row[Letters.id]
                val addrId = row[Letters.recipientAddressId]
                Letters.update({ Letters.id eq lid }) {
                    it[status] = "delivered"
                    it[deliveredAt] = nowTs
                    it[updatedAt] = nowTs
                }
                val addrLabel = addrId?.let { aid ->
                    UserAddresses.selectAll().where { UserAddresses.id eq aid }
                        .firstOrNull()?.get(UserAddresses.label)
                }
                val title = if (addrLabel != null) "「$addrLabel」收到一封新信" else "你有一封新信"
                log.info { "letter.delivered id=$lid to=$userId address=$addrId" }
                NotificationService.emit(
                    userId = userId,
                    type = "new_letter",
                    title = title,
                    letterId = lid,
                    addressId = addrId
                )
            }

        // 步骤 2：收件箱
        // - hidden=false: 仅返回当前位置已送达且未隐藏的信
        // - hidden=true: 返回已隐藏的信（不限位置，便于用户找回）
        Letters.selectAll()
            .where {
                val base = (Letters.recipientId eq userId) and
                    (Letters.status inList listOf("delivered", "read")) and
                    (Letters.deliveryAt lessEq nowTs)
                if (hidden) {
                    base and Letters.recipientHiddenAt.isNotNull()
                } else {
                    val visible = base and Letters.recipientHiddenAt.isNull()
                    if (currentAddressId != null) visible and (Letters.recipientAddressId eq currentAddressId) else visible
                }
            }
            .orderBy(Letters.deliveryAt to SortOrder.DESC)
            .limit(limit)
            .map { row -> buildSummary(row, userId) }
    }

    fun outbox(userId: Uuid, limit: Int = 50, hidden: Boolean = false): List<LetterSummaryDto> = transaction {
        Letters.selectAll()
            .where {
                val base = Letters.senderId eq userId
                if (hidden) base and Letters.senderHiddenAt.isNotNull()
                else base and Letters.senderHiddenAt.isNull()
            }
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
        // 收件人在途时不应访问；不在当前位置也不应访问
        if (recipient == viewerId && sender != viewerId) {
            val deliveryAt = row[Letters.deliveryAt]
            if (deliveryAt == null || deliveryAt.isAfter(now())) {
                throw NotFoundException("LETTER_NOT_FOUND", "信件尚未送达")
            }
            val currentAddressId = Users.selectAll().where { Users.id eq viewerId }
                .firstOrNull()?.get(Users.currentAddressId)
            val letterAddressId = row[Letters.recipientAddressId]
            if (currentAddressId != null && letterAddressId != null && currentAddressId != letterAddressId) {
                throw NotFoundException("LETTER_NOT_FOUND", "请先切换到该信件的收件地址")
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
        row[Letters.senderId]?.let { senderId ->
            NotificationService.emitSignal(
                senderId,
                com.luvtter.contract.dto.SignalDto(
                    type = "letter_read",
                    letterId = id.toString(),
                    ts = now().toString()
                )
            )
        }
    }

    fun expedite(senderId: Uuid, id: Uuid, seconds: Long): LetterDetailDto = transaction {
        val row = Letters.selectAll().where { Letters.id eq id }.firstOrNull()
            ?: throw NotFoundException("LETTER_NOT_FOUND", "信件不存在")
        if (row[Letters.senderId] != senderId) {
            throw NotFoundException("LETTER_NOT_FOUND", "信件不存在")
        }
        if (row[Letters.status] !in listOf("in_transit")) {
            throw ApiException("LETTER_NOT_EDITABLE", "仅在途信件可加速", HttpStatusCode.Conflict)
        }
        val target = now().plusSeconds(seconds.coerceIn(1, 3600))
        Letters.update({ Letters.id eq id }) {
            it[deliveryAt] = target
            it[updatedAt] = now()
        }
        log.info { "letter.expedite id=$id seconds=$seconds newDeliveryAt=$target" }
        loadDetail(id, senderId)
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

    fun search(userId: Uuid, query: String, limit: Int = 50): List<LetterSummaryDto> = transaction {
        val q = query.trim()
        if (q.isBlank()) return@transaction emptyList()
        val cap = limit * 4
        val contentIds = mutableListOf<Uuid>()
        exec(
            "SELECT letter_id FROM letter_contents " +
                "WHERE index_tsv @@ letter_bigram_query(?) " +
                "LIMIT $cap",
            listOf(VarCharColumnType() to q)
        ) { rs ->
            while (rs.next()) {
                contentIds += Uuid.parse(rs.getObject(1).toString())
            }
        }
        if (contentIds.isEmpty()) return@transaction emptyList()
        Letters.selectAll()
            .where {
                (Letters.id inList contentIds) and
                    (((Letters.senderId eq userId) and Letters.senderHiddenAt.isNull()) or
                        ((Letters.recipientId eq userId) and Letters.recipientHiddenAt.isNull()))
            }
            .orderBy(Letters.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { buildSummary(it, userId) }
    }

    fun favorites(userId: Uuid, limit: Int = 50): List<LetterSummaryDto> = transaction {
        val orderedIds = Favorites.selectAll()
            .where { Favorites.userId eq userId }
            .orderBy(Favorites.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it[Favorites.letterId] }
        if (orderedIds.isEmpty()) return@transaction emptyList()
        val byId = Letters.selectAll()
            .where { Letters.id inList orderedIds }
            .associateBy { it[Letters.id] }
        orderedIds.mapNotNull { byId[it] }.map { buildSummary(it, userId) }
    }

    fun byFolder(userId: Uuid, folderId: Uuid, limit: Int = 50): List<LetterSummaryDto> = transaction {
        val ids = LetterFolders.selectAll()
            .where { (LetterFolders.userId eq userId) and (LetterFolders.folderId eq folderId) }
            .limit(limit)
            .map { it[LetterFolders.letterId] }
        if (ids.isEmpty()) return@transaction emptyList()
        Letters.selectAll()
            .where { Letters.id inList ids }
            .orderBy(Letters.createdAt to SortOrder.DESC)
            .map { buildSummary(it, userId) }
    }

    fun events(viewerId: Uuid, id: Uuid): List<LetterEventDto> = transaction {
        val row = Letters.selectAll().where { Letters.id eq id }.firstOrNull()
            ?: throw NotFoundException("LETTER_NOT_FOUND", "信件不存在")
        if (row[Letters.senderId] != viewerId && row[Letters.recipientId] != viewerId) {
            throw NotFoundException("LETTER_NOT_FOUND", "信件不存在")
        }
        val nowTs = now()
        LetterEvents.selectAll()
            .where { (LetterEvents.letterId eq id) and (LetterEvents.visibleAt lessEq nowTs) }
            .orderBy(LetterEvents.visibleAt to SortOrder.ASC)
            .map { e ->
                LetterEventDto(
                    id = e[LetterEvents.id].toString(),
                    letterId = e[LetterEvents.letterId].toString(),
                    eventType = e[LetterEvents.eventType],
                    title = e[LetterEvents.title],
                    content = e[LetterEvents.content],
                    imageUrl = e[LetterEvents.imageUrl],
                    visibleAt = e[LetterEvents.visibleAt].toString(),
                    readAt = e[LetterEvents.readAt]?.toString()
                )
            }
    }

    fun unhide(viewerId: Uuid, id: Uuid) = transaction {
        val row = Letters.selectAll().where { Letters.id eq id }.firstOrNull()
            ?: throw NotFoundException("LETTER_NOT_FOUND", "信件不存在")
        Letters.update({ Letters.id eq id }) {
            if (row[Letters.senderId] == viewerId) it[senderHiddenAt] = null
            if (row[Letters.recipientId] == viewerId) it[recipientHiddenAt] = null
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
            .map { it.toAttachmentDto(storage) }
        val bodyElement = content[LetterContents.bodyJson]
        val body = bodyElement?.let {
            runCatching { jsonCodec.decodeFromJsonElement(LetterBodyText.serializer(), it) }.getOrNull()
        }
        // 扫描信 / 手写信:每次按对应 object_key 重签发短期 GET URL,覆盖 body_url 字段返回前端
        val resolvedBodyUrl = content[LetterContents.scanObjectKey]
            ?.let { runCatching { storage.presignGet(it) }.getOrNull() }
            ?: content[LetterContents.handwritingObjectKey]
                ?.let { runCatching { storage.presignGet(it) }.getOrNull() }
            ?: content[LetterContents.bodyUrl]
        return LetterDetailDto(
            summary = buildSummary(letterRow, viewerId),
            contentType = content[LetterContents.contentType],
            fontCode = content[LetterContents.fontCode],
            body = body,
            bodyUrl = resolvedBodyUrl,
            attachments = attachments
        )
    }

    private fun buildSummary(row: ResultRow, viewerId: Uuid): LetterSummaryDto {
        val sender = row[Letters.senderId]?.let { sid ->
            Users.selectAll().where { Users.id eq sid }.firstOrNull()?.let(::userRow)?.publicDto()
        }
        val recipient = row[Letters.recipientId]?.let { rid ->
            Users.selectAll().where { Users.id eq rid }.firstOrNull()?.let(::userRow)?.publicDto()
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
        // 寄件人不应感知收件人是否已读：把 read 状态对寄件人折叠回 delivered，并隐藏 readAt
        val isSenderViewing = row[Letters.senderId] == viewerId && row[Letters.recipientId] != viewerId
        val rawStatus = row[Letters.status]
        val visibleStatus = if (isSenderViewing && rawStatus == "read") "delivered" else rawStatus
        val visibleReadAt = if (isSenderViewing) null else row[Letters.readAt]?.toString()
        val recipientAddressLabel = row[Letters.recipientAddressId]?.let { aid ->
            UserAddresses.selectAll().where { UserAddresses.id eq aid }
                .firstOrNull()?.get(UserAddresses.label)
        }
        val hidden = if (isSenderViewing) row[Letters.senderHiddenAt] != null
        else row[Letters.recipientHiddenAt] != null
        val attCounts = LetterAttachments.selectAll()
            .where { LetterAttachments.letterId eq row[Letters.id] }
            .groupingBy { it[LetterAttachments.attachmentType] }
            .eachCount()
        return LetterSummaryDto(
            id = row[Letters.id].toString(),
            status = visibleStatus,
            sender = sender,
            recipient = recipient,
            stampCode = stamp,
            stationeryCode = stationery,
            sentAt = sentAt?.toString(),
            deliveryAt = deliveryAt?.toString(),
            deliveredAt = row[Letters.deliveredAt]?.toString(),
            readAt = visibleReadAt,
            sealedUntil = row[Letters.sealedUntil]?.toString(),
            totalWeight = row[Letters.totalWeight],
            transitStage = transitStageVal,
            wearLevel = wear,
            isFavorite = isFavorite,
            replyToLetterId = row[Letters.replyToLetterId]?.toString(),
            recipientAddressLabel = recipientAddressLabel,
            hidden = hidden,
            photoCount = attCounts["photo"] ?: 0,
            stickerCount = attCounts["sticker"] ?: 0
        )
    }

    private fun extractPlainText(element: JsonElement): String = runCatching {
        val parsed = jsonCodec.decodeFromJsonElement(LetterBodyText.serializer(), element)
        parsed.segments.joinToString("") { if (it.style == "strikethrough") "" else it.text }
    }.getOrDefault("")
}

