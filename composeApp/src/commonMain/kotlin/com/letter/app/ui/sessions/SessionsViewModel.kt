package com.letter.app.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letter.shared.network.MeApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SessionsViewModel(private val me: MeApi) : ViewModel() {
    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { me.listSessions() }
                .onSuccess { list -> _state.update { it.copy(sessions = list) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
            _state.update { it.copy(loading = false) }
        }
    }

    fun revoke(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(revokingId = id, error = null) }
            runCatching { me.revokeSession(id) }
                .onSuccess {
                    _state.update { s ->
                        s.copy(sessions = s.sessions.filterNot { it.id == id })
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = "撤销失败: ${e.message}") } }
            _state.update { it.copy(revokingId = null) }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
