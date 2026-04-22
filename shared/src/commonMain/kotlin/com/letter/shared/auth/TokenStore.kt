package com.letter.shared.auth

import com.letter.contract.dto.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto
)

class TokenStore {
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session

    fun current(): Session? = _session.value
    fun accessToken(): String? = _session.value?.accessToken

    fun set(session: Session) {
        _session.value = session
    }

    fun updateUser(user: UserDto) {
        val s = _session.value ?: return
        _session.value = s.copy(user = user)
    }

    fun clear() {
        _session.value = null
    }
}
