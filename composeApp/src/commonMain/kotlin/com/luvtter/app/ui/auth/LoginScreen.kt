package com.luvtter.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    onSuccess: () -> Unit,
    onGoRegister: () -> Unit,
    onPlayground: (() -> Unit)? = null,
    vm: LoginViewModel = koinViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LoginContent(
        state = state,
        onEmailChange = vm::onEmailChange,
        onPasswordChange = vm::onPasswordChange,
        onSubmit = { vm.submit(onSuccess) },
        onGoRegister = onGoRegister,
        onPlayground = onPlayground,
    )
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoRegister: () -> Unit,
    onPlayground: (() -> Unit)?,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("登录", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = state.email, onValueChange = onEmailChange,
                label = { Text("邮箱") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.password, onValueChange = onPasswordChange,
                label = { Text("密码") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
            ) { Text(if (state.loading) "登录中..." else "登录") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onGoRegister) { Text("没有账号？去注册") }
            onPlayground?.let {
                Spacer(Modifier.height(2.dp))
                TextButton(onClick = it) { Text("↗ 组件沙盒") }
            }
        }
    }
}
