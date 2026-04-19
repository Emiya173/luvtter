package com.letter.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.letter.shared.network.HelloApi
import com.letter.shared.network.createHttpClient
import kotlinx.coroutines.launch

/**
 * 平台特定的 API 根地址
 * - Android 模拟器: 10.0.2.2
 * - Desktop / iOS 模拟器: localhost
 */
expect val apiBaseUrl: String

@Composable
fun App() {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        val api = remember { HelloApi(createHttpClient(apiBaseUrl)) }

        var message by remember { mutableStateOf("点击下方按钮连接服务端") }
        var loading by remember { mutableStateOf(false) }

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("信件应用", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text("Letter App · 0.1.0", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(32.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = message, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(24.dp))

                Button(
                    enabled = !loading,
                    onClick = {
                        loading = true
                        scope.launch {
                            try {
                                val resp = api.hello()
                                message = "${resp.message}\n时间: ${resp.serverTime}"
                            } catch (e: Exception) {
                                message = "连接失败: ${e.message}"
                            } finally {
                                loading = false
                            }
                        }
                    }
                ) {
                    Text(if (loading) "连接中..." else "Ping 服务端")
                }
            }
        }
    }
}
