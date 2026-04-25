package com.luvtter.contract.dto

import kotlinx.serialization.Serializable

@Serializable
data class TextSegment(
    val text: String,
    val style: String? = null // null | "strikethrough"
)

@Serializable
data class LetterBodyText(
    val segments: List<TextSegment>
)

@Serializable
data class CreateDraftRequest(
    val recipientHandle: String? = null,
    val recipientId: String? = null,
    val recipientAddressId: String? = null,
    val senderAddressId: String? = null,
    val stampId: String? = null,
    val stationeryId: String? = null,
    val fontCode: String? = null,
    val contentType: String = "text", // text | handwriting | scan
    val body: LetterBodyText? = null,
    val bodyUrl: String? = null,
    val replyToLetterId: String? = null
)

@Serializable
data class UpdateDraftRequest(
    val recipientHandle: String? = null,
    val recipientId: String? = null,
    val recipientAddressId: String? = null,
    val senderAddressId: String? = null,
    val stampId: String? = null,
    val stationeryId: String? = null,
    val fontCode: String? = null,
    val body: LetterBodyText? = null,
    val bodyUrl: String? = null
)

@Serializable
data class SealDraftRequest(val sealedUntil: String)

@Serializable
data class SendResultDto(
    val letter: LetterSummaryDto,
    val estimatedDeliveryAt: String,
    val transitStage: String
)

@Serializable
data class LetterSummaryDto(
    val id: String,
    val status: String,
    val sender: UserDto? = null,
    val recipient: UserDto? = null,
    val stampCode: String? = null,
    val stationeryCode: String? = null,
    val sentAt: String? = null,
    val deliveryAt: String? = null,
    val deliveredAt: String? = null,
    val readAt: String? = null,
    val sealedUntil: String? = null,
    val totalWeight: Int = 0,
    val transitStage: String? = null, // sending | on_the_way | arriving
    val wearLevel: Int = 0,
    val isFavorite: Boolean = false,
    val replyToLetterId: String? = null,
    val preview: String? = null,
    val recipientAddressLabel: String? = null,
    val hidden: Boolean = false,
    /** 附件计数,供列表页显示 chip(详情页才返回完整 attachments 列表)。 */
    val photoCount: Int = 0,
    val stickerCount: Int = 0
)

@Serializable
data class LetterDetailDto(
    val summary: LetterSummaryDto,
    val contentType: String,
    val fontCode: String? = null,
    val body: LetterBodyText? = null,
    val bodyUrl: String? = null,
    val attachments: List<AttachmentDto> = emptyList()
)

@Serializable
data class AttachmentDto(
    val id: String,
    val attachmentType: String,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val stickerId: String? = null,
    val objectKey: String? = null,
    val positionX: Double? = null,
    val positionY: Double? = null,
    val rotation: Double? = null,
    val weight: Int,
    val orderIndex: Int = 0
)

@Serializable
data class AddPhotoAttachmentRequest(
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val objectKey: String? = null,
    val weight: Int,
    val positionX: Double? = null,
    val positionY: Double? = null,
    val rotation: Double? = null
)

@Serializable
data class AddStickerRequest(
    val stickerId: String,
    val positionX: Double? = null,
    val positionY: Double? = null,
    val rotation: Double? = null
)

@Serializable
data class LetterEventDto(
    val id: String,
    val letterId: String,
    val eventType: String,
    val title: String? = null,
    val content: String? = null,
    val imageUrl: String? = null,
    val visibleAt: String,
    val readAt: String? = null
)
