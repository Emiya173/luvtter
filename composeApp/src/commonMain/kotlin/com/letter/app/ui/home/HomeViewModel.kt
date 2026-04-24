package com.letter.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letter.contract.dto.CreateFolderRequest
import com.letter.contract.dto.FinalizeHandleRequest
import com.letter.contract.dto.LetterSummaryDto
import com.letter.contract.dto.UserDto
import com.letter.shared.auth.TokenStore
import com.letter.shared.network.AddressApi
import com.letter.shared.network.DailyRewardApi
import com.letter.shared.network.FolderApi
import com.letter.shared.network.LetterApi
import com.letter.shared.network.MeApi
import com.letter.shared.network.NotificationApi
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeViewModel(
    private val tokens: TokenStore,
    private val letters: LetterApi,
    private val addresses: AddressApi,
    private val folders: FolderApi,
    private val notifications: NotificationApi,
    private val dailyReward: DailyRewardApi,
    private val me: MeApi
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    val session get() = tokens.session

    init {
        viewModelScope.launch {
            reloadAll()
            runCatching {
                val r = dailyReward.claim(TimeZone.currentSystemDefault().id)
                if (r.claimed) _state.update { it.copy(reward = "今日奖励已发放") }
            }
        }
        viewModelScope.launch { reload() }
        viewModelScope.launch {
            notifications.stream()
                .retry { e ->
                    delay(3000)
                    true
                }
                .catch { /* swallow terminal errors */ }
                .collect { dto ->
                    _state.update { s ->
                        s.copy(
                            notifications = listOf(dto) + s.notifications.filterNot { it.id == dto.id },
                            unread = s.unread + 1
                        )
                    }
                }
        }
    }

    fun selectTab(tab: HomeTab) {
        if (_state.value.tab == tab) return
        _state.update { it.copy(tab = tab) }
        viewModelScope.launch { reload() }
    }

    fun toggleShowHidden() {
        _state.update { it.copy(showHidden = !it.showHidden) }
        viewModelScope.launch { reload() }
    }

    fun selectFolder(id: String?) {
        _state.update { it.copy(selectedFolderId = id) }
        viewModelScope.launch { reload() }
    }

    fun refreshAll() {
        viewModelScope.launch { reload(); reloadAll() }
    }

    private suspend fun reloadAll() {
        runCatching { _state.update { it.copy(addresses = addresses.list()) } }
        runCatching { _state.update { it.copy(folders = folders.list()) } }
        runCatching { _state.update { it.copy(notifications = notifications.list()) } }
        runCatching { _state.update { it.copy(unread = notifications.unreadCount()) } }
    }

    fun reloadLetters() { viewModelScope.launch { reload() } }

    private suspend fun reload() {
        val s = _state.value
        _state.update { it.copy(loading = true, error = null) }
        try {
            val list = when (s.tab) {
                HomeTab.Inbox -> letters.inbox(hidden = s.showHidden)
                HomeTab.Outbox -> letters.outbox(hidden = s.showHidden)
                HomeTab.Drafts -> letters.listDrafts()
                HomeTab.Favorites -> letters.favorites()
                HomeTab.Folders -> s.selectedFolderId?.let { letters.byFolder(it) } ?: emptyList()
            }
            _state.update { it.copy(letters = list) }
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message) }
        } finally {
            _state.update { it.copy(loading = false) }
        }
    }

    fun openNotifications() {
        viewModelScope.launch {
            runCatching { _state.update { it.copy(notifications = notifications.list()) } }
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            runCatching { notifications.markAllRead() }
            val list = runCatching { notifications.list() }.getOrDefault(emptyList())
            _state.update { it.copy(notifications = list, unread = 0) }
        }
    }

    fun switchCurrentAddress(addressId: String) {
        viewModelScope.launch {
            runCatching {
                val u = me.setCurrentAddress(addressId)
                tokens.updateUser(u)
            }.onFailure { e -> _state.update { it.copy(error = e.message) } }
            reload()
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            runCatching { folders.create(CreateFolderRequest(name)) }
                .onSuccess {
                    val f = runCatching { folders.list() }.getOrDefault(_state.value.folders)
                    _state.update { it.copy(folders = f) }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch {
            runCatching { folders.delete(id) }
                .onSuccess {
                    val f = runCatching { folders.list() }.getOrDefault(_state.value.folders)
                    val sel = if (_state.value.selectedFolderId == id) null else _state.value.selectedFolderId
                    _state.update { it.copy(folders = f, selectedFolderId = sel) }
                    reload()
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun deleteDraft(id: String) = mutate("删除草稿失败") { letters.deleteDraft(id) }
    fun expedite(id: String) = mutate("加速失败") { letters.expedite(id, 5) }
    fun hide(id: String) = mutate("隐藏失败") { letters.hide(id) }
    fun unhide(id: String) = mutate("恢复失败") { letters.unhide(id) }

    fun finalizeHandle(value: String, onDone: (UserDto) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { me.finalizeHandle(FinalizeHandleRequest(value)) }
                .onSuccess { u ->
                    tokens.updateUser(u)
                    onDone(u)
                }
                .onFailure { e -> onError(e.message ?: "提交失败") }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun mutate(errPrefix: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { reload() }
                .onFailure { e -> _state.update { it.copy(error = "$errPrefix: ${e.message}") } }
        }
    }

    fun letterOwnedByMe(l: LetterSummaryDto): Boolean {
        val uid = tokens.session.value?.user?.id
        return when (_state.value.tab) {
            HomeTab.Outbox, HomeTab.Drafts -> true
            HomeTab.Inbox -> false
            else -> l.sender?.id == uid
        }
    }
}
