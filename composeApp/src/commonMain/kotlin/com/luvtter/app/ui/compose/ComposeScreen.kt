package com.luvtter.app.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luvtter.app.ui.common.FlowChips
import com.luvtter.contract.dto.TextSegment
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ComposeScreen(
    onSent: () -> Unit,
    onCancel: () -> Unit,
    vm: ComposeViewModel = koinViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSealDialog by remember { mutableStateOf(false) }
    var showStickerDialog by remember { mutableStateOf(false) }
    var showPhotoDialog by remember { mutableStateOf(false) }
    ComposeContent(
        state = state,
        isReply = vm.isReply,
        showSealDialog = showSealDialog,
        showStickerDialog = showStickerDialog,
        showPhotoDialog = showPhotoDialog,
        onCancel = onCancel,
        onPickContact = vm::lookupRecipientFromContact,
        onRecipientHandleChange = vm::onRecipientHandleChange,
        onLookup = vm::lookupRecipient,
        onRecipientAddressSelect = vm::onRecipientAddressSelect,
        onSegmentTextChange = vm::onSegmentTextChange,
        onSegmentStyleToggle = vm::onSegmentStyleToggle,
        onSegmentAddAfter = vm::onSegmentAddAfter,
        onSegmentRemove = vm::onSegmentRemove,
        onStampSelect = vm::onStampSelect,
        onStationerySelect = vm::onStationerySelect,
        onFontSelect = vm::onFontSelect,
        onSenderAddressSelect = vm::onSenderAddressSelect,
        onSaveDraft = vm::saveDraft,
        onSend = { vm.send(onSent) },
        onShowSealDialog = { showSealDialog = true },
        onDismissSealDialog = { showSealDialog = false },
        onSeal = { iso -> vm.sealUntil(iso); showSealDialog = false },
        onShowStickerDialog = { showStickerDialog = true },
        onDismissStickerDialog = { showStickerDialog = false },
        onPickSticker = { id -> vm.addStickerAttachment(id); showStickerDialog = false },
        onShowPhotoDialog = { showPhotoDialog = true },
        onDismissPhotoDialog = { showPhotoDialog = false },
        onAddPhoto = { url, w -> vm.addPhotoAttachment(url, w); showPhotoDialog = false },
        onRemoveAttachment = vm::removeAttachment
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeContent(
    state: ComposeUiState,
    isReply: Boolean,
    showSealDialog: Boolean,
    showStickerDialog: Boolean,
    showPhotoDialog: Boolean,
    onCancel: () -> Unit,
    onPickContact: (handle: String) -> Unit,
    onRecipientHandleChange: (String) -> Unit,
    onLookup: () -> Unit,
    onRecipientAddressSelect: (String) -> Unit,
    onSegmentTextChange: (Int, String) -> Unit,
    onSegmentStyleToggle: (Int) -> Unit,
    onSegmentAddAfter: (Int) -> Unit,
    onSegmentRemove: (Int) -> Unit,
    onStampSelect: (String) -> Unit,
    onStationerySelect: (String?) -> Unit,
    onFontSelect: (String?) -> Unit,
    onSenderAddressSelect: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onSend: () -> Unit,
    onShowSealDialog: () -> Unit,
    onDismissSealDialog: () -> Unit,
    onSeal: (String) -> Unit,
    onShowStickerDialog: () -> Unit,
    onDismissStickerDialog: () -> Unit,
    onPickSticker: (String) -> Unit,
    onShowPhotoDialog: () -> Unit,
    onDismissPhotoDialog: () -> Unit,
    onAddPhoto: (String, Int) -> Unit,
    onRemoveAttachment: (String) -> Unit
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
            Text("信件内容", style = MaterialTheme.typography.titleSmall)
            Text(
                "每一段为独立片段,可单独划掉(保留痕迹,服务端仍存原文)。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            state.segments.forEachIndexed { index, seg ->
                SegmentEditorRow(
                    segment = seg,
                    index = index,
                    canRemove = state.segments.size > 1,
                    onTextChange = { onSegmentTextChange(index, it) },
                    onToggleStrikethrough = { onSegmentStyleToggle(index) },
                    onAddAfter = { onSegmentAddAfter(index) },
                    onRemove = { onSegmentRemove(index) }
                )
                Spacer(Modifier.height(6.dp))
            }
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

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("附件", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                val capacity = state.stampCapacity
                val weightText = if (capacity != null) "${state.totalWeight}/${capacity}g" else "${state.totalWeight}g"
                val overWeight = capacity != null && state.totalWeight > capacity
                Text(
                    weightText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overWeight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            if (state.attachments.isEmpty()) {
                Text("尚未添加附件", style = MaterialTheme.typography.bodySmall)
            } else {
                state.attachments.forEach { att ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val label = when (att.attachmentType) {
                            "sticker" -> state.stickers.firstOrNull { it.id == att.stickerId }?.name
                                ?: "贴纸"
                            "photo" -> "图片 · ${att.mediaUrl ?: "?"}"
                            else -> att.attachmentType
                        }
                        Text("· $label · ${att.weight}g", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(
                            enabled = !state.attachmentBusy,
                            onClick = { onRemoveAttachment(att.id) }
                        ) { Text("移除") }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row {
                OutlinedButton(
                    enabled = !state.attachmentBusy && state.stickers.isNotEmpty(),
                    onClick = onShowStickerDialog
                ) { Text("添加贴纸") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    enabled = !state.attachmentBusy,
                    onClick = onShowPhotoDialog
                ) { Text("添加图片") }
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
    if (showStickerDialog) {
        StickerPickerDialog(
            stickers = state.stickers,
            onDismiss = onDismissStickerDialog,
            onPick = onPickSticker
        )
    }
    if (showPhotoDialog) {
        AddPhotoDialog(
            onDismiss = onDismissPhotoDialog,
            onConfirm = onAddPhoto
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

@Composable
private fun StickerPickerDialog(
    stickers: List<com.luvtter.contract.dto.StickerDto>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择贴纸") },
        text = {
            Column {
                Text("贴纸会计入信件重量。", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                FlowChips(
                    items = stickers.map { Triple(it.id, "${it.name} · ${it.weight}g", false) },
                    onSelect = onPick
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AddPhotoDialog(onDismiss: () -> Unit, onConfirm: (String, Int) -> Unit) {
    var mediaUrl by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("10") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加图片") },
        text = {
            Column {
                Text("输入图片 URL（上传功能待接入）。重量以克计。", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = mediaUrl, onValueChange = { mediaUrl = it },
                    label = { Text("图片 URL") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.filter { c -> c.isDigit() } },
                    label = { Text("重量 (g)") }, singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = mediaUrl.isNotBlank() && (weightText.toIntOrNull()?.let { it > 0 } == true),
                onClick = { onConfirm(mediaUrl.trim(), weightText.toInt()) }
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun SegmentEditorRow(
    segment: TextSegment,
    index: Int,
    canRemove: Boolean,
    onTextChange: (String) -> Unit,
    onToggleStrikethrough: () -> Unit,
    onAddAfter: () -> Unit,
    onRemove: () -> Unit
) {
    val isStruck = segment.style == STYLE_STRIKETHROUGH
    val labelText = buildString {
        append("段 ")
        append(index + 1)
        if (isStruck) append(" · 已划掉")
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = segment.text,
            onValueChange = onTextChange,
            label = { Text(labelText) },
            textStyle = if (isStruck) {
                TextStyle(textDecoration = TextDecoration.LineThrough)
            } else {
                TextStyle.Default
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            minLines = 2
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onToggleStrikethrough) {
                Text(if (isStruck) "取消划掉" else "划掉本段")
            }
            TextButton(onClick = onAddAfter) { Text("下方新增段") }
            Spacer(Modifier.weight(1f))
            TextButton(
                enabled = canRemove,
                onClick = onRemove
            ) { Text("删除") }
        }
    }
}
