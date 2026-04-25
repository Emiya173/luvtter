package com.luvtter.app.ui.compose

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
import com.luvtter.shared.network.AddressApi
import com.luvtter.shared.network.CatalogApi
import com.luvtter.shared.network.ContactApi
import com.luvtter.shared.network.LetterApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ComposeViewModel(
    savedStateHandle: SavedStateHandle,
    private val addresses: AddressApi,
    private val contacts: ContactApi,
    private val catalog: CatalogApi,
    private val letters: LetterApi
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
        val segs = if (loaded.isNotEmpty()) loaded else listOf(TextSegment(""))
        _state.update { prev ->
            prev.copy(
                recipientHandle = s.recipient?.handle.orEmpty(),
                segments = segs,
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

    fun onSegmentTextChange(index: Int, text: String) = _state.update { st ->
        if (index !in st.segments.indices) return@update st
        st.copy(segments = st.segments.toMutableList().also { it[index] = it[index].copy(text = text) })
    }

    fun onSegmentStyleToggle(index: Int) = _state.update { st ->
        if (index !in st.segments.indices) return@update st
        val cur = st.segments[index]
        val next = if (cur.style == STYLE_STRIKETHROUGH) null else STYLE_STRIKETHROUGH
        st.copy(segments = st.segments.toMutableList().also { it[index] = cur.copy(style = next) })
    }

    fun onSegmentAddAfter(index: Int) = _state.update { st ->
        val i = index.coerceIn(-1, st.segments.lastIndex)
        val next = st.segments.toMutableList().apply { add(i + 1, TextSegment("")) }
        st.copy(segments = next)
    }

    fun onSegmentRemove(index: Int) = _state.update { st ->
        if (index !in st.segments.indices) return@update st
        if (st.segments.size <= 1) {
            st.copy(segments = listOf(TextSegment("")))
        } else {
            st.copy(segments = st.segments.toMutableList().also { it.removeAt(index) })
        }
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
        val packed = s.segments.filter { it.text.isNotEmpty() }.ifEmpty { listOf(TextSegment("")) }
        val body = LetterBodyText(packed)
        val existing = s.draftId
        return if (existing != null) {
            letters.updateDraft(existing, UpdateDraftRequest(
                recipientHandle = s.recipientHandle,
                recipientAddressId = s.recipientAddressId,
                senderAddressId = s.senderAddressId,
                stampId = s.stampId,
                stationeryId = s.stationeryId,
                fontCode = s.fontCode,
                body = body
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
                contentType = "text",
                body = body,
                replyToLetterId = replyToLetterId
            ))
            _state.update { it.copy(draftId = d.summary.id) }
            d.summary.id
        }
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
                _state.update { it.copy(status = "已寄出，预计送达: ${result.estimatedDeliveryAt}") }
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
                _state.update { it.copy(sealedUntil = isoTime, status = "已封存至 $isoTime") }
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

    fun addPhotoAttachment(mediaUrl: String, weight: Int) {
        viewModelScope.launch {
            _state.update { it.copy(attachmentBusy = true, status = null) }
            runCatching {
                val draftId = upsertDraft()
                val att = letters.addPhotoAttachment(
                    draftId,
                    AddPhotoAttachmentRequest(mediaUrl = mediaUrl, weight = weight.coerceAtLeast(1))
                )
                _state.update { it.copy(attachments = it.attachments + att) }
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
