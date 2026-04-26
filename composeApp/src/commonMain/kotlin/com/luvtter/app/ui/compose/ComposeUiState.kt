package com.luvtter.app.ui.compose

import androidx.compose.ui.text.TextRange
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

    /**
     * Stage 3「涂改模式」：开启后,在 [strikeOnDeleteWindowMs] 之外的 backspace 不再真正删字,
     * 而是把被删的尾部切成一个 strikethrough 段保留下来,模仿手写信划掉的痕迹。
     * 仅作用于「末尾后缀删除」场景(newText 是 oldText 的 prefix),其它编辑(中间删、整体替换)按原样落盘。
     */
    val strikeOnDelete: Boolean = true,
    /** 一个字符若在此毫秒数内刚被键入,backspace 走真删;超过则按「涂改」处理为划线。默认 3 秒。 */
    val strikeOnDeleteWindowMs: Long = 3_000L,
    /**
     * 与 editorText 等长的「该字符首次输入时间」(epoch ms,0 表示来自历史草稿,视为远古)。
     * 用每字符时间戳取代全局 lastEditAt,这样:刚敲的新字符 backspace 真删,旧字符 backspace 自动划线;
     * 键入新字符不会"重置"前面已敲字符的时间。
     */
    val charTimes: List<Long> = emptyList(),
    /** 编辑器光标 / 选区。在 segments 之外单独维护,因为 selection 不属于落盘内容。 */
    val editorSelection: TextRange = TextRange.Zero,
) {
    /** 把 segments 拼成扁平字符串,作为 OutlinedTextField 的 value。 */
    val editorText: String get() = segments.joinToString("") { it.text }
    /** 与 editorText 长度一致的「每个字符是否被划掉」mask,用于 visualTransformation 染色。 */
    val struckMask: List<Boolean> get() = buildList {
        segments.forEach { s ->
            val struck = s.style == STYLE_STRIKETHROUGH
            repeat(s.text.length) { add(struck) }
        }
    }

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
