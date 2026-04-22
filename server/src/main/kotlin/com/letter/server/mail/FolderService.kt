package com.letter.server.mail

import com.letter.contract.dto.*
import com.letter.server.common.newId
import com.letter.server.common.now
import com.letter.server.common.parseId
import com.letter.server.config.NotFoundException
import com.letter.server.config.ValidationException
import com.letter.server.db.Favorites
import com.letter.server.db.Folders
import com.letter.server.db.LetterFolders
import com.letter.server.db.Letters
import io.github.oshai.kotlinlogging.KotlinLogging
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
class FolderService {

    fun list(userId: Uuid): List<FolderDto> = transaction {
        Folders.selectAll()
            .where { Folders.userId eq userId }
            .orderBy(Folders.orderIndex to SortOrder.ASC)
            .map { it.toDto() }
    }

    fun create(userId: Uuid, req: CreateFolderRequest): FolderDto = transaction {
        if (req.name.isBlank()) throw ValidationException("名称不能为空")
        val maxOrder = Folders.selectAll().where { Folders.userId eq userId }
            .maxOfOrNull { it[Folders.orderIndex] } ?: -1
        val id = newId()
        val ts = now()
        Folders.insert {
            it[Folders.id] = id
            it[Folders.userId] = userId
            it[name] = req.name
            it[icon] = req.icon
            it[orderIndex] = maxOrder + 1
            it[createdAt] = ts
        }
        Folders.selectAll().where { Folders.id eq id }.first().toDto()
    }

    fun update(userId: Uuid, id: Uuid, req: UpdateFolderRequest): FolderDto = transaction {
        val row = Folders.selectAll().where { (Folders.id eq id) and (Folders.userId eq userId) }.firstOrNull()
            ?: throw NotFoundException(message = "分类不存在")
        Folders.update({ Folders.id eq id }) {
            req.name?.let { v -> it[name] = v }
            req.icon?.let { v -> it[icon] = v }
        }
        Folders.selectAll().where { Folders.id eq id }.first().toDto()
    }

    fun delete(userId: Uuid, id: Uuid) = transaction {
        LetterFolders.deleteWhere { (LetterFolders.folderId eq id) and (LetterFolders.userId eq userId) }
        Folders.deleteWhere { (Folders.id eq id) and (Folders.userId eq userId) }
    }

    fun reorder(userId: Uuid, orderedIds: List<String>) = transaction {
        orderedIds.forEachIndexed { idx, raw ->
            val fid = parseId(raw)
            Folders.update({ (Folders.id eq fid) and (Folders.userId eq userId) }) {
                it[orderIndex] = idx
            }
        }
    }

    fun assign(userId: Uuid, letterId: Uuid, folderId: Uuid?) = transaction {
        val letter = Letters.selectAll().where { Letters.id eq letterId }.firstOrNull()
            ?: throw NotFoundException(message = "信件不存在")
        val sender = letter[Letters.senderId]
        val recipient = letter[Letters.recipientId]
        if (sender != userId && recipient != userId) throw NotFoundException(message = "信件不存在")

        LetterFolders.deleteWhere { (LetterFolders.letterId eq letterId) and (LetterFolders.userId eq userId) }
        folderId?.let { fid ->
            val own = Folders.selectAll().where { (Folders.id eq fid) and (Folders.userId eq userId) }.firstOrNull()
                ?: throw ValidationException("分类不存在")
            LetterFolders.insert {
                it[LetterFolders.letterId] = letterId
                it[LetterFolders.userId] = userId
                it[LetterFolders.folderId] = fid
            }
        }
        log.info { "folder.assign user=$userId letter=$letterId folder=$folderId" }
    }

    fun favorite(userId: Uuid, letterId: Uuid) = transaction {
        val exists = Favorites.selectAll()
            .where { (Favorites.userId eq userId) and (Favorites.letterId eq letterId) }
            .firstOrNull()
        if (exists == null) {
            Favorites.insert {
                it[Favorites.userId] = userId
                it[Favorites.letterId] = letterId
                it[createdAt] = now()
            }
        }
    }

    fun unfavorite(userId: Uuid, letterId: Uuid) = transaction {
        Favorites.deleteWhere { (Favorites.userId eq userId) and (Favorites.letterId eq letterId) }
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toDto() = FolderDto(
        id = this[Folders.id].toString(),
        name = this[Folders.name],
        icon = this[Folders.icon],
        orderIndex = this[Folders.orderIndex]
    )
}
