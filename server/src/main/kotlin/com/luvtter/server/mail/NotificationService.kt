package com.luvtter.server.mail

import com.luvtter.contract.dto.*
import com.luvtter.server.common.newId
import com.luvtter.server.common.now
import com.luvtter.server.config.ValidationException
import com.luvtter.server.db.*
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
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val nlog = KotlinLogging.logger("com.luvtter.server.notify")

@OptIn(ExperimentalUuidApi::class)
object NotificationService {

    private val streams = ConcurrentHashMap<Uuid, MutableSharedFlow<NotificationDto>>()
    private val signalStreams = ConcurrentHashMap<Uuid, MutableSharedFlow<SignalDto>>()

    private fun bus(userId: Uuid): MutableSharedFlow<NotificationDto> =
        streams.computeIfAbsent(userId) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
        }

    private fun signalBus(userId: Uuid): MutableSharedFlow<SignalDto> =
        signalStreams.computeIfAbsent(userId) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
        }

    fun subscribe(userId: Uuid): SharedFlow<NotificationDto> = bus(userId).asSharedFlow()
    fun subscribeSignals(userId: Uuid): SharedFlow<SignalDto> = signalBus(userId).asSharedFlow()

    fun emitSignal(userId: Uuid, signal: SignalDto) {
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
        if (isInQuietHours(prefs)) {
            nlog.info { "notify.quiet user=$userId type=$type letter=$letterId (持久化已落库,跳过 SSE 推送)" }
        } else {
            bus(userId).tryEmit(dto)
            nlog.info { "notify.emit user=$userId type=$type letter=$letterId address=$addressId" }
        }
    }

    private fun isInQuietHours(prefs: org.jetbrains.exposed.v1.core.ResultRow?): Boolean {
        if (prefs == null) return false
        val start = prefs[UserNotificationPrefs.quietStart]?.toInt() ?: return false
        val end = prefs[UserNotificationPrefs.quietEnd]?.toInt() ?: return false
        if (start == end) return false
        val zone = prefs[UserNotificationPrefs.timezone]
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.of("UTC")
        val hour = ZonedDateTime.now(zone).hour
        return if (start < end) {
            hour in start until end
        } else {
            hour !in end..<start
        }
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
            reply = row?.get(UserNotificationPrefs.reply) ?: true,
            quietStart = row?.get(UserNotificationPrefs.quietStart)?.toInt(),
            quietEnd = row?.get(UserNotificationPrefs.quietEnd)?.toInt(),
            timezone = row?.get(UserNotificationPrefs.timezone)
        )
    }

    fun updatePrefs(userId: Uuid, req: NotificationPrefsDto): NotificationPrefsDto {
        val normalized = normalizeQuietHours(req)
        val exists = UserNotificationPrefs.selectAll().where { UserNotificationPrefs.userId eq userId }.firstOrNull()
        if (exists == null) {
            UserNotificationPrefs.insert {
                it[UserNotificationPrefs.userId] = userId
                it[newLetter] = normalized.newLetter
                it[postcard] = normalized.postcard
                it[reply] = normalized.reply
                it[quietStart] = normalized.quietStart?.toShort()
                it[quietEnd] = normalized.quietEnd?.toShort()
                it[timezone] = normalized.timezone
                it[updatedAt] = now()
            }
        } else {
            UserNotificationPrefs.update({ UserNotificationPrefs.userId eq userId }) {
                it[newLetter] = normalized.newLetter
                it[postcard] = normalized.postcard
                it[reply] = normalized.reply
                it[quietStart] = normalized.quietStart?.toShort()
                it[quietEnd] = normalized.quietEnd?.toShort()
                it[timezone] = normalized.timezone
                it[updatedAt] = now()
            }
        }
        return normalized
    }

    private fun normalizeQuietHours(req: NotificationPrefsDto): NotificationPrefsDto {
        val s = req.quietStart
        val e = req.quietEnd
        // 任一为 null 则视为关闭,清空另一字段与 timezone (避免半残状态)
        if (s == null || e == null) {
            return req.copy(quietStart = null, quietEnd = null, timezone = null)
        }
        if (s !in 0..23 || e !in 0..23) {
            throw ValidationException("quietStart/quietEnd 必须在 [0,23]")
        }
        val tz = req.timezone?.let {
            runCatching { ZoneId.of(it).id }.getOrElse {
                throw ValidationException("非法 timezone: ${req.timezone}")
            }
        }
        return req.copy(quietStart = s, quietEnd = e, timezone = tz)
    }
}
