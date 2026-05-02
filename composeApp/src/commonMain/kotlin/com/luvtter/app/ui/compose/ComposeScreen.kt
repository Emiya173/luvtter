package com.luvtter.app.ui.compose

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.luvtter.app.theme.LuvtterTheme
import com.luvtter.app.ui.common.*
import com.luvtter.app.ui.letter.*
import com.luvtter.contract.dto.StationeryDto
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
    val tokens = LuvtterTheme.tokens
    val activeStationery = Stationery.byId(stationeryCodeOf(state.stationeries, state.stationeryId) ?: "cream")
    val activeStampSpec = Stamps.byId(stampCodeOf(state.stamps, state.stampId) ?: "airmail")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.colors.paperDeep),
    ) {
        ComposeTopBar(isReply = isReply, onCancel = onCancel)

        Row(modifier = Modifier.fillMaxSize().weight(1f)) {
            // ── Paper area ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PaperCard(
                    stationery = activeStationery,
                    state = state,
                    onRecipientHandleChange = onRecipientHandleChange,
                    onLookup = onLookup,
                    onRecipientAddressSelect = onRecipientAddressSelect,
                    onPickContact = onPickContact,
                    onEditorChange = onEditorChange,
                    onStrikeSelection = onStrikeSelection,
                    onUnstrikeSelection = onUnstrikeSelection,
                    onModeSelect = onModeSelect,
                    onPickScan = onPickScan,
                    onClearScan = onClearScan,
                    onToggleStrikeOnDelete = onToggleStrikeOnDelete,
                )

                Spacer(Modifier.height(16.dp))

                AttachmentsBlock(
                    state = state,
                    onShowStickerDialog = onShowStickerDialog,
                    onPickPhoto = onPickPhoto,
                    onRemoveAttachment = onRemoveAttachment,
                )

                state.sealedUntil?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "封存中：${formatLocalDateTime(it) ?: it}（期间不可编辑/寄出）",
                        style = tokens.typography.meta.copy(fontSize = 11.sp, color = tokens.colors.seal),
                    )
                }
                state.status?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = tokens.typography.body.copy(fontSize = 13.sp, color = tokens.colors.seal))
                }
            }

            // ── Sidebar ───────────────────────────────────────────────────
            Sidebar(
                state = state,
                activeStampSpec = activeStampSpec,
                onStationerySelect = onStationerySelect,
                onFontSelect = onFontSelect,
                onStampSelect = onStampSelect,
                onSenderAddressSelect = onSenderAddressSelect,
                onSaveDraft = onSaveDraft,
                onSend = onSend,
                onShowSealDialog = onShowSealDialog,
            )
        }
    }

    if (showSealDialog) {
        SealDraftDialog(onDismiss = onDismissSealDialog, onConfirm = onSeal)
    }
    if (showStickerDialog) {
        StickerPickerDialog(
            stickers = state.stickers,
            onDismiss = onDismissStickerDialog,
            onPick = onPickSticker,
        )
    }
}

@Composable
private fun ComposeTopBar(isReply: Boolean, onCancel: () -> Unit) {
    val tokens = LuvtterTheme.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.colors.paper)
            .drawBehind {
                drawRect(
                    color = tokens.colors.ruleSoft,
                    topLeft = Offset(0f, size.height - 0.5f),
                    size = Size(size.width, 0.5f),
                )
            }
            .padding(horizontal = 32.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) {
            Text("取消", style = tokens.typography.meta.copy(fontSize = 12.sp, color = tokens.colors.inkSoft))
        }
        Spacer(Modifier.width(20.dp))
        Text(
            if (isReply) "回信" else "写信",
            style = tokens.typography.title.copy(fontSize = 24.sp, letterSpacing = 1.92.sp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            "把心事交给笔尖",
            style = tokens.typography.caption.copy(color = tokens.colors.inkFaded),
        )
        Spacer(Modifier.weight(1f))
        Text(
            "一经寄出 · 不可撤回",
            style = tokens.typography.meta.copy(color = tokens.colors.inkGhost),
        )
    }
}

