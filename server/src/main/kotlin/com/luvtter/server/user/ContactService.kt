package com.luvtter.server.user

import com.luvtter.contract.dto.*
import com.luvtter.server.auth.userRow
import com.luvtter.server.common.newId
import com.luvtter.server.common.now
import com.luvtter.server.config.NotFoundException
import com.luvtter.server.config.ValidationException
import com.luvtter.server.db.Blocks
import com.luvtter.server.db.Contacts
import com.luvtter.server.db.Users
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ContactService {

    fun list(ownerId: Uuid): List<ContactDto> = transaction {
        Contacts.selectAll().where { Contacts.ownerId eq ownerId }.map { row ->
            val target = Users.selectAll().where { Users.id eq row[Contacts.targetId] }
                .first().let(::userRow).publicDto()
            ContactDto(
                id = row[Contacts.id].toString(),
                target = target,
                note = row[Contacts.note],
                relation = row[Contacts.relation],
                tags = emptyList()
            )
        }
    }

    fun create(ownerId: Uuid, req: CreateContactRequest): ContactDto = transaction {
        val targetId = com.luvtter.server.common.parseId(req.targetId)
        if (targetId == ownerId) throw ValidationException("不能添加自己为联系人")
        Users.selectAll().where { Users.id eq targetId }.firstOrNull()
            ?: throw NotFoundException(message = "目标用户不存在")
        val existing = Contacts.selectAll()
            .where { (Contacts.ownerId eq ownerId) and (Contacts.targetId eq targetId) }
            .firstOrNull()
        val ts = now()
        if (existing != null) {
            Contacts.update({ Contacts.id eq existing[Contacts.id] }) {
                it[note] = req.note
                it[relation] = req.relation
                it[updatedAt] = ts
            }
            return@transaction loadOne(existing[Contacts.id])
        }
        val id = newId()
        Contacts.insert {
            it[Contacts.id] = id
            it[Contacts.ownerId] = ownerId
            it[Contacts.targetId] = targetId
            it[note] = req.note
            it[relation] = req.relation
            it[createdAt] = ts
            it[updatedAt] = ts
        }
        loadOne(id)
    }

    fun update(ownerId: Uuid, contactId: Uuid, req: UpdateContactRequest): ContactDto = transaction {
        val missing = Contacts.selectAll()
            .where { (Contacts.id eq contactId) and (Contacts.ownerId eq ownerId) }
            .empty()
        if (missing) throw NotFoundException(message = "联系人不存在")
        Contacts.update({ Contacts.id eq contactId }) {
            req.note?.let { v -> it[note] = v }
            req.relation?.let { v -> it[relation] = v }
            it[updatedAt] = now()
        }
        loadOne(contactId)
    }

    fun delete(ownerId: Uuid, contactId: Uuid) = transaction {
        val n = Contacts.deleteWhere { (Contacts.id eq contactId) and (Contacts.ownerId eq ownerId) }
        if (n == 0) throw NotFoundException(message = "联系人不存在")
    }

    fun lookup(viewerId: Uuid, handle: String): LookupResult = transaction {
        val row = Users.selectAll().where { Users.handle eq handle }.firstOrNull()
            ?: throw NotFoundException(message = "用户不存在")
        val target = userRow(row).publicDto()
        val targetId = row[Users.id]
        val blocked = Blocks.selectAll()
            .where { (Blocks.ownerId eq targetId) and (Blocks.targetId eq viewerId) }
            .firstOrNull() != null
        val onlyFriends = row[Users.onlyFriends]
        val isContactOfTarget = if (onlyFriends) {
            Contacts.selectAll()
                .where { (Contacts.ownerId eq targetId) and (Contacts.targetId eq viewerId) }
                .firstOrNull() != null
        } else true
        LookupResult(user = target, acceptsFromMe = !blocked && isContactOfTarget)
    }

    fun listBlocks(ownerId: Uuid): List<BlockDto> = transaction {
        Blocks.selectAll().where { Blocks.ownerId eq ownerId }.map { row ->
            val target = Users.selectAll().where { Users.id eq row[Blocks.targetId] }
                .first().let(::userRow).publicDto()
            BlockDto(id = row[Blocks.id].toString(), target = target)
        }
    }

    fun block(ownerId: Uuid, req: CreateBlockRequest): BlockDto = transaction {
        val targetId = com.luvtter.server.common.parseId(req.targetId)
        if (targetId == ownerId) throw ValidationException("不能屏蔽自己")
        val existing = Blocks.selectAll()
            .where { (Blocks.ownerId eq ownerId) and (Blocks.targetId eq targetId) }
            .firstOrNull()
        val id = if (existing != null) existing[Blocks.id] else {
            val newBlock = newId()
            Blocks.insert {
                it[Blocks.id] = newBlock
                it[Blocks.ownerId] = ownerId
                it[Blocks.targetId] = targetId
                it[createdAt] = now()
            }
            newBlock
        }
        val target = Users.selectAll().where { Users.id eq targetId }.first()
            .let(::userRow).publicDto()
        BlockDto(id = id.toString(), target = target)
    }

    fun unblock(ownerId: Uuid, targetId: Uuid) = transaction {
        Blocks.deleteWhere { (Blocks.ownerId eq ownerId) and (Blocks.targetId eq targetId) }
    }

    private fun loadOne(contactId: Uuid): ContactDto {
        val row = Contacts.selectAll().where { Contacts.id eq contactId }.first()
        val target = Users.selectAll().where { Users.id eq row[Contacts.targetId] }
            .first().let(::userRow).publicDto()
        return ContactDto(
            id = row[Contacts.id].toString(),
            target = target,
            note = row[Contacts.note],
            relation = row[Contacts.relation],
            tags = emptyList()
        )
    }
}
