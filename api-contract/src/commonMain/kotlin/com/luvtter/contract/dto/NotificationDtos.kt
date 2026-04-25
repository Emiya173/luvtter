package com.luvtter.contract.dto

import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    val id: String,
    val type: String, // new_letter | postcard | reply
    val letterId: String? = null,
    val eventId: String? = null,
    val addressId: String? = null,
    val addressLabel: String? = null,
    val title: String,
    val preview: String? = null,
    val readAt: String? = null,
    val createdAt: String
)

/**
 * 瞬时信号（不入库），通过 SSE event=signal 推到当前用户的所有在线会话。
 * 类型示例：upload_done | letter_read | session_revoked
 */
@Serializable
data class SignalDto(
    val type: String,
    val letterId: String? = null,
    val objectKey: String? = null,
    val sizeBytes: Long? = null,
    val ts: String
)

@Serializable
data class UploadDoneRequest(
    val objectKey: String,
    val sizeBytes: Long? = null
)

@Serializable
data class NotificationPrefsDto(
    val newLetter: Boolean,
    val postcard: Boolean,
    val reply: Boolean,
    /** 免打扰起始本地小时 [0,23]，与 quietEnd 同时为 null 表示关闭。 */
    val quietStart: Int? = null,
    /** 免打扰结束本地小时 [0,23]，半开区间。start>end 表示跨日。 */
    val quietEnd: Int? = null,
    /** IANA 时区 (如 Asia/Shanghai)，为 null 时按 UTC。 */
    val timezone: String? = null
)

@Serializable
data class UnreadCountDto(val count: Int)
