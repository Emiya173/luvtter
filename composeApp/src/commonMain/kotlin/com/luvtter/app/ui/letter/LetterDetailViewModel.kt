package com.luvtter.app.ui.letter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.luvtter.app.navigation.LetterDetailRoute
import com.luvtter.shared.auth.TokenStore
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
            _state.update { it.copy(detail = detail, events = events, folders = folderList) }
            if (detail.summary.status == "delivered") runCatching { letters.markRead(letterId) }
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message) }
        }
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
}
