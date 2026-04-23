package com.letter.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.letter.contract.dto.FolderDto
import com.letter.contract.dto.LetterDetailDto
import com.letter.contract.dto.LetterEventDto
import com.letter.shared.AppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterDetailScreen(
    container: AppContainer,
    letterId: String,
    onReply: (recipientHandle: String?) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<LetterDetailDto?>(null) }
    var events by remember { mutableStateOf<List<LetterEventDto>>(emptyList()) }
    var folders by remember { mutableStateOf<List<FolderDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showFolderPicker by remember { mutableStateOf(false) }
    val viewerId = container.tokens.session.collectAsState().value?.user?.id

    suspend fun reload() {
        try {
            detail = container.letters.detail(letterId)
            events = runCatching { container.letters.events(letterId) }.getOrDefault(emptyList())
            folders = runCatching { container.folders.list() }.getOrDefault(emptyList())
            if (detail?.summary?.status == "delivered") {
                runCatching { container.letters.markRead(letterId) }
            }
        } catch (e: Exception) { error = e.message }
    }

    LaunchedEffect(letterId) { reload() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("信件详情") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val d = detail ?: run {
                Text("加载中...")
                return@Column
            }
            val s = d.summary
            val viewerIsRecipient = viewerId != null && s.recipient?.id == viewerId
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.sender?.displayName ?: "—", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (s.isFavorite) {
                    AssistChip(onClick = {}, label = { Text("★ 已收藏") })
                }
            }
            Text("→ ${s.recipient?.displayName ?: "—"}", style = MaterialTheme.typography.bodyMedium)
            s.recipientAddressLabel?.takeIf { it.isNotBlank() }?.let {
                Text("收件地址：$it", style = MaterialTheme.typography.labelMedium)
            }
            s.replyToLetterId?.let {
                Text("↩ 回复此前的来信", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text("状态: ${s.status}${s.transitStage?.let { " ($it)" } ?: ""}", style = MaterialTheme.typography.labelMedium)
            s.deliveryAt?.let { Text("预计送达: $it", style = MaterialTheme.typography.labelSmall) }
            s.deliveredAt?.let { Text("送达时间: $it", style = MaterialTheme.typography.labelSmall) }
            if (s.hidden) {
                Spacer(Modifier.height(4.dp))
                Text("（这封信已从你的箱子中隐藏）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            val text = buildAnnotatedString {
                d.body?.segments?.forEach { seg ->
                    if (seg.style == "strikethrough") {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(seg.text) }
                    } else append(seg.text)
                }
            }
            if (text.text.isEmpty()) {
                Text(d.bodyUrl ?: "（无正文）", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }

            if (events.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("途中事件", style = MaterialTheme.typography.titleSmall)
                events.forEach { e ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            e.title ?: e.eventType,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        e.content?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(e.visibleAt.take(19).replace('T', ' '), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (viewerIsRecipient && (s.status == "delivered" || s.status == "read")) {
                    Button(onClick = { onReply(s.sender?.handle) }) { Text("回信") }
                    Spacer(Modifier.width(8.dp))
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        runCatching {
                            if (s.isFavorite) container.letters.unfavorite(letterId)
                            else container.letters.favorite(letterId)
                        }.onSuccess { reload() }.onFailure { error = it.message }
                    }
                }) { Text(if (s.isFavorite) "取消收藏" else "收藏") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { showFolderPicker = true }) { Text("移到分类") }
                Spacer(Modifier.width(8.dp))
                if (s.hidden) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            runCatching { container.letters.unhide(letterId) }
                                .onSuccess { onBack() }
                                .onFailure { error = it.message }
                        }
                    }) { Text("恢复") }
                } else if (s.status == "delivered" || s.status == "read" || s.status == "in_transit") {
                    OutlinedButton(onClick = {
                        scope.launch {
                            runCatching { container.letters.hide(letterId) }
                                .onSuccess { onBack() }
                                .onFailure { error = it.message }
                        }
                    }) { Text("隐藏") }
                }
            }
        }
    }

    if (showFolderPicker) {
        AlertDialog(
            onDismissRequest = { showFolderPicker = false },
            title = { Text("移到分类") },
            text = {
                Column {
                    if (folders.isEmpty()) {
                        Text("还没有分类，请先到「分类」标签创建。", style = MaterialTheme.typography.bodySmall)
                    } else {
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { container.folders.assign(letterId, null) }
                                    .onSuccess { showFolderPicker = false; reload() }
                                    .onFailure { error = it.message }
                            }
                        }) { Text("（移除分类）") }
                        folders.forEach { f ->
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { container.folders.assign(letterId, f.id) }
                                        .onSuccess { showFolderPicker = false; reload() }
                                        .onFailure { error = it.message }
                                }
                            }) { Text(f.name) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFolderPicker = false }) { Text("关闭") } }
        )
    }
}
