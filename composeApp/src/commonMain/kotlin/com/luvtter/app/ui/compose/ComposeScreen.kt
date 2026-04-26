package com.luvtter.app.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.luvtter.app.ui.common.FlowChips
import com.luvtter.app.ui.common.formatLocalDateTime
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
    ComposeContent(
        state = state,
        isReply = vm.isReply,
        showSealDialog = showSealDialog,
        showStickerDialog = showStickerDialog,
        onCancel = onCancel,
        onPickContact = vm::lookupRecipientFromContact,
        onRecipientHandleChange = vm::onRecipientHandleChange,
        onLookup = vm::lookupRecipient,
        onRecipientAddressSelect = vm::onRecipientAddressSelect,
        onEditorChange = vm::onEditorChange,
        onStrikeSelection = vm::strikeSelection,
        onUnstrikeSelection = vm::unstrikeSelection,
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
        onPickPhoto = vm::pickAndUploadPhoto,
        onRemoveAttachment = vm::removeAttachment,
        onModeSelect = vm::setMode,
        onPickScan = vm::pickAndUploadScan,
        onClearScan = vm::clearScan,
        onToggleStrikeOnDelete = vm::toggleStrikeOnDelete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeContent(
    state: ComposeUiState,
    isReply: Boolean,
    showSealDialog: Boolean,
    showStickerDialog: Boolean,
    onCancel: () -> Unit,
    onPickContact: (handle: String) -> Unit,
    onRecipientHandleChange: (String) -> Unit,
    onLookup: () -> Unit,
    onRecipientAddressSelect: (String) -> Unit,
    onEditorChange: (TextFieldValue) -> Unit,
    onStrikeSelection: () -> Unit,
    onUnstrikeSelection: () -> Unit,
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
    onPickPhoto: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onModeSelect: (String) -> Unit,
    onPickScan: () -> Unit,
    onClearScan: () -> Unit,
    onToggleStrikeOnDelete: () -> Unit,
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
            Spacer(Modifier.height(4.dp))
            FlowChips(
                items = listOf(
                    Triple("text", "键入文本", state.mode == "text"),
                    Triple("scan", "扫描信", state.mode == "scan"),
                ),
                onSelect = onModeSelect
            )
            Spacer(Modifier.height(6.dp))

            if (state.mode == "scan") {
                ScanEditorSection(
                    state = state,
                    onPickScan = onPickScan,
                    onClearScan = onClearScan
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = state.strikeOnDelete, onCheckedChange = { onToggleStrikeOnDelete() })
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.strikeOnDelete)
                            "涂改模式开:停顿 ${state.strikeOnDeleteWindowMs / 1000} 秒后再 backspace,删字保留为划线"
                        else "涂改模式关",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    val hasSelection = !state.editorSelection.collapsed
                    TextButton(enabled = hasSelection, onClick = onStrikeSelection) { Text("划掉所选") }
                    TextButton(enabled = hasSelection, onClick = onUnstrikeSelection) { Text("撤销划线") }
                }
                Spacer(Modifier.height(6.dp))
                LetterBodyEditor(
                    text = state.editorText,
                    selection = state.editorSelection,
                    struckMask = state.struckMask,
                    onChange = onEditorChange,
                )
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
                            "photo" -> {
                                val key = att.objectKey?.substringAfterLast('/')
                                "图片 · ${key ?: att.mediaUrl ?: "?"}"
                            }
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
                    onClick = onPickPhoto
                ) { Text(if (state.attachmentBusy) "处理中..." else "选图并上传") }
            }

            state.sealedUntil?.let {
                Spacer(Modifier.height(8.dp))
                Text("封存中：${formatLocalDateTime(it) ?: it}（期间不可编辑/寄出）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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
private fun LetterBodyEditor(
    text: String,
    selection: androidx.compose.ui.text.TextRange,
    struckMask: List<Boolean>,
    onChange: (TextFieldValue) -> Unit,
) {
    val struckColor = MaterialTheme.colorScheme.onSurfaceVariant
    // 字符级 visualTransformation:同位置字符若 struck=true,加 line-through 染色;
    // 长度不变,所以 OffsetMapping.Identity 足够,光标可在划线 / 非划线之间自由移动。
    val transformation = remember(struckMask, struckColor) {
        strikeMaskTransformation(struckMask, struckColor)
    }
    OutlinedTextField(
        value = TextFieldValue(text = text, selection = selection),
        onValueChange = onChange,
        label = { Text(if (struckMask.any { it }) "信件正文(含划痕)" else "信件正文") },
        visualTransformation = transformation,
        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
        minLines = 8
    )
}

private fun strikeMaskTransformation(mask: List<Boolean>, color: Color): VisualTransformation =
    VisualTransformation { input ->
        val annotated = buildAnnotatedString {
            input.text.forEachIndexed { i, c ->
                if (mask.getOrElse(i) { false }) {
                    withStyle(
                        SpanStyle(textDecoration = TextDecoration.LineThrough, color = color)
                    ) { append(c) }
                } else {
                    append(c)
                }
            }
        }
        TransformedText(annotated, OffsetMapping.Identity)
    }

@Composable
private fun ScanEditorSection(
    state: ComposeUiState,
    onPickScan: () -> Unit,
    onClearScan: () -> Unit,
) {
    Text(
        "扫描信:上传一张图片或 PDF 作为信件主体。文本类编辑器将被替代,寄出后会进入 OCR 索引以便搜索。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    if (state.scanBound) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("已绑定:${state.scanFilename ?: "(已上传)"}", style = MaterialTheme.typography.bodyMedium)
            val previewUrl = state.scanPreviewUrl
            val ct = state.scanContentType
            val isImage = ct == null && previewUrl != null && Regex("\\.(jpe?g|png|webp|gif)(\\?|$)").containsMatchIn(previewUrl)
                || ct?.startsWith("image/") == true
            if (previewUrl != null && isImage) {
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = previewUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(8.dp))
                )
            }
            if (previewUrl != null && !isImage) {
                val uri = LocalUriHandler.current
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { uri.openUri(previewUrl) }) {
                    Text("在浏览器中打开 PDF")
                }
            }
            Spacer(Modifier.height(4.dp))
            Row {
                OutlinedButton(enabled = !state.scanUploading, onClick = onPickScan) {
                    Text(if (state.scanUploading) "处理中..." else "替换扫描件")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(enabled = !state.scanUploading, onClick = onClearScan) { Text("移除") }
            }
        }
    } else {
        Button(enabled = !state.scanUploading, onClick = onPickScan) {
            Text(if (state.scanUploading) "上传中..." else "选择扫描件并上传")
        }
    }
}
