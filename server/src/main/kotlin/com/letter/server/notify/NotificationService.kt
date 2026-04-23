package com.letter.server.mail

import com.letter.contract.dto.*
import com.letter.server.common.newId
import com.letter.server.common.now
import com.letter.server.db.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val nlog = KotlinLogging.logger("com.letter.server.notify")

@OptIn(ExperimentalUuidApi::class)
object NotificationService {

    fun emit(
        userId: Uuid,
        type: String,
        title: String,
        letterId: Uuid? = null,
        eventId: Uuid? = null,
        addressId: Uuid? = null,
        preview: String? = null
    ) {
        val prefs = UserNotificationPrefs.selectAll().where { UserNotificationPrefs.userId eq userId }.firstOrNull()
        val enabled = when (type) {
            "new_letter" -> prefs?.get(UserNotificationPrefs.newLetter) ?: true
            "postcard" -> prefs?.get(UserNotificationPrefs.postcard) ?: true
            "reply" -> prefs?.get(UserNotificationPrefs.reply) ?: true
            else -> true
        }
        if (!enabled) {
            nlog.debug { "notify.skip user=$userId type=$type (prefs off)" }
            return
        }
        Notifications.insert {
            it[Notifications.id] = newId()
            it[Notifications.userId] = userId
            it[Notifications.type] = type
            it[Notifications.letterId] = letterId
            it[Notifications.eventId] = eventId
            it[Notifications.addressId] = addressId
            it[Notifications.title] = title
            it[Notifications.preview] = preview
            it[Notifications.createdAt] = now()
        }
        nlog.info { "notify.emit user=$userId type=$type letter=$letterId address=$addressId" }
    }

    fun list(userId: Uuid, limit: Int = 50): List<NotificationDto> =
        Notifications.selectAll()
            .where { Notifications.userId eq userId }
            .orderBy(Notifications.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { r ->
                val addrId = r[Notifications.addressId]
                val addrLabel = addrId?.let { aid ->
                    UserAddresses.selectAll().where { UserAddresses.id eq aid }
                        .firstOrNull()?.get(UserAddresses.label)
                }
                NotificationDto(
                    id = r[Notifications.id].toString(),
                    type = r[Notifications.type],
                    title = r[Notifications.title],
                    preview = r[Notifications.preview],
                    letterId = r[Notifications.letterId]?.toString(),
                    eventId = r[Notifications.eventId]?.toString(),
                    addressId = addrId?.toString(),
                    addressLabel = addrLabel,
                    readAt = r[Notifications.readAt]?.toString(),
                    createdAt = r[Notifications.createdAt].toString()
                )
            }

    fun markRead(userId: Uuid, id: Uuid) {
        Notifications.update({ (Notifications.id eq id) and (Notifications.userId eq userId) }) {
            it[readAt] = now()
        }
    }

    fun markAllRead(userId: Uuid) {
        val ts = now()
        Notifications.update({ (Notifications.userId eq userId) and Notifications.readAt.isNull() }) {
            it[readAt] = ts
        }
    }

    fun unreadCount(userId: Uuid): Int =
        Notifications.selectAll()
            .where { (Notifications.userId eq userId) and Notifications.readAt.isNull() }
            .count()
            .toInt()

    fun getPrefs(userId: Uuid): NotificationPrefsDto {
        val row = UserNotificationPrefs.selectAll().where { UserNotificationPrefs.userId eq userId }.firstOrNull()
        return NotificationPrefsDto(
            newLetter = row?.get(UserNotificationPrefs.newLetter) ?: true,
            postcard = row?.get(UserNotificationPrefs.postcard) ?: true,
            reply = row?.get(UserNotificationPrefs.reply) ?: true
        )
    }

    fun updatePrefs(userId: Uuid, req: NotificationPrefsDto): NotificationPrefsDto {
        val exists = UserNotificationPrefs.selectAll().where { UserNotificationPrefs.userId eq userId }.firstOrNull()
        if (exists == null) {
            UserNotificationPrefs.insert {
                it[UserNotificationPrefs.userId] = userId
                it[newLetter] = req.newLetter
                it[postcard] = req.postcard
                it[reply] = req.reply
                it[updatedAt] = now()
            }
        } else {
            UserNotificationPrefs.update({ UserNotificationPrefs.userId eq userId }) {
                it[newLetter] = req.newLetter
                it[postcard] = req.postcard
                it[reply] = req.reply
                it[updatedAt] = now()
            }
        }
        return req
    }
}
