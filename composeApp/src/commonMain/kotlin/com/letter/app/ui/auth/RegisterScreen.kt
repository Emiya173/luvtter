package com.letter.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RegisterScreen(
    onSuccess: () -> Unit,
    onGoLogin: () -> Unit,
    vm: RegisterViewModel = koinViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    RegisterContent(
        state = state,
        onEmailChange = vm::onEmailChange,
        onDisplayNameChange = vm::onDisplayNameChange,
        onPasswordChange = vm::onPasswordChange,
        onSubmit = { vm.submit(onSuccess) },
        onGoLogin = onGoLogin
    )
}

@Composable
private fun RegisterContent(
    state: RegisterUiState,
    onEmailChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoLogin: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("注册", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = state.email, onValueChange = onEmailChange,
                label = { Text("邮箱") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.displayName, onValueChange = onDisplayNameChange,
                label = { Text("昵称") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.password, onValueChange = onPasswordChange,
                label = { Text("密码 (≥8位)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                enabled = state.canSubmit,
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (state.loading) "注册中..." else "注册") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onGoLogin) { Text("已有账号？去登录") }
        }
    }
}
