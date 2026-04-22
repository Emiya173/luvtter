package com.letter.contract.dto

import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    val id: String,
    val type: String, // new_letter | postcard | reply
    val letterId: String? = null,
    val eventId: String? = null,
    val title: String,
    val preview: String? = null,
    val readAt: String? = null,
    val createdAt: String
)

@Serializable
data class NotificationPrefsDto(
    val newLetter: Boolean,
    val postcard: Boolean,
    val reply: Boolean
)

@Serializable
data class UnreadCountDto(val count: Int)
