package com.luvtter.app.ui.compose

import com.luvtter.contract.dto.AddressDto
import com.luvtter.contract.dto.AttachmentDto
import com.luvtter.contract.dto.ContactDto
import com.luvtter.contract.dto.MyAssetsDto
import com.luvtter.contract.dto.RecipientAddressDto
import com.luvtter.contract.dto.StampDto
import com.luvtter.contract.dto.StationeryDto
import com.luvtter.contract.dto.StickerDto
import com.luvtter.contract.dto.TextSegment

data class ComposeUiState(
    val recipientHandle: String = "",
    val recipientName: String? = null,
    val recipientAddresses: List<RecipientAddressDto> = emptyList(),
    val recipientAddressId: String? = null,
    val lookupBusy: Boolean = false,

    val segments: List<TextSegment> = listOf(TextSegment("")),
    val stamps: List<StampDto> = emptyList(),
    val stampId: String? = null,
    val stationeries: List<StationeryDto> = emptyList(),
    val stationeryId: String? = null,
    val fontCode: String? = null,

    val assets: MyAssetsDto? = null,
    val senderAddresses: List<AddressDto> = emptyList(),
    val senderAddressId: String? = null,
    val contacts: List<ContactDto> = emptyList(),

    val stickers: List<StickerDto> = emptyList(),
    val attachments: List<AttachmentDto> = emptyList(),

    val draftId: String? = null,
    val sealedUntil: String? = null,
    val loading: Boolean = false,
    val attachmentBusy: Boolean = false,
    val status: String? = null,

    /** "text" | "scan" — 信件输入模式 */
    val mode: String = "text",
    /** 本次会话中刚上传的扫描件 objectKey (用于把新 key 提交给服务端) */
    val scanObjectKey: String? = null,
    /** 草稿上是否已经绑定了一个扫描件 (从服务端 hydrate 出来的) */
    val scanBound: Boolean = false,
    /** 展示给用户的文件名 / signed GET URL */
    val scanFilename: String? = null,
    val scanPreviewUrl: String? = null,
    val scanContentType: String? = null,
    val scanUploading: Boolean = false,
) {
    val canSaveDraft: Boolean
        get() = !loading && recipientHandle.isNotBlank() && when (mode) {
            "scan" -> scanObjectKey != null || scanBound
            else -> segments.any { it.text.isNotBlank() }
        }
    val canSend: Boolean get() = canSaveDraft && stampId != null
    val totalWeight: Int get() = attachments.sumOf { it.weight }
    val stampCapacity: Int? get() = stampId?.let { id -> stamps.firstOrNull { it.id == id }?.weightCapacity }
}

internal const val STYLE_STRIKETHROUGH = "strikethrough"

internal val FONT_OPTIONS: List<Pair<String?, String>> = listOf(
    null to "默认",
    "kaiti" to "楷体",
    "songti" to "宋体",
    "handwriting-1" to "手写体"
)
