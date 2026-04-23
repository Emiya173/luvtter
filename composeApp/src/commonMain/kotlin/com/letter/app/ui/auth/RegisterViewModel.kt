package com.letter.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letter.contract.dto.RegisterRequest
import com.letter.shared.auth.Session
import com.letter.shared.auth.TokenStore
import com.letter.shared.network.AuthApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val auth: AuthApi,
    private val tokens: TokenStore
) : ViewModel() {
    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onEmailChange(v: String) = _state.update { it.copy(email = v.trim()) }
    fun onDisplayNameChange(v: String) = _state.update { it.copy(displayName = v) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v) }

    fun submit(onSuccess: () -> Unit) {
        val s = _state.value
        if (!s.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val t = auth.register(RegisterRequest(s.email, s.password, s.displayName))
                tokens.set(Session(t.accessToken, t.refreshToken, t.user))
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "注册失败") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