@Composable
private fun PaperCard(
    stationery: StationerySpec,
    state: ComposeUiState,
    onRecipientHandleChange: (String) -> Unit,
    onLookup: () -> Unit,
    onRecipientAddressSelect: (String) -> Unit,
    onPickContact: (String) -> Unit,
    onEditorChange: (TextFieldValue) -> Unit,
    onStrikeSelection: () -> Unit,
    onUnstrikeSelection: () -> Unit,
    onModeSelect: (String) -> Unit,
    onPickScan: () -> Unit,
    onClearScan: () -> Unit,
    onToggleStrikeOnDelete: () -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    Column(
        modifier = Modifier
            .widthIn(max = 620.dp)
            .fillMaxWidth()
            .shadow(20.dp, RectangleShape, ambientColor = Color(0xFF1E140A).copy(alpha = 0.16f))
            .background(stationery.tint)
            .stationeryRulesCompose(stationery.rule, tokens.colors.inkFaded)
            .padding(horizontal = 48.dp, vertical = 48.dp),
    ) {
        ToFromHeader(
            state = state,
            onRecipientHandleChange = onRecipientHandleChange,
            onLookup = onLookup,
            onRecipientAddressSelect = onRecipientAddressSelect,
            onPickContact = onPickContact,
        )

        Spacer(Modifier.height(28.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("内容", style = tokens.typography.meta.copy(color = tokens.colors.inkFaded))
            Spacer(Modifier.width(12.dp))
            ModeChip(label = "键入", selected = state.mode == "text") { onModeSelect("text") }
            Spacer(Modifier.width(6.dp))
            ModeChip(label = "扫描", selected = state.mode == "scan") { onModeSelect("scan") }
        }
        Spacer(Modifier.height(12.dp))

        if (state.mode == "scan") {
            ScanEditorSection(state = state, onPickScan = onPickScan, onClearScan = onClearScan)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = state.strikeOnDelete,
                    onCheckedChange = { onToggleStrikeOnDelete() },
                    modifier = Modifier.scale(0.75f),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (state.strikeOnDelete) "涂改模式 · ${state.strikeOnDeleteWindowMs / 1000}s 外删字保留划痕" else "涂改模式关",
                    style = tokens.typography.meta.copy(fontSize = 10.sp, color = tokens.colors.inkFaded),
                    modifier = Modifier.weight(1f),
                )
                val hasSelection = !state.editorSelection.collapsed
                if (hasSelection) {
                    TextButton(onClick = onStrikeSelection, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("划掉", style = tokens.typography.meta.copy(fontSize = 11.sp))
                    }
                    TextButton(onClick = onUnstrikeSelection, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("撤销划线", style = tokens.typography.meta.copy(fontSize = 11.sp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LetterBodyEditor(
                text = state.editorText,
                selection = state.editorSelection,
                struckMask = state.struckMask,
                fontCode = state.fontCode,
                onChange = onEditorChange,
            )
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "——— ${state.recipientName ?: ""}".let { if (it.endsWith(" ")) "———" else it },
            style = tokens.typography.caption.copy(
                fontStyle = FontStyle.Italic,
                color = tokens.colors.inkFaded,
                fontSize = 14.sp,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ToFromHeader(
    state: ComposeUiState,
    onRecipientHandleChange: (String) -> Unit,
    onLookup: () -> Unit,
    onRecipientAddressSelect: (String) -> Unit,
    onPickContact: (String) -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("致 · TO", style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.inkFaded))
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTransparentField(
                    value = state.recipientHandle,
                    onValueChange = onRecipientHandleChange,
                    placeholder = "@handle",
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = !state.lookupBusy && state.recipientHandle.isNotBlank(),
                    onClick = onLookup,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        if (state.lookupBusy) "查找中…" else "查找",
                        style = tokens.typography.meta.copy(fontSize = 11.sp, color = tokens.colors.seal),
                    )
                }
            }
            state.recipientName?.let {
                Text(
                    "→ $it",
                    style = tokens.typography.caption.copy(fontSize = 12.sp, color = tokens.colors.inkSoft),
                )
            }
            Spacer(Modifier.height(6.dp))
            val senderLabel = state.senderAddresses.firstOrNull { it.id == state.senderAddressId }?.label ?: ""
            Text(
                "自 · $senderLabel · 今日",
                style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.inkFaded),
            )
            if (state.contacts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    state.contacts.take(6).forEach { c ->
                        val sel = state.recipientHandle == c.target.handle
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .border(
                                    width = 0.5.dp,
                                    color = if (sel) tokens.colors.ink else tokens.colors.paperEdge,
                                    shape = RoundedCornerShape(2.dp),
                                )
                                .clickable { onPickContact(c.target.handle) }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        ) {
                            Text(
                                "@${c.target.handle}",
                                style = tokens.typography.meta.copy(
                                    fontSize = 9.sp,
                                    color = if (sel) tokens.colors.ink else tokens.colors.inkFaded,
                                ),
                            )
                        }
                    }
                }
            }
            if (state.recipientAddresses.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "寄到对方哪个地址",
                    style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.inkFaded),
                )
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.recipientAddresses.forEach { a ->
                        val sel = state.recipientAddressId == a.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRecipientAddressSelect(a.id) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (sel) "● " else "○ ",
                                style = tokens.typography.meta.copy(fontSize = 10.sp, color = if (sel) tokens.colors.seal else tokens.colors.inkGhost),
                            )
                            Text(
                                "${a.label} · ${a.type}" + if (a.isDefault) " · 默认" else "",
                                style = tokens.typography.body.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            )
                        }
                    }
                }
            }
        }
        Postmark(
            city = state.recipientName?.take(2) ?: "远方",
            date = "今日",
            size = 66.dp,
            rotateDeg = 3f,
            ink = LuvtterTheme.colors.stampInk.copy(alpha = 0.55f),
        )
    }

    Spacer(Modifier.height(20.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(LuvtterTheme.colors.rule.copy(alpha = 0.45f)),
    )
}

