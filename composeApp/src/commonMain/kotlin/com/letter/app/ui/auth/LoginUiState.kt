package com.letter.app.ui.auth

data class LoginUiState(
    val email: String = "test0@qq.com",
    val password: String = "zxc123456",
    val loading: Boolean = false,
    val error: String? = null
) {
    val canSubmit: Boolean get() = !loading && email.isNotBlank() && password.isNotBlank()
}

data class RegisterUiState(
    val email: String = "",
    val displayName: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null
) {
    val canSubmit: Boolean get() = !loading && email.isNotBlank() && password.length >= 8 && displayName.isNotBlank()
}
