package com.letter.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.letter.contract.dto.LoginRequest
import com.letter.contract.dto.RegisterRequest
import com.letter.shared.AppContainer
import com.letter.shared.auth.Session
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    container: AppContainer,
    onSuccess: () -> Unit,
    onGoRegister: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("登录", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it.trim() },
                label = { Text("邮箱") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("密码") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                onClick = {
                    loading = true; error = null
                    scope.launch {
                        try {
                            val tokens = container.auth.login(LoginRequest(email, password, deviceName = "desktop", platform = "desktop"))
                            container.tokens.set(Session(tokens.accessToken, tokens.refreshToken, tokens.user))
                            onSuccess()
                        } catch (e: Exception) {
                            error = e.message ?: "登录失败"
                        } finally { loading = false }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (loading) "登录中..." else "登录") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onGoRegister) { Text("没有账号？去注册") }
        }
    }
}

@Composable
fun RegisterScreen(
    container: AppContainer,
    onSuccess: () -> Unit,
    onGoLogin: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("注册", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it.trim() },
                label = { Text("邮箱") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = displayName, onValueChange = { displayName = it },
                label = { Text("昵称") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("密码 (≥8位)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                enabled = !loading && email.isNotBlank() && password.length >= 8 && displayName.isNotBlank(),
                onClick = {
                    loading = true; error = null
                    scope.launch {
                        try {
                            val tokens = container.auth.register(RegisterRequest(email, password, displayName))
                            container.tokens.set(Session(tokens.accessToken, tokens.refreshToken, tokens.user))
                            onSuccess()
                        } catch (e: Exception) {
                            error = e.message ?: "注册失败"
                        } finally { loading = false }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (loading) "注册中..." else "注册") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onGoLogin) { Text("已有账号？去登录") }
        }
    }
}