@Composable
private fun BasicTransparentField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LuvtterTheme.tokens
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            fontFamily = tokens.fonts.serifZh,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = tokens.colors.ink,
            letterSpacing = 0.72.sp,
        ),
        cursorBrush = SolidColor(tokens.colors.seal),
        singleLine = true,
        modifier = modifier.padding(vertical = 4.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = TextStyle(
                        fontFamily = tokens.fonts.serifZh,
                        fontSize = 18.sp,
                        color = tokens.colors.inkGhost,
                    ),
                )
            }
            inner()
        },
    )
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = LuvtterTheme.tokens
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .border(
                width = 0.5.dp,
                color = if (selected) tokens.colors.ink else tokens.colors.paperEdge,
                shape = RoundedCornerShape(2.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            style = tokens.typography.meta.copy(
                fontSize = 10.sp,
                color = if (selected) tokens.colors.ink else tokens.colors.inkFaded,
            ),
        )
    }
}

@Composable
private fun Sidebar(
    state: ComposeUiState,
    activeStampSpec: StampSpec,
    onStationerySelect: (String?) -> Unit,
    onFontSelect: (String?) -> Unit,
    onStampSelect: (String) -> Unit,
    onSenderAddressSelect: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onSend: () -> Unit,
    onShowSealDialog: () -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    Column(
        modifier = Modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(tokens.colors.paper)
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (state.stationeries.isNotEmpty()) {
            ToolSection("信纸") {
                StationeryGrid(
                    items = state.stationeries,
                    selectedId = state.stationeryId,
                    onSelect = onStationerySelect,
                )
            }
        }

        ToolSection("字体") {
            FontList(selectedCode = state.fontCode, onSelect = onFontSelect)
        }

        if (state.stamps.isNotEmpty()) {
            ToolSection("邮票") {
                StampPickerRow(
                    state = state,
                    onSelect = onStampSelect,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${activeStampSpec.name} · ${activeStampSpec.days}",
                    style = tokens.typography.caption.copy(
                        fontSize = 12.sp,
                        color = tokens.colors.inkFaded,
                        fontStyle = FontStyle.Italic,
                    ),
                )
            }
        }

        ToolSection("重量") {
            WeightBar(
                weight = state.totalWeight,
                capacity = state.stampCapacity,
            )
        }

        if (state.senderAddresses.isNotEmpty()) {
            ToolSection("寄件地址") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.senderAddresses.forEach { a ->
                        val sel = state.senderAddressId == a.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSenderAddressSelect(a.id) }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (sel) "●" else "○",
                                style = tokens.typography.meta.copy(
                                    fontSize = 10.sp,
                                    color = if (sel) tokens.colors.seal else tokens.colors.inkGhost,
                                ),
                                modifier = Modifier.width(16.dp),
                            )
                            Text(
                                "${a.label} · ${a.type}",
                                style = tokens.typography.body.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f, fill = false))
        Spacer(Modifier.height(4.dp))

        SealButton(
            enabled = state.canSend,
            label = if (state.loading) "处理中…" else "封缄 · 寄出",
            onClick = onSend,
        )
        TextButton(
            enabled = state.canSaveDraft,
            onClick = onSaveDraft,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("存为草稿", style = tokens.typography.meta.copy(fontSize = 12.sp, color = tokens.colors.inkSoft))
        }
        TextButton(
            enabled = state.canSaveDraft,
            onClick = onShowSealDialog,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("封存草稿", style = tokens.typography.meta.copy(fontSize = 12.sp, color = tokens.colors.inkSoft))
        }
    }
}

