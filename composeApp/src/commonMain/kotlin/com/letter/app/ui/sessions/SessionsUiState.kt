package com.letter.app.ui.sessions

import com.letter.contract.dto.SessionDto

data class SessionsUiState(
    val sessions: List<SessionDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val revokingId: String? = null
)
