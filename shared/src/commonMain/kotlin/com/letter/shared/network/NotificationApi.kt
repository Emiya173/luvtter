package com.letter.shared.network

import com.letter.contract.dto.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.plugins.sse.sseSession
import io.ktor.client.request.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

private val streamJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
private val nalog = KotlinLogging.logger("com.letter.shared.notify")

class NotificationApi(private val client: HttpClient) {
    suspend fun list(limit: Int = 50): List<NotificationDto> =
        client.get("/api/v1/notifications") { url { parameters.append("limit", limit.toString()) } }.unwrap()

    suspend fun unreadCount(): Int =
        client.get("/api/v1/notifications/unread-count").unwrap<UnreadCountDto>().count

    suspend fun markRead(id: String) { client.post("/api/v1/notifications/$id/read").ensureSuccess() }
    suspend fun markAllRead() { client.post("/api/v1/notifications/read-all").ensureSuccess() }

    suspend fun prefs(): NotificationPrefsDto =
        client.get("/api/v1/notifications/prefs").unwrap()

    suspend fun updatePrefs(req: NotificationPrefsDto): NotificationPrefsDto =
        client.patch("/api/v1/notifications/prefs") { setBody(req) }.unwrap()

    fun stream(): Flow<NotificationDto> = flow {
        val session = client.sseSession("/api/v1/notifications/stream")
        session.incoming.collect { sse ->
            if (sse.event == "notification") {
                val data = sse.data ?: return@collect
                runCatching { streamJson.decodeFromString(NotificationDto.serializer(), data) }
                    .onSuccess { emit(it) }
                    .onFailure { e -> nalog.warn(e) { "notify.stream.decode-failed data=${data.take(80)}" } }
            }
            // event=ping / event=ready 仅作为保活和诊断,无需回调
        }
    }

    /**
     * 与 [stream] 共享同一条 SSE 链路,但只输出瞬时信号(upload_done / letter_read 等)。
     * 客户端通常分别 collect 这两个 flow——这里再开一条单独连接以保持解耦。
     */
    fun signals(): Flow<SignalDto> = flow {
        val session = client.sseSession("/api/v1/notifications/stream")
        session.incoming.collect { sse ->
            if (sse.event == "signal") {
                val data = sse.data ?: return@collect
                runCatching { streamJson.decodeFromString(SignalDto.serializer(), data) }
                    .onSuccess { emit(it) }
                    .onFailure { e -> nalog.warn(e) { "notify.signal.decode-failed data=${data.take(80)}" } }
            }
        }
    }

    suspend fun notifyUploadDone(req: UploadDoneRequest) {
        client.post("/api/v1/uploads/photo/done") { setBody(req) }.ensureSuccess()
    }
}

class DailyRewardApi(private val client: HttpClient) {
    suspend fun claim(tz: String? = null): DailyRewardDto =
        client.post("/api/v1/me/daily-reward") {
            tz?.let { headers.append("X-User-Timezone", it) }
        }.unwrap()
}
