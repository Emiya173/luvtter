package com.letter.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.letter.contract.dto.*
import com.letter.shared.AppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    container: AppContainer,
    onSent: () -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var stamps by remember { mutableStateOf<List<StampDto>>(emptyList()) }
    var addresses by remember { mutableStateOf<List<AddressDto>>(emptyList()) }
    var assets by remember { mutableStateOf<MyAssetsDto?>(null) }

    var recipientHandle by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var stampId by remember { mutableStateOf<String?>(null) }
    var senderAddressId by remember { mutableStateOf<String?>(null) }

    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            stamps = container.catalog.stamps()
            addresses = container.addresses.list()
            assets = container.catalog.myAssets()
            stampId = stamps.firstOrNull()?.id
            senderAddressId = addresses.firstOrNull { it.isDefault }?.id ?: addresses.firstOrNull()?.id
        } catch (e: Exception) { status = "加载失败: ${e.message}" }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("写一封信") }, navigationIcon = { TextButton(onClick = onCancel) { Text("取消") } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = recipientHandle, onValueChange = { recipientHandle = it.trim() },
                label = { Text("收件人 handle") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = content, onValueChange = { content = it },
                label = { Text("信件内容") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                minLines = 6
            )
            Spacer(Modifier.height(16.dp))

            Text("邮票", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            FlowChips(
                items = stamps.map { stamp ->
                    val qty = assets?.stamps?.firstOrNull { it.assetId == stamp.id }?.quantity ?: 0
                    val label = "${stamp.name}×$qty (${stamp.weightCapacity}g)"
                    Triple(stamp.id, label, stampId == stamp.id)
                },
                onSelect = { stampId = it }
            )

            Spacer(Modifier.height(16.dp))
            Text("寄件地址", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            if (addresses.isEmpty()) {
                Text("尚未创建地址，请先到「址」管理。", style = MaterialTheme.typography.bodySmall)
            } else {
                FlowChips(
                    items = addresses.map { Triple(it.id, "${it.label} · ${it.type}", senderAddressId == it.id) },
                    onSelect = { senderAddressId = it }
                )
            }

            status?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                enabled = !loading && recipientHandle.isNotBlank() && content.isNotBlank() && stampId != null,
                onClick = {
                    loading = true; status = null
                    scope.launch {
                        try {
                            val draft = container.letters.createDraft(
                                CreateDraftRequest(
                                    recipientHandle = recipientHandle,
                                    senderAddressId = senderAddressId,
                                    stampId = stampId,
                                    contentType = "text",
                                    body = LetterBodyText(listOf(TextSegment(content)))
                                )
                            )
                            val result = container.letters.send(draft.summary.id)
                            status = "已寄出，预计送达: ${result.estimatedDeliveryAt}"
                            onSent()
                        } catch (e: Exception) {
                            status = "寄信失败: ${e.message}"
                        } finally { loading = false }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (loading) "寄送中..." else "寄出") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowChips(items: List<Triple<String, String, Boolean>>, onSelect: (String) -> Unit) {
    Column {
        items.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { (id, label, selected) ->
                    FilterChip(
                        selected = selected,
                        onClick = { onSelect(id) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 6.dp, bottom = 6.dp)
                    )
                }
            }
        }
    }
}
