package com.luvtter.app.ui.letter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.luvtter.app.navigation.LetterDetailRoute
import com.luvtter.shared.auth.TokenStore
import com.luvtter.shared.network.CatalogApi
import com.luvtter.shared.network.FolderApi
import com.luvtter.shared.network.LetterApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LetterDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val letters: LetterApi,
    private val folders: FolderApi,
    private val catalog: CatalogApi,
    private val tokens: TokenStore
) : ViewModel() {

    private val route: LetterDetailRoute = savedStateHandle.toRoute()
    val letterId: String = route.id
    val viewerId get() = tokens.session.value?.user?.id

    private val _state = MutableStateFlow(LetterDetailUiState())
    val state: StateFlow<LetterDetailUiState> = _state.asStateFlow()

    init { viewModelScope.launch { reload() } }

    private suspend fun reload() {
        try {
            val detail = letters.detail(letterId)
            val events = runCatching { letters.events(letterId) }.getOrDefault(emptyList())
            val folderList = runCatching { folders.list() }.getOrDefault(emptyList())
            val stickerList = runCatching { catalog.stickers() }.getOrDefault(emptyList())
            _state.update {
                it.copy(detail = detail, events = events, folders = folderList, stickers = stickerList)
            }
            // markRead 不再在 reload 里触发 —— 由用户点击火漆拆封时显式调用 [markRead]。
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message) }
        }
    }

    /** 用户点击火漆拆封时调用,服务端把 delivered → read,扣减 unread 计数。已读则幂等 no-op。 */
    fun markRead() {
        val s = _state.value.detail?.summary ?: return
        if (s.status != "delivered") return
        viewModelScope.launch { runCatching { letters.markRead(letterId) } }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val fav = _state.value.detail?.summary?.isFavorite ?: return@launch
            runCatching { if (fav) letters.unfavorite(letterId) else letters.favorite(letterId) }
                .onSuccess { reload() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun assignFolder(folderId: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { folders.assign(letterId, folderId) }
                .onSuccess { reload(); onDone() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun hide(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { letters.hide(letterId) }
                .onSuccess { onDone() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun unhide(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { letters.unhide(letterId) }
                .onSuccess { onDone() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** 浮层播完一张事件后调,把 read_at 写到服务端,跨会话不再重播。失败静默(本地仍然短期记住)。 */
    fun markEventRead(eventId: String) {
        viewModelScope.launch {
            runCatching { letters.markEventRead(letterId, eventId) }
            _state.update { st ->
                st.copy(events = st.events.map {
                    if (it.id == eventId && it.readAt == null) it.copy(readAt = "client") else it
                })
            }
        }
    }
}
