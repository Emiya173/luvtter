package com.letter.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.letter.contract.dto.LetterSummaryDto
import com.letter.shared.AppContainer
import kotlinx.coroutines.launch

private enum class Tab(val label: String) { Inbox("收件箱"), Outbox("发件箱") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onCompose: () -> Unit,
    onAddresses: () -> Unit,
    onOpenLetter: (String) -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(Tab.Inbox) }
    var letters by remember { mutableStateOf<List<LetterSummaryDto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var unread by remember { mutableStateOf(0) }
    var reward by remember { mutableStateOf<String?>(null) }

    val user = container.tokens.current()?.user

    LaunchedEffect(Unit) {
        runCatching { unread = container.notifications.unreadCount() }
        runCatching {
            val r = container.dailyReward.claim(java.util.TimeZone.getDefault().id)
            if (r.claimed) reward = "今日奖励已发放"
        }
    }

    suspend fun reload() {
        loading = true; error = null
        try {
            letters = when (tab) {
                Tab.Inbox -> container.letters.inbox()
                Tab.Outbox -> container.letters.outbox()
            }
        } catch (e: Exception) {
            error = e.message
        } finally { loading = false }
    }

    LaunchedEffect(tab) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(user?.displayName?.let { "你好，$it" } ?: "信件") },
                actions = {
                    BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { container.notifications.markAllRead() }
                                unread = 0
                            }
                        }) { Text("铃") }
                    }
                    IconButton(onClick = onAddresses) { Text("址", style = MaterialTheme.typography.labelLarge) }
                    IconButton(onClick = {
                        scope.launch {
                            reload()
                            runCatching { unread = container.notifications.unreadCount() }
                        }
                    }) { Text("⟳") }
                    TextButton(onClick = onLogout) { Text("退出") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCompose, text = { Text("写信") }, icon = { Text("✉") })
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                Tab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) })
                }
            }
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            }
            reward?.let {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!loading && letters.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有信件", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(letters, key = { it.id }) { l ->
                        LetterRow(l, mine = tab == Tab.Outbox, onClick = { onOpenLetter(l.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun LetterRow(l: LetterSummaryDto, mine: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val who = if (mine) (l.recipient?.displayName ?: "未知收件人") else (l.sender?.displayName ?: "未知寄件人")
            Text(who, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            AssistChip(onClick = {}, label = { Text(statusLabel(l.status, l.transitStage)) })
        }
        l.preview?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
        Spacer(Modifier.height(4.dp))
        val time = l.deliveredAt ?: l.deliveryAt ?: l.sentAt
        time?.let {
            Text(it.take(19).replace('T', ' '), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun statusLabel(status: String, stage: String?): String = when (status) {
    "draft" -> "草稿"
    "sealed" -> "已封缄"
    "in_transit" -> when (stage) {
        "sending" -> "投递中"
        "on_the_way" -> "在路上"
        "arriving" -> "即将送达"
        else -> "运输中"
    }
    "delivered" -> "已送达"
    "read" -> "已读"
    "hidden" -> "已隐藏"
    else -> status
}
