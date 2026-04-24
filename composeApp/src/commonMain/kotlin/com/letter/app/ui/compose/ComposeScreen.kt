package com.letter.app.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letter.app.ui.common.FlowChips
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ComposeScreen(
    onSent: () -> Unit,
    onCancel: () -> Unit,
    vm: ComposeViewModel = koinViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSealDialog by remember { mutableStateOf(false) }
    ComposeContent(
        state = state,
        isReply = vm.isReply,
        showSealDialog = showSealDialog,
        onCancel = onCancel,
        onPickContact = vm::lookupRecipientFromContact,
        onRecipientHandleChange = vm::onRecipientHandleChange,
        onLookup = vm::lookupRecipient,
        onRecipientAddressSelect = vm::onRecipientAddressSelect,
        onContentChange = vm::onContentChange,
        onStampSelect = vm::onStampSelect,
        onStationerySelect = vm::onStationerySelect,
        onFontSelect = vm::onFontSelect,
        onSenderAddressSelect = vm::onSenderAddressSelect,
        onSaveDraft = vm::saveDraft,
        onSend = { vm.send(onSent) },
        onShowSealDialog = { showSealDialog = true },
        onDismissSealDialog = { showSealDialog = false },
        onSeal = { iso -> vm.sealUntil(iso); showSealDialog = false }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeContent(
    state: ComposeUiState,
    isReply: Boolean,
    showSealDialog: Boolean,
    onCancel: () -> Unit,
    onPickContact: (handle: String) -> Unit,
    onRecipientHandleChange: (String) -> Unit,
    onLookup: () -> Unit,
    onRecipientAddressSelect: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onStampSelect: (String) -> Unit,
    onStationerySelect: (String?) -> Unit,
    onFontSelect: (String?) -> Unit,
    onSenderAddressSelect: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onSend: () -> Unit,
    onShowSealDialog: () -> Unit,
    onDismissSealDialog: () -> Unit,
    onSeal: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isReply) "回信" else "写一封信") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("取消") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            if (state.contacts.isNotEmpty()) {
                Text("从联系人选择", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                FlowChips(
                    items = state.contacts.map {
                        Triple(it.target.handle, "${it.target.displayName} · @${it.target.handle}", state.recipientHandle == it.target.handle)
                    },
                    onSelect = onPickContact
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.recipientHandle, onValueChange = onRecipientHandleChange,
                    label = { Text("收件人 handle") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(enabled = !state.lookupBusy && state.recipientHandle.isNotBlank(), onClick = onLookup) {
                    Text(if (state.lookupBusy) "查找中" else "查找")
                }
            }
            state.recipientName?.let {
                Text("→ $it", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            }
            if (state.recipientAddresses.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("寄到对方哪个地址", style = MaterialTheme.typography.labelMedium)
                FlowChips(
                    items = state.recipientAddresses.map { Triple(it.id, "${it.label} · ${it.type}" + if (it.isDefault) " · 默认" else "", state.recipientAddressId == it.id) },
                    onSelect = onRecipientAddressSelect
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.content, onValueChange = onContentChange,
                label = { Text("信件内容") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                minLines = 6
            )
            Spacer(Modifier.height(16.dp))

            Text("邮票", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            FlowChips(
                items = state.stamps.map { stamp ->
                    val qty = state.assets?.stamps?.firstOrNull { it.assetId == stamp.id }?.quantity ?: 0
                    Triple(stamp.id, "${stamp.name}×$qty (${stamp.weightCapacity}g)", state.stampId == stamp.id)
                },
                onSelect = onStampSelect
            )

            if (state.stationeries.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("信纸", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                FlowChips(
                    items = listOf(Triple("", "无", state.stationeryId == null)) +
                        state.stationeries.map { Triple(it.id, it.name, state.stationeryId == it.id) },
                    onSelect = { onStationerySelect(it.ifBlank { null }) }
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("字体", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            FlowChips(
                items = FONT_OPTIONS.map { (code, label) ->
                    Triple(code.orEmpty(), label, state.fontCode == code)
                },
                onSelect = { onFontSelect(it.ifBlank { null }) }
            )

            Spacer(Modifier.height(16.dp))
            Text("寄件地址", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            if (state.senderAddresses.isEmpty()) {
                Text("尚未创建地址，请先到「址」管理。", style = MaterialTheme.typography.bodySmall)
            } else {
                FlowChips(
                    items = state.senderAddresses.map { Triple(it.id, "${it.label} · ${it.type}", state.senderAddressId == it.id) },
                    onSelect = onSenderAddressSelect
                )
            }

            state.sealedUntil?.let {
                Spacer(Modifier.height(8.dp))
                Text("封存中：$it（期间不可编辑/寄出）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }

            state.status?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    enabled = state.canSaveDraft,
                    onClick = onSaveDraft,
                    modifier = Modifier.weight(1f)
                ) { Text("保存草稿") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    enabled = state.canSaveDraft,
                    onClick = onShowSealDialog,
                    modifier = Modifier.weight(1f)
                ) { Text("封存草稿") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = state.canSend,
                    onClick = onSend,
                    modifier = Modifier.weight(1f)
                ) { Text(if (state.loading) "处理中..." else "寄出") }
            }
        }
    }

    if (showSealDialog) {
        SealDraftDialog(
            onDismiss = onDismissSealDialog,
            onConfirm = onSeal
        )
    }
}

@Composable
private fun SealDraftDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var minutes by remember { mutableStateOf("60") }
    val presets = listOf(5 to "5 分钟", 60 to "1 小时", 60 * 24 to "1 天", 60 * 24 * 7 to "7 天")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("封存至未来") },
        text = {
            Column {
                Text("封存期间草稿不可编辑/寄出。选择预设或输入分钟数：", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                FlowChips(
                    items = presets.map { (m, label) -> Triple(m.toString(), label, minutes == m.toString()) },
                    onSelect = { minutes = it }
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                    label = { Text("分钟") }, singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = minutes.toIntOrNull()?.let { it > 0 } == true,
                onClick = {
                    val m = minutes.toInt()
                    val target = kotlin.time.Clock.System.now().plus(kotlin.time.Duration.parse("${m}m"))
                    onConfirm(target.toString())
                }
            ) { Text("封存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