@Composable
private fun ToolSection(label: String, content: @Composable () -> Unit) {
    val tokens = LuvtterTheme.tokens
    Column {
        Text(
            label,
            style = tokens.typography.meta.copy(fontSize = 10.sp, color = tokens.colors.inkFaded),
            modifier = Modifier.padding(bottom = 10.dp),
        )
        content()
    }
}

@Composable
private fun StationeryGrid(
    items: List<StationeryDto>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { item ->
                    val sel = selectedId == item.id
                    val spec = Stationery.byId(item.code)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(if (sel) null else item.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(spec.tint)
                                .stationeryRulesCompose(spec.rule, tokens.colors.inkFaded)
                                .border(
                                    width = 0.5.dp,
                                    color = if (sel) tokens.colors.ink else tokens.colors.paperEdge,
                                    shape = RoundedCornerShape(2.dp),
                                ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            item.name,
                            maxLines = 1,
                            style = tokens.typography.meta.copy(
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = if (sel) tokens.colors.ink else tokens.colors.inkFaded,
                                fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                                letterSpacing = 0.4.sp,
                            ),
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FontList(
    selectedCode: String?,
    onSelect: (String?) -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    Column {
        FONT_OPTIONS.forEach { (code, label) ->
            val sel = selectedCode == code
            val accent = if (sel) tokens.colors.seal else Color.Transparent
            val bg = if (sel) tokens.colors.sealGlow.copy(alpha = 0.06f) else Color.Transparent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .drawBehind {
                        drawRect(
                            color = accent,
                            topLeft = Offset(0f, 0f),
                            size = Size(1.5f, size.height),
                        )
                    }
                    .clickable { onSelect(code) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = tokens.typography.meta.copy(
                        fontSize = 10.sp,
                        color = tokens.colors.inkFaded,
                    ),
                    modifier = Modifier.width(40.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "永字八法",
                    style = TextStyle(
                        fontFamily = fontFamilyFor(code, tokens.fonts.serifZh, tokens.fonts.handZh, tokens.fonts.handLoose),
                        fontSize = 14.sp,
                        color = if (sel) tokens.colors.ink else tokens.colors.inkSoft,
                        letterSpacing = 0.28.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StampPickerRow(
    state: ComposeUiState,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        state.stamps.forEach { stamp ->
            val sel = state.stampId == stamp.id
            val spec = Stamps.byId(stamp.code)
            val qty = state.assets?.stamps?.firstOrNull { it.assetId == stamp.id }?.quantity ?: 0
            Column(
                modifier = Modifier
                    .clickable { onSelect(stamp.id) }
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .alpha(if (sel) 1f else 0.42f),
                ) {
                    Stamp(spec = spec, size = 36.dp)
                }
                Text(
                    "×$qty",
                    style = LuvtterTheme.tokens.typography.meta.copy(
                        fontSize = 9.sp,
                        color = if (sel) LuvtterTheme.colors.ink else LuvtterTheme.colors.inkGhost,
                    ),
                )
            }
        }
    }
}

@Composable
private fun WeightBar(weight: Int, capacity: Int?) {
    val tokens = LuvtterTheme.tokens
    val pct = if (capacity != null && capacity > 0) (weight.toFloat() / capacity).coerceIn(0f, 1f) else 0f
    val over = capacity != null && weight > capacity
    val activeColor = if (over || pct > 0.9f) tokens.colors.seal else tokens.colors.ink

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(tokens.colors.paperEdge),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct)
                    .height(2.dp)
                    .background(activeColor),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "$weight / ${capacity ?: "—"}",
                style = tokens.typography.meta.copy(fontSize = 10.sp, color = tokens.colors.inkFaded),
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (over) "超重" else "可承载",
                style = tokens.typography.caption.copy(
                    fontSize = 11.sp,
                    color = if (over) tokens.colors.seal else tokens.colors.inkFaded,
                    fontStyle = FontStyle.Italic,
                ),
            )
        }
    }
}

@Composable
private fun SealButton(enabled: Boolean, label: String, onClick: () -> Unit) {
    val tokens = LuvtterTheme.tokens
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = tokens.colors.seal,
            contentColor = tokens.colors.paperRaised,
            disabledContainerColor = tokens.colors.seal.copy(alpha = 0.4f),
            disabledContentColor = tokens.colors.paperRaised.copy(alpha = 0.7f),
        ),
        shape = RoundedCornerShape(2.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Text(
            label,
            maxLines = 1,
            style = TextStyle(
                fontFamily = tokens.fonts.serifZh,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
            ),
        )
    }
}

@Composable
private fun AttachmentsBlock(
    state: ComposeUiState,
    onShowStickerDialog: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    Column(
        modifier = Modifier
            .widthIn(max = 620.dp)
            .fillMaxWidth(),
    ) {
        Text("附件", style = tokens.typography.meta.copy(color = tokens.colors.inkFaded))
        Spacer(Modifier.height(6.dp))
        if (state.attachments.isEmpty()) {
            Text("尚未添加附件", style = tokens.typography.caption.copy(fontSize = 12.sp, color = tokens.colors.inkGhost))
        } else {
            state.attachments.forEach { att ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val label = when (att.attachmentType) {
                        "sticker" -> state.stickers.firstOrNull { it.id == att.stickerId }?.name ?: "贴纸"
                        "photo" -> "图片 · ${att.objectKey?.substringAfterLast('/') ?: att.mediaUrl ?: "?"}"
                        else -> att.attachmentType
                    }
                    Text(
                        "· $label · ${att.weight}g",
                        style = tokens.typography.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = !state.attachmentBusy,
                        onClick = { onRemoveAttachment(att.id) },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text("移除", style = tokens.typography.meta.copy(fontSize = 11.sp))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = !state.attachmentBusy && state.stickers.isNotEmpty(),
                onClick = onShowStickerDialog,
                shape = RoundedCornerShape(2.dp),
            ) {
                Text("添加贴纸", style = tokens.typography.meta.copy(fontSize = 11.sp))
            }
            OutlinedButton(
                enabled = !state.attachmentBusy,
                onClick = onPickPhoto,
                shape = RoundedCornerShape(2.dp),
            ) {
                Text(
                    if (state.attachmentBusy) "处理中…" else "选图并上传",
                    style = tokens.typography.meta.copy(fontSize = 11.sp),
                )
            }
        }
    }
}

@Composable
private fun LetterBodyEditor(
    text: String,
    selection: TextRange,
    struckMask: List<Boolean>,
    fontCode: String?,
    onChange: (TextFieldValue) -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    val struckColor = tokens.colors.inkFaded
    val transformation = remember(struckMask, struckColor) {
        strikeMaskTransformation(struckMask, struckColor)
    }
    val family = fontFamilyFor(fontCode, tokens.fonts.serifZh, tokens.fonts.handZh, tokens.fonts.handLoose)
    val style = tokens.typography.body.copy(
        fontFamily = family,
        fontSize = 17.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.34.sp,
        color = tokens.colors.ink,
    )
    androidx.compose.foundation.text.BasicTextField(
        value = TextFieldValue(text = text, selection = selection),
        onValueChange = onChange,
        textStyle = style,
        cursorBrush = SolidColor(tokens.colors.seal),
        visualTransformation = transformation,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 360.dp),
        decorationBox = { inner ->
            if (text.isEmpty()) {
                Text("落笔便是心声。", style = style.copy(color = tokens.colors.inkGhost))
            }
            inner()
        },
    )
}

@Composable
private fun ScanEditorSection(
    state: ComposeUiState,
    onPickScan: () -> Unit,
    onClearScan: () -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    Text(
        "扫描信:上传图片或 PDF 作为信件主体。寄出后会进入 OCR 索引。",
        style = tokens.typography.body.copy(fontSize = 12.sp, lineHeight = 18.sp, color = tokens.colors.inkFaded),
    )
    Spacer(Modifier.height(8.dp))
    if (state.scanBound) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "已绑定:${state.scanFilename ?: "(已上传)"}",
                style = tokens.typography.body.copy(fontSize = 13.sp),
            )
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
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(4.dp)),
                )
            }
            if (previewUrl != null && !isImage) {
                val uri = LocalUriHandler.current
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { uri.openUri(previewUrl) }) {
                    Text("在浏览器中打开 PDF")
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !state.scanUploading, onClick = onPickScan, shape = RoundedCornerShape(2.dp)) {
                    Text(if (state.scanUploading) "处理中…" else "替换", style = tokens.typography.meta.copy(fontSize = 11.sp))
                }
                OutlinedButton(enabled = !state.scanUploading, onClick = onClearScan, shape = RoundedCornerShape(2.dp)) {
                    Text("移除", style = tokens.typography.meta.copy(fontSize = 11.sp))
                }
            }
        }
    } else {
        Button(
            enabled = !state.scanUploading,
            onClick = onPickScan,
            shape = RoundedCornerShape(2.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.colors.seal,
                contentColor = tokens.colors.paperRaised,
            ),
        ) {
            Text(
                if (state.scanUploading) "上传中…" else "选择扫描件并上传",
                style = tokens.typography.meta.copy(fontSize = 12.sp, letterSpacing = 1.sp),
            )
        }
    }
}

