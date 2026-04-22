package com.letter.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.letter.contract.dto.LetterDetailDto
import com.letter.shared.AppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterDetailScreen(
    container: AppContainer,
    letterId: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<LetterDetailDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(letterId) {
        try {
            detail = container.letters.detail(letterId)
            if (detail?.summary?.status == "delivered") {
                runCatching { container.letters.markRead(letterId) }
            }
        } catch (e: Exception) { error = e.message }
    }

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
            Text(s.sender?.displayName ?: "—", style = MaterialTheme.typography.titleMedium)
            Text("→ ${s.recipient?.displayName ?: "—"}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text("状态: ${s.status}${s.transitStage?.let { " ($it)" } ?: ""}", style = MaterialTheme.typography.labelMedium)
            s.deliveryAt?.let { Text("预计送达: $it", style = MaterialTheme.typography.labelSmall) }
            s.deliveredAt?.let { Text("送达时间: $it", style = MaterialTheme.typography.labelSmall) }
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

            Spacer(Modifier.height(24.dp))
            if (s.status == "delivered" || s.status == "read") {
                OutlinedButton(onClick = {
                    scope.launch {
                        runCatching { container.letters.hide(letterId) }
                        onBack()
                    }
                }) { Text("隐藏") }
            }
        }
    }
}
