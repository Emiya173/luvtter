package com.letter.shared.network

import com.letter.contract.dto.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class NotificationApi(private val client: HttpClient) {
    suspend fun list(limit: Int = 50): List<NotificationDto> =
        client.get("/api/v1/notifications") { url { parameters.append("limit", limit.toString()) } }
            .body<ApiResponse<List<NotificationDto>>>().data

    suspend fun unreadCount(): Int =
        client.get("/api/v1/notifications/unread-count").body<ApiResponse<UnreadCountDto>>().data.count

    suspend fun markRead(id: String) { client.post("/api/v1/notifications/$id/read") }
    suspend fun markAllRead() { client.post("/api/v1/notifications/read-all") }

    suspend fun prefs(): NotificationPrefsDto =
        client.get("/api/v1/notifications/prefs").body<ApiResponse<NotificationPrefsDto>>().data

    suspend fun updatePrefs(req: NotificationPrefsDto): NotificationPrefsDto =
        client.patch("/api/v1/notifications/prefs") { setBody(req) }.body<ApiResponse<NotificationPrefsDto>>().data
}

class DailyRewardApi(private val client: HttpClient) {
    suspend fun claim(tz: String? = null): DailyRewardDto =
        client.post("/api/v1/me/daily-reward") {
            tz?.let { headers.append("X-User-Timezone", it) }
        }.body<ApiResponse<DailyRewardDto>>().data
}