@Composable
private fun SealDraftDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var minutes by remember { mutableStateOf("60") }
    val presets = listOf(5 to "5 分钟", 60 to "1 小时", 60 * 24 to "1 天", 60 * 24 * 7 to "7 天")
    PaperDialog(
        onDismissRequest = onDismiss,
        title = "封 · 存 · 至 · 未 · 来",
        subtitle = "SEAL · 期间不可编辑/寄出",
        actions = {
            PaperGhostButton(label = "取 消", onClick = onDismiss)
            PaperPrimaryButton(
                label = "封 · 存",
                enabled = minutes.toIntOrNull()?.let { it > 0 } == true,
                onClick = {
                    val m = minutes.toInt()
                    val target = kotlin.time.Clock.System.now().plus(kotlin.time.Duration.parse("${m}m"))
                    onConfirm(target.toString())
                },
            )
        },
    ) {
        Column {
            PaperFieldLabel("常 用 时 长")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (m, label) ->
                    PaperChip(
                        label = label,
                        selected = minutes == m.toString(),
                        onClick = { minutes = m.toString() },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            PaperFieldLabel("自 定 义（分 钟）")
            PaperInput(
                value = minutes,
                onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                placeholder = "60",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            )
        }
    }
}

@Composable
private fun StickerPickerDialog(
    stickers: List<com.luvtter.contract.dto.StickerDto>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    PaperDialog(
        onDismissRequest = onDismiss,
        title = "拣 · 一 · 枚 · 贴 · 纸",
        subtitle = "STICKER · 会计入信件重量",
        actions = { PaperGhostButton(label = "取 消", onClick = onDismiss) },
    ) {
        if (stickers.isEmpty()) {
            Text(
                "目前没有可用贴纸。",
                style = TextStyle(
                    fontFamily = tokens.fonts.serifZh,
                    fontSize = 13.sp,
                    color = tokens.colors.inkGhost,
                ),
            )
        } else {
            Column(modifier = Modifier.heightIn(max = 360.dp)) {
                stickers.forEach { s ->
                    PaperListRow(onClick = { onPick(s.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.name,
                                modifier = Modifier.weight(1f),
                                style = TextStyle(
                                    fontFamily = tokens.fonts.serifZh,
                                    fontSize = 14.sp,
                                    color = tokens.colors.ink,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.4.sp,
                                ),
                            )
                            Text(
                                "${s.weight}g",
                                style = tokens.typography.meta.copy(fontSize = 11.sp, color = tokens.colors.inkFaded),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun strikeMaskTransformation(mask: List<Boolean>, color: Color): VisualTransformation =
    VisualTransformation { input ->
        val annotated = buildAnnotatedString {
            input.text.forEachIndexed { i, c ->
                if (mask.getOrElse(i) { false }) {
                    withStyle(
                        SpanStyle(textDecoration = TextDecoration.LineThrough, color = color),
                    ) { append(c) }
                } else {
                    append(c)
                }
            }
        }
        TransformedText(annotated, OffsetMapping.Identity)
    }

private fun stationeryCodeOf(items: List<StationeryDto>, id: String?): String? =
    id?.let { sid -> items.firstOrNull { it.id == sid }?.code }

private fun stampCodeOf(items: List<com.luvtter.contract.dto.StampDto>, id: String?): String? =
    id?.let { sid -> items.firstOrNull { it.id == sid }?.code }

private fun fontFamilyFor(
    code: String?,
    serifZh: androidx.compose.ui.text.font.FontFamily,
    handZh: androidx.compose.ui.text.font.FontFamily,
    handLoose: androidx.compose.ui.text.font.FontFamily,
): androidx.compose.ui.text.font.FontFamily = when (code) {
    "kaiti" -> handZh
    "handwriting-1" -> handLoose
    else -> serifZh
}

private fun Modifier.stationeryRulesCompose(rule: StationeryRule, lineColor: Color): Modifier =
    this.drawBehind {
        val step = 32f
        when (rule) {
            StationeryRule.Lines -> {
                var y = step
                while (y < size.height) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.16f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 0.5f,
                    )
                    y += step
                }
            }
            StationeryRule.Grid -> {
                var y = step
                while (y < size.height) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.10f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 0.5f,
                    )
                    y += step
                }
                var x = step
                while (x < size.width) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.08f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 0.5f,
                    )
                    x += step
                }
            }
            StationeryRule.Tatebun -> {
                var x = step * 0.4f
                while (x < size.width) {
                    drawLine(
                        color = LuvtterColorsLike.tatebun.copy(alpha = 0.25f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 0.5f,
                    )
                    x += 14f
                }
            }
            StationeryRule.None -> Unit
        }
    }

private object LuvtterColorsLike {
    val tatebun = Color(0xFF8C3C1E)
}

