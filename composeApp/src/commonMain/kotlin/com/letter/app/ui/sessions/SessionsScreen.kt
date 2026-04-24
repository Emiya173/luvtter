package com.letter.app.ui.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letter.contract.dto.SessionDto
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SessionsScreen(
    onBack: () -> Unit,
    vm: SessionsViewModel = koinViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    SessionsContent(
        state = state,
        onBack = onBack,
        onRefresh = vm::refresh,
        onRevoke = vm::revoke,
        onClearError = vm::clearError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionsContent(
    state: SessionsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRevoke: (String) -> Unit,
    onClearError: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("已登录设备") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    TextButton(enabled = !state.loading, onClick = onRefresh) { Text("刷新") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "撤销某条会话后,对应设备的 refresh token 失效,需要重新登录。" +
                    "access token 直到过期(默认 1 小时)仍可能有效。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                AssistChip(onClick = onClearError, label = { Text(it) })
            }
            Spacer(Modifier.height(12.dp))
            if (state.loading && state.sessions.isEmpty()) {
                Text("加载中…")
            } else if (state.sessions.isEmpty()) {
                Text("尚无活跃会话。")
            } else {
                LazyColumn {
                    items(state.sessions, key = { it.id }) { s ->
                        SessionRow(
                            session = s,
                            revoking = state.revokingId == s.id,
                            onRevoke = { onRevoke(s.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionDto, revoking: Boolean, onRevoke: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val device = session.deviceName ?: "未知设备"
            val platform = session.platform?.let { " · $it" } ?: ""
            Text("$device$platform", style = MaterialTheme.typography.titleSmall)
            Text(
                "最近活跃: ${session.lastActiveAt}",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "到期: ${session.expiresAt}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(enabled = !revoking, onClick = onRevoke) {
            Text(if (revoking) "撤销中" else "撤销")
        }
    }
}
