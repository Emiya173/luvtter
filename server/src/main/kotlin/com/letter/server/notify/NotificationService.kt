package com.letter.server.mail

import com.letter.contract.dto.*
import com.letter.server.common.newId
import com.letter.server.common.now
import com.letter.server.db.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val nlog = KotlinLogging.logger("com.letter.server.notify")

@OptIn(ExperimentalUuidApi::class)
object NotificationService {

    private val streams = ConcurrentHashMap<Uuid, MutableSharedFlow<NotificationDto>>()
    private val signalStreams = ConcurrentHashMap<Uuid, MutableSharedFlow<com.letter.contract.dto.SignalDto>>()

    private fun bus(userId: Uuid): MutableSharedFlow<NotificationDto> =
        streams.computeIfAbsent(userId) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
        }

    private fun signalBus(userId: Uuid): MutableSharedFlow<com.letter.contract.dto.SignalDto> =
        signalStreams.computeIfAbsent(userId) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
        }

    fun subscribe(userId: Uuid): SharedFlow<NotificationDto> = bus(userId).asSharedFlow()
    fun subscribeSignals(userId: Uuid): SharedFlow<com.letter.contract.dto.SignalDto> = signalBus(userId).asSharedFlow()

    fun emitSignal(userId: Uuid, signal: com.letter.contract.dto.SignalDto) {
        signalBus(userId).tryEmit(signal)
        nlog.info { "notify.signal user=$userId type=${signal.type}" }
    }

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
        val nid = newId()
        val ts = now()
        Notifications.insert {
            it[Notifications.id] = nid
            it[Notifications.userId] = userId
            it[Notifications.type] = type
            it[Notifications.letterId] = letterId
            it[Notifications.eventId] = eventId
            it[Notifications.addressId] = addressId
            it[Notifications.title] = title
            it[Notifications.preview] = preview
            it[Notifications.createdAt] = ts
        }
        val addrLabel = addressId?.let { aid ->
            UserAddresses.selectAll().where { UserAddresses.id eq aid }
                .firstOrNull()?.get(UserAddresses.label)
        }
        val dto = NotificationDto(
            id = nid.toString(),
            type = type,
            title = title,
            preview = preview,
            letterId = letterId?.toString(),
            eventId = eventId?.toString(),
            addressId = addressId?.toString(),
            addressLabel = addrLabel,
            readAt = null,
            createdAt = ts.toString()
        )
        bus(userId).tryEmit(dto)
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
