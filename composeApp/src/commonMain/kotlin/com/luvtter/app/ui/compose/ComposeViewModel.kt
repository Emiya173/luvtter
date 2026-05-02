package com.luvtter.app.ui.compose

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.luvtter.app.navigation.ComposeRoute
import com.luvtter.contract.dto.AddPhotoAttachmentRequest
import com.luvtter.contract.dto.AddStickerRequest
import com.luvtter.contract.dto.CreateDraftRequest
import com.luvtter.contract.dto.LetterBodyText
import com.luvtter.contract.dto.SealDraftRequest
import com.luvtter.contract.dto.TextSegment
import com.luvtter.contract.dto.UpdateDraftRequest
import com.luvtter.app.platform.FilePicker
import com.luvtter.app.ui.common.formatLocalDateTime
import com.luvtter.shared.network.AddressApi
import com.luvtter.shared.network.CatalogApi
import com.luvtter.shared.network.ContactApi
import com.luvtter.shared.network.LetterApi
import com.luvtter.shared.network.MediaApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

class ComposeViewModel(
    savedStateHandle: SavedStateHandle,
    private val addresses: AddressApi,
    private val contacts: ContactApi,
    private val catalog: CatalogApi,
    private val letters: LetterApi,
    private val media: MediaApi,
    private val filePicker: FilePicker,
) : ViewModel() {

    private val route: ComposeRoute = savedStateHandle.toRoute()
    private val replyToLetterId: String? = route.replyToLetterId
    private val editDraftId: String? = route.editDraftId

    private val _state = MutableStateFlow(
        ComposeUiState(
            recipientHandle = route.recipientHandle.orEmpty(),
            draftId = editDraftId
        )
    )
    val state: StateFlow<ComposeUiState> = _state.asStateFlow()

    init { viewModelScope.launch { load(route.recipientHandle) } }

    private suspend fun load(prefillHandle: String?) {
        runCatching {
            val stamps = catalog.stamps()
            val stationeries = catalog.stationeries()
            val stickers = catalog.stickers()
            val senderAddresses = addresses.list()
            val assets = catalog.myAssets()
            val contactList = runCatching { contacts.list() }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    stamps = stamps,
                    stationeries = stationeries,
                    stickers = stickers,
                    senderAddresses = senderAddresses,
                    assets = assets,
                    contacts = contactList,
                    stampId = it.stampId ?: stamps.firstOrNull()?.id,
                    senderAddressId = it.senderAddressId
                        ?: senderAddresses.firstOrNull { a -> a.isDefault }?.id
                        ?: senderAddresses.firstOrNull()?.id
                )
            }
            if (editDraftId != null) hydrateFromDraft(editDraftId)
            else if (!prefillHandle.isNullOrBlank()) lookupRecipient()
        }.onFailure { e -> _state.update { it.copy(status = "加载失败: ${e.message}") } }
    }

    private suspend fun hydrateFromDraft(id: String) {
        val detail = letters.getDraft(id)
        val s = detail.summary
        val loaded = detail.body?.segments.orEmpty()
        val segs = loaded.ifEmpty { listOf(TextSegment("")) }
        val totalLen = segs.sumOf { it.text.length }
        _state.update { prev ->
            prev.copy(
                recipientHandle = s.recipient?.handle.orEmpty(),
                segments = segs,
                charTimes = List(totalLen) { 0L },
                editorSelection = TextRange(totalLen),
                mode = if (detail.contentType == "scan") "scan" else "text",
                // detail.bodyUrl 是重签的 MinIO GET URL;objectKey 本身服务端不暴露,
                // 草稿已绑定就不需要重传,upsertDraft 也不会再往 update 里塞 scanObjectKey。
                scanBound = detail.contentType == "scan",
                scanObjectKey = null,
                scanFilename = if (detail.contentType == "scan") detail.bodyUrl?.substringAfterLast('/')?.substringBefore('?') else null,
                scanPreviewUrl = if (detail.contentType == "scan") detail.bodyUrl else null,
                fontCode = detail.fontCode,
                sealedUntil = s.sealedUntil,
                stampId = s.stampCode?.let { code -> prev.stamps.firstOrNull { it.code == code }?.id } ?: prev.stampId,
                stationeryId = s.stationeryCode?.let { code -> prev.stationeries.firstOrNull { it.code == code }?.id } ?: prev.stationeryId,
                attachments = detail.attachments
            )
        }
        if (_state.value.recipientHandle.isNotBlank()) lookupRecipient()
    }

    fun onRecipientHandleChange(v: String) = _state.update { it.copy(recipientHandle = v.trim()) }

    /**
     * 单一 OutlinedTextField 的统一编辑入口。把字符级别的 diff(insert / delete / replace)落回
     * segments 数据结构;在「涂改模式 + 跨窗口 / 已处于 streak」时,删除部分被改成「保留原文 + 标 strikethrough」,
     * 视觉上原地 inline 划线,而不是真正缩短文本。
     */
    fun onEditorChange(new: TextFieldValue) {
        val st = _state.value
        val oldText = st.editorText
        val newText = new.text
        if (newText == oldText) {
            _state.update { it.copy(editorSelection = new.selection) }
            return
        }
        val now = currentTimeMillis()

        // 优先用「新光标位置」消歧:重复字符场景下,贪心前/后缀会把删除点错放到末尾,
        // 而 Compose 在编辑后告诉我们的 selection.start 才是真实的编辑位置。
        val cursor = new.selection.start.coerceIn(0, newText.length)
        val lengthDiff = newText.length - oldText.length
        var prefix = -1
        var oldMid = 0
        var newMid = 0
        when {
            lengthDiff < 0 -> { // 净删除
                val p = cursor
                val om = -lengthDiff
                if (p + om <= oldText.length &&
                    oldText.regionMatches(0, newText, 0, p) &&
                    oldText.regionMatches(p + om, newText, p, oldText.length - p - om)
                ) {
                    prefix = p; oldMid = om; newMid = 0
                }
            }
            lengthDiff > 0 -> { // 净插入
                val nm = lengthDiff
                val p = (cursor - nm).coerceAtLeast(0)
                if (p <= oldText.length &&
                    oldText.regionMatches(0, newText, 0, p) &&
                    oldText.regionMatches(p, newText, p + nm, oldText.length - p)
                ) {
                    prefix = p; oldMid = 0; newMid = nm
                }
            }
        }
        // 上面没命中(等长替换 / 多区段编辑 / 光标提示与文本不一致)→ 退回贪心前后缀匹配
        if (prefix < 0) {
            val p = run {
                val n = minOf(oldText.length, newText.length)
                var i = 0
                while (i < n && oldText[i] == newText[i]) i++
                i
            }
            val s = run {
                val n = minOf(oldText.length - p, newText.length - p)
                var i = 0
                while (i < n && oldText[oldText.length - 1 - i] == newText[newText.length - 1 - i]) i++
                i
            }
            prefix = p
            oldMid = oldText.length - p - s
            newMid = newText.length - p - s
        }

        val mask = st.struckMask.toMutableList()
        val times = st.charTimes.toMutableList()
        // 防御:历史草稿/初始态可能 mask/times 长度短于 text;补齐为旧文本长度。
        while (mask.size < oldText.length) mask.add(false)
        while (times.size < oldText.length) times.add(0L)

        // 每字符判定:被删的所有字符若都"老于窗口"(t==0 视为远古/草稿)→ 整段划线;
        // 否则按真删处理。这样刚键入的新字符 backspace 仍走真删,不会被自己划掉。
        val canStrikeRange = oldMid > 0 && st.strikeOnDelete &&
            (prefix until prefix + oldMid).all { i ->
                val t = times.getOrElse(i) { 0L }
                t == 0L || (now - t) > st.strikeOnDeleteWindowMs
            }

        val resultText: String
        val newCursor: Int
        when {
            // 纯插入:新字符获得当前时间戳
            oldMid == 0 && newMid > 0 -> {
                for (k in 0 until newMid) {
                    mask.add(prefix + k, false)
                    times.add(prefix + k, now)
                }
                resultText = newText
                newCursor = prefix + newMid
            }
            // 纯删除
            oldMid > 0 && newMid == 0 -> {
                if (canStrikeRange) {
                    for (i in prefix until prefix + oldMid) mask[i] = true
                    resultText = oldText
                    // 光标移到划痕「左边」,下一次 backspace 才能继续向左划
                    newCursor = prefix
                } else {
                    repeat(oldMid) {
                        mask.removeAt(prefix)
                        times.removeAt(prefix)
                    }
                    resultText = newText
                    newCursor = prefix
                }
            }
            // 替换(选中后输入,或删改混合)
            oldMid > 0 && newMid > 0 -> {
                if (canStrikeRange) {
                    for (i in prefix until prefix + oldMid) mask[i] = true
                    val insertedSlice = newText.substring(prefix, prefix + newMid)
                    resultText = oldText.substring(0, prefix + oldMid) + insertedSlice +
                        oldText.substring(prefix + oldMid)
                    for (k in 0 until newMid) {
                        mask.add(prefix + oldMid + k, false)
                        times.add(prefix + oldMid + k, now)
                    }
                    newCursor = prefix + oldMid + newMid
                } else {
                    repeat(oldMid) {
                        mask.removeAt(prefix)
                        times.removeAt(prefix)
                    }
                    for (k in 0 until newMid) {
                        mask.add(prefix + k, false)
                        times.add(prefix + k, now)
                    }
                    resultText = newText
                    newCursor = prefix + newMid
                }
            }
            else -> {
                resultText = newText
                newCursor = new.selection.start
            }
        }

        _state.update {
            it.copy(
                segments = rebuildSegments(resultText, mask),
                charTimes = times,
                editorSelection = TextRange(newCursor.coerceIn(0, resultText.length)),
            )
        }
    }

    fun strikeSelection() = _state.update { st ->
        val sel = st.editorSelection
        if (sel.collapsed) return@update st
        val mask = st.struckMask.toMutableList()
        for (i in sel.min until sel.max.coerceAtMost(mask.size)) mask[i] = true
        st.copy(segments = rebuildSegments(st.editorText, mask))
    }

    fun unstrikeSelection() = _state.update { st ->
        val sel = st.editorSelection
        if (sel.collapsed) return@update st
        val mask = st.struckMask.toMutableList()
        for (i in sel.min until sel.max.coerceAtMost(mask.size)) mask[i] = false
        st.copy(segments = rebuildSegments(st.editorText, mask))
    }

    fun toggleStrikeOnDelete() = _state.update { it.copy(strikeOnDelete = !it.strikeOnDelete) }

    private fun rebuildSegments(text: String, mask: List<Boolean>): List<TextSegment> {
        if (text.isEmpty()) return listOf(TextSegment(""))
        val out = mutableListOf<TextSegment>()
        var startIdx = 0
        var curStruck = mask.getOrElse(0) { false }
        for (i in 1..text.length) {
            val nextStruck = mask.getOrElse(i) { curStruck }
            if (i == text.length || nextStruck != curStruck) {
                out.add(
                    TextSegment(
                        text = text.substring(startIdx, i),
                        style = if (curStruck) STYLE_STRIKETHROUGH else null
                    )
                )
                if (i < text.length) {
                    startIdx = i
                    curStruck = nextStruck
                }
            }
        }
        return out
    }

    fun onStampSelect(id: String) = _state.update { it.copy(stampId = id) }
    fun onStationerySelect(id: String?) = _state.update { it.copy(stationeryId = id) }
    fun onFontSelect(code: String?) = _state.update { it.copy(fontCode = code) }
    fun onSenderAddressSelect(id: String) = _state.update { it.copy(senderAddressId = id) }
    fun onRecipientAddressSelect(id: String) = _state.update { it.copy(recipientAddressId = id) }
    fun clearStatus() = _state.update { it.copy(status = null) }

    fun lookupRecipientFromContact(handle: String) {
        _state.update { it.copy(recipientHandle = handle) }
        lookupRecipient()
    }

    fun lookupRecipient() {
        val handle = _state.value.recipientHandle
        if (handle.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(lookupBusy = true, status = null) }
            runCatching {
                val lr = contacts.lookup(handle)
                val addrs = addresses.listForRecipient(handle)
                _state.update {
                    it.copy(
                        recipientName = lr.user.displayName,
                        recipientAddresses = addrs,
                        recipientAddressId = addrs.firstOrNull { a -> a.isDefault }?.id ?: addrs.firstOrNull()?.id
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        recipientName = null,
                        recipientAddresses = emptyList(),
                        recipientAddressId = null,
                        status = "找不到收件人: ${e.message}"
                    )
                }
            }
            _state.update { it.copy(lookupBusy = false) }
        }
    }

    private suspend fun upsertDraft(): String {
        val s = _state.value
        val isScan = s.mode == "scan"
        val body: LetterBodyText? = if (isScan) null else {
            LetterBodyText(s.segments.filter { it.text.isNotEmpty() }.ifEmpty { listOf(TextSegment("")) })
        }
        val existing = s.draftId
        return if (existing != null) {
            letters.updateDraft(existing, UpdateDraftRequest(
                recipientHandle = s.recipientHandle,
                recipientAddressId = s.recipientAddressId,
                senderAddressId = s.senderAddressId,
                stampId = s.stampId,
                stationeryId = s.stationeryId,
                fontCode = s.fontCode,
                body = body,
                scanObjectKey = s.scanObjectKey
            ))
            existing
        } else {
            val d = letters.createDraft(CreateDraftRequest(
                recipientHandle = s.recipientHandle,
                recipientAddressId = s.recipientAddressId,
                senderAddressId = s.senderAddressId,
                stampId = s.stampId,
                stationeryId = s.stationeryId,
                fontCode = s.fontCode,
                contentType = if (isScan) "scan" else "text",
                body = body,
                scanObjectKey = s.scanObjectKey,
                replyToLetterId = replyToLetterId
            ))
            _state.update { it.copy(draftId = d.summary.id) }
            d.summary.id
        }
    }

    fun setMode(mode: String) {
        if (mode != "text" && mode != "scan") return
        _state.update { it.copy(mode = mode, status = null) }
    }

    /** 选扫描件并直传 MinIO,完成后把 objectKey 暂存到 state,等保存草稿/寄出时一并提交。 */
    fun pickAndUploadScan() {
        viewModelScope.launch {
            _state.update { it.copy(scanUploading = true, status = "选择扫描件中...") }
            runCatching {
                val picked = filePicker.pickScan() ?: run {
                    _state.update { it.copy(scanUploading = false, status = null) }
                    return@launch
                }
                _state.update { it.copy(status = "上传中 ${picked.filename}...") }
                val key = media.uploadScan(picked.filename, picked.contentType, picked.bytes)
                _state.update {
                    it.copy(
                        scanObjectKey = key,
                        scanBound = true,
                        scanFilename = picked.filename,
                        scanContentType = picked.contentType,
                        scanPreviewUrl = null,
                        status = "已上传 ${picked.filename}"
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(status = "扫描件上传失败: ${e.message}") }
            }
            _state.update { it.copy(scanUploading = false) }
        }
    }

    fun clearScan() = _state.update {
        it.copy(
            scanObjectKey = null,
            scanBound = false,
            scanFilename = null,
            scanContentType = null,
            scanPreviewUrl = null,
            status = null
        )
    }

    fun saveDraft() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, status = null) }
            runCatching { upsertDraft() }
                .onSuccess { _state.update { it.copy(status = "已保存草稿") } }
                .onFailure { e -> _state.update { it.copy(status = "保存失败: ${e.message}") } }
            _state.update { it.copy(loading = false) }
        }
    }

    fun send(onSent: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, status = null) }
            runCatching {
                val id = upsertDraft()
                letters.send(id)
            }.onSuccess { result ->
                _state.update {
                    val pretty = formatLocalDateTime(result.estimatedDeliveryAt) ?: result.estimatedDeliveryAt
                    it.copy(status = "已寄出，预计送达: $pretty")
                }
                onSent()
            }.onFailure { e ->
                _state.update { it.copy(status = "寄信失败: ${e.message}") }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    fun sealUntil(isoTime: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, status = null) }
            runCatching {
                val id = upsertDraft()
                letters.seal(id, SealDraftRequest(isoTime))
            }.onSuccess {
                _state.update {
                    val pretty = formatLocalDateTime(isoTime) ?: isoTime
                    it.copy(sealedUntil = isoTime, status = "已封存至 $pretty")
                }
            }.onFailure { e ->
                _state.update { it.copy(status = "封存失败: ${e.message}") }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    fun addStickerAttachment(stickerId: String) {
        viewModelScope.launch {
            _state.update { it.copy(attachmentBusy = true, status = null) }
            runCatching {
                val draftId = upsertDraft()
                val att = letters.addSticker(draftId, AddStickerRequest(stickerId = stickerId))
                _state.update { it.copy(attachments = it.attachments + att) }
            }.onFailure { e ->
                _state.update { it.copy(status = "添加贴纸失败: ${e.message}") }
            }
            _state.update { it.copy(attachmentBusy = false) }
        }
    }

    /**
     * 选图 → MinIO 直传 → addPhoto(objectKey)。
     * 重量按 ⌈sizeKB/10⌉ 估算 (10KB ~ 1g),最少 1g。客户端可手动调整逻辑后续再细化。
     */
    fun pickAndUploadPhoto() {
        viewModelScope.launch {
            _state.update { it.copy(attachmentBusy = true, status = "选择图片中...") }
            runCatching {
                val picked = filePicker.pickImage() ?: run {
                    _state.update { it.copy(attachmentBusy = false, status = null) }
                    return@launch
                }
                _state.update { it.copy(status = "上传中 ${picked.filename}...") }
                val draftId = upsertDraft()
                val key = media.uploadPhoto(picked.filename, picked.contentType, picked.bytes)
                val sizeKB = (picked.sizeBytes + 1023) / 1024
                val weight = ((sizeKB + 9) / 10).toInt().coerceAtLeast(1)
                val att = letters.addPhotoAttachment(
                    draftId,
                    AddPhotoAttachmentRequest(objectKey = key, weight = weight)
                )
                _state.update {
                    it.copy(
                        attachments = it.attachments + att,
                        status = "已添加 ${picked.filename} (${weight}g)"
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(status = "添加图片失败: ${e.message}") }
            }
            _state.update { it.copy(attachmentBusy = false) }
        }
    }

    fun removeAttachment(attachmentId: String) {
        val draftId = _state.value.draftId ?: return
        viewModelScope.launch {
            _state.update { it.copy(attachmentBusy = true, status = null) }
            runCatching {
                letters.deleteAttachment(draftId, attachmentId)
                _state.update { it.copy(attachments = it.attachments.filterNot { a -> a.id == attachmentId }) }
            }.onFailure { e ->
                _state.update { it.copy(status = "移除附件失败: ${e.message}") }
            }
            _state.update { it.copy(attachmentBusy = false) }
        }
    }

    val isReply: Boolean get() = replyToLetterId != null
}
