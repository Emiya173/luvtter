package com.luvtter.app.ui.letter

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LetterDetailScreen(
    onReply: (recipientHandle: String?) -> Unit,
    onBack: () -> Unit,
    vm: LetterDetailViewModel = koinViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showFolderPicker by remember { mutableStateOf(false) }
    LetterDetailContent(
        state = state,
        viewerId = vm.viewerId,
        showFolderPicker = showFolderPicker,
        onReply = onReply,
        onBack = onBack,
        onToggleFavorite = vm::toggleFavorite,
        onShowFolderPicker = { showFolderPicker = true },
        onDismissFolderPicker = { showFolderPicker = false },
        onAssignFolder = { id ->
            vm.assignFolder(id) { showFolderPicker = false }
        },
        onHide = { vm.hide(onBack) },
        onUnhide = { vm.unhide(onBack) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LetterDetailContent(
    state: LetterDetailUiState,
    viewerId: String?,
    showFolderPicker: Boolean,
    onReply: (recipientHandle: String?) -> Unit,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowFolderPicker: () -> Unit,
    onDismissFolderPicker: () -> Unit,
    onAssignFolder: (String?) -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("信件详情") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val d = state.detail ?: run {
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

            if (state.events.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("途中事件", style = MaterialTheme.typography.titleSmall)
                state.events.forEach { e ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(e.title ?: e.eventType, style = MaterialTheme.typography.bodyMedium)
                        e.content?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
                OutlinedButton(onClick = onToggleFavorite) {
                    Text(if (s.isFavorite) "取消收藏" else "收藏")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onShowFolderPicker) { Text("移到分类") }
                Spacer(Modifier.width(8.dp))
                if (s.hidden) {
                    OutlinedButton(onClick = onUnhide) { Text("恢复") }
                } else if (s.status == "delivered" || s.status == "read" || s.status == "in_transit") {
                    OutlinedButton(onClick = onHide) { Text("隐藏") }
                }
            }
        }
    }

    if (showFolderPicker) {
        AlertDialog(
            onDismissRequest = onDismissFolderPicker,
            title = { Text("移到分类") },
            text = {
                Column {
                    if (state.folders.isEmpty()) {
                        Text("还没有分类，请先到「分类」标签创建。", style = MaterialTheme.typography.bodySmall)
                    } else {
                        TextButton(onClick = { onAssignFolder(null) }) { Text("（移除分类）") }
                        state.folders.forEach { f ->
                            TextButton(onClick = { onAssignFolder(f.id) }) { Text(f.name) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismissFolderPicker) { Text("关闭") } }
        )
    }
}
